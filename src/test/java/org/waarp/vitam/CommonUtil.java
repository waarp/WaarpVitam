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

package org.waarp.vitam;

import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetector.Level;
import org.waarp.common.file.FileUtils;
import org.waarp.common.logging.WaarpLogger;
import org.waarp.common.logging.WaarpLoggerFactory;
import org.waarp.common.utility.DetectionUtils;

import java.io.File;

import static org.junit.Assert.*;

public abstract class CommonUtil {
  private static final String CONFIG_CLIENT_SUBMIT_XML =
      "config-clientSubmitA.xml";
  private static WaarpLogger logger;
  private static File home;
  private static File baseDir;
  private static File resources;
  private static File projectHome;
  public static File waarpClientConfig;

  public static void launchServers(String baseDirHome) throws Exception {
    ResourceLeakDetector.setLevel(Level.PARANOID);
    logger = WaarpLoggerFactory.getLogger(CommonUtil.class);
    // Setup directories : /tmp/R66 and sub dirs
    DetectionUtils.setJunit(true);
    setupResources(baseDirHome);
  }

  private static void setupResources(String baseDirHome) throws Exception {
    final ClassLoader classLoader = CommonUtil.class.getClassLoader();
    File file =
        new File(classLoader.getResource(CONFIG_CLIENT_SUBMIT_XML).getFile());
    final String newfile = file.getAbsolutePath().replace("target/test-classes",
                                                          "src/test/resources");
    waarpClientConfig = new File(newfile);
    if (waarpClientConfig.exists()) {
      // R66 Home
      home = new File("/tmp/R66");
      // Resources directory
      resources = waarpClientConfig.getParentFile();
      // Project Home directory
      projectHome = resources.getParentFile().getParentFile().getParentFile();
      home.mkdirs();
      baseDir = new File(baseDirHome);
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

  public static void stopServers() throws Exception {
    // Clean directories
    FileUtils.forceDeleteRecursiveDir(home);
    FileUtils.forceDeleteRecursiveDir(baseDir);
  }

}