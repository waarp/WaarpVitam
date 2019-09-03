/*
 * This file is part of Waarp Project (named also Waarp or GG).
 *
 *  Copyright (c) 2019, Waarp SAS, and individual contributors by the @author
 *  tags. See the COPYRIGHT.txt in the distribution for a full listing of
 * individual contributors.
 *
 *  All Waarp Project is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Waarp is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 * Waarp . If not, see <http://www.gnu.org/licenses/>.
 */

package org.waarp.vitam.ingest;

import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetector.Level;
import org.apache.tools.ant.Project;
import org.waarp.common.database.exception.WaarpDatabaseException;
import org.waarp.common.file.FileUtils;
import org.waarp.common.logging.WaarpLogger;
import org.waarp.common.logging.WaarpLoggerFactory;
import org.waarp.common.utility.DetectionUtils;
import org.waarp.common.utility.Processes;
import org.waarp.openr66.configuration.FileBasedConfiguration;
import org.waarp.openr66.dao.DAOFactory;
import org.waarp.openr66.dao.TransferDAO;
import org.waarp.openr66.dao.exception.DAOConnectionException;
import org.waarp.openr66.database.data.DbTaskRunner;
import org.waarp.openr66.protocol.configuration.Configuration;
import org.waarp.openr66.protocol.networkhandler.NetworkTransaction;
import org.waarp.openr66.protocol.utils.ChannelUtils;
import org.waarp.openr66.server.ServerInitDatabase;

import java.io.File;
import java.io.FileFilter;

import static org.junit.Assert.*;

public abstract class CommonUtil {
  private static final String OPEN_R_66_AUTHENT_THROUGH_PROXY_XML =
      "OpenR66-authent-ThroughProxy.xml";
  private static final String OPEN_R_66_AUTHENT_A_XML = "OpenR66-authent-A.xml";
  private static final String CONFIG_CLIENT_SUBMIT_XML =
      "config-clientSubmitA.xml";
  private static final String CONFIG_SERVER_INIT_A_XML =
      "config-serverInitA.xml";
  protected static WaarpLogger logger;
  static File home;
  static File baseDir;
  static File resources;
  static File projectHome;
  static Project project;
  static NetworkTransaction networkTransaction;
  static Configuration r66Configuration;
  static Configuration proxyConfiguration;
  static int r66pid = 0;
  static boolean testShouldFailed;

  public static void launchServers() throws Exception {
    ResourceLeakDetector.setLevel(Level.PARANOID);
    logger = WaarpLoggerFactory.getLogger(CommonUtil.class);
    // Setup directories : /tmp/R66 and sub dirs
    DetectionUtils.setJunit(true);
    setupResources();

    // Launch R66 remote server using resources/r66 directory
    setUpDbBeforeClass();
    // Move to clientA
    setUpBeforeClassClient(CONFIG_CLIENT_SUBMIT_XML);
    Configuration.configuration.setTimeoutCon(100);
  }

  public static void stopServers() throws Exception {
    // Stop R66 remote server using resources/r66 directory
    Configuration.configuration.setTimeoutCon(100);
    tearDownAfterClass();
    // Clean directories
    FileUtils.forceDeleteRecursiveDir(home);
    FileUtils.forceDeleteRecursiveDir(baseDir);
  }

  public static void setupResources() throws Exception {
    final ClassLoader classLoader = CommonUtil.class.getClassLoader();
    File file =
        new File(classLoader.getResource("config-serverInitA.xml").getFile());
    final String newfile = file.getAbsolutePath().replace("target/test-classes",
                                                          "src/test/resources");
    file = new File(newfile);
    if (file.exists()) {
      // R66 Home
      home = new File("/tmp/R66");
      // Resources directory
      resources = file.getParentFile();
      // Project Home directory
      projectHome = resources.getParentFile().getParentFile().getParentFile();
      home.mkdirs();
      baseDir = new File(IngestRequestFactory.TMP_INGEST_FACTORY);
      baseDir.mkdirs();
      FileUtils.forceDeleteRecursiveDir(home);
      FileUtils.forceDeleteRecursiveDir(baseDir);
      new File(home, "in").mkdir();
      new File(home, "out").mkdir();
      new File(home, "arch").mkdir();
      new File(home, "work").mkdir();
      final File conf = new File(home, "conf");
      conf.mkdir();
      // Copy to final home directory
      final File[] copied = FileUtils.copyRecursive(resources, conf, false);
      for (final File fileCopied : copied) {
        System.out.print(fileCopied.getAbsolutePath() + ' ');
      }
      System.out.println(" Done");
    } else {
      System.err
          .println("Cannot find serverInit file: " + file.getAbsolutePath());
      fail("Cannot find serverInit file");
    }
  }

  static void setUpDbBeforeClass() throws Exception {
    deleteBase();
    logger.warn("Dir {} exists? {}", projectHome, projectHome.isDirectory());

    // global ant project settings
    project = Processes.getProject(projectHome);

    initiateDbA();

    Processes.finalizeProject(project);
    project = null;
  }

  private static void initiateDbA() {
    final File fileAuth = new File(resources, OPEN_R_66_AUTHENT_A_XML);
    logger.warn("File {} exists? {}", fileAuth, fileAuth.isFile());
    assertTrue(fileAuth.isFile());
    initiateDb(CONFIG_SERVER_INIT_A_XML, fileAuth);
  }

  private static void initiateDb(String serverInit, File fileAuth) {
    final File file = new File(resources, serverInit);
    logger.warn("File {} exists? {}", file, file.isFile());
    assertTrue(file.isFile());

    final String[] args = {
        file.getAbsolutePath(), "-initdb", "-dir", resources.getAbsolutePath(),
        "-auth", fileAuth.getAbsolutePath()
    };
    Processes.executeJvm(project, projectHome, ServerInitDatabase.class, args,
                         false);
    // Redo natively for debug
    // ServerInitDatabase.main(args);
  }

  public static void deleteBase() {
    final File tmp = new File("/tmp");
    final File[] files = tmp.listFiles(new FileFilter() {
      @Override
      public boolean accept(File file) {
        return file.getName().startsWith("openr66");
      }
    });
    for (final File file : files) {
      file.delete();
    }
  }

  static void tearDownAfterClass() throws Exception {
    Thread.sleep(200);
    Configuration.configuration.setTimeoutCon(100);
    if (project != null) {
      project.log("finished");
      project.fireBuildFinished(null);
      project = null;
    }
    Configuration.configuration.setTimeoutCon(100);
    tearDownAfterClassClient();
    Configuration.configuration.setTimeoutCon(100);
    tearDownAfterClassServer();
    Thread.sleep(500);
    deleteBase();
  }

  private static void tearDownAfterClassServer() throws Exception {
    Configuration.configuration.setTimeoutCon(100);
    ChannelUtils.exit();
  }

  static void setUpBeforeClassClient(String clientConfig) throws Exception {
    final File clientConfigFile = new File(resources, clientConfig);
    if (clientConfigFile.isFile()) {
      System.err.println(
          "Find serverInit file: " + clientConfigFile.getAbsolutePath());
      if (!FileBasedConfiguration
          .setSubmitClientConfigurationFromXml(Configuration.configuration,
                                               new File(resources, clientConfig)
                                                   .getAbsolutePath())) {
        logger.error("Needs a correct configuration file as first argument");
        return;
      }
    } else {
      logger.error("Needs a correct configuration file as first argument");
      return;
    }
    Configuration.configuration.pipelineInit();
    networkTransaction = new NetworkTransaction();
    DbTaskRunner.clearCache();
    TransferDAO transferAccess = null;
    try {
      transferAccess = DAOFactory.getInstance().getTransferDAO();
      transferAccess.deleteAll();
    } catch (final DAOConnectionException e) {
      throw new WaarpDatabaseException(e);
    } finally {
      if (transferAccess != null) {
        transferAccess.close();
      }
    }
  }

  private static void tearDownAfterClassClient() throws Exception {
    if (networkTransaction != null) {
      networkTransaction.closeAll();
    }
  }

}