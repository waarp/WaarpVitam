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

import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.ingest.external.client.IngestExternalClient;
import fr.gouv.vitam.ingest.external.client.IngestExternalClientFactory;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.waarp.common.guid.GUID;
import org.waarp.common.logging.SysErrLogger;
import org.waarp.common.logging.WaarpLogger;
import org.waarp.common.logging.WaarpLoggerFactory;
import org.waarp.common.utility.SystemPropertyUtil;
import org.waarp.vitam.common.waarp.ManagerToWaarp;
import org.waarp.vitam.common.waarp.ManagerToWaarpFactory;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Factory that handles IngestRequest within a directory
 */
public class IngestRequestFactory {
  public static final String DEFAULT_INGEST_FACTORY =
      "/waarp/data/r66/IngestFactory";
  static final String ORG_WAARP_INGEST_BASEDIR = "org.waarp.ingest.basedir";
  /**
   * Internal Logger
   */
  private static final WaarpLogger logger =
      WaarpLoggerFactory.getLogger(IngestRequestFactory.class);
  private static final String WORK = "work";
  private static final String BASENAME =
      IngestRequest.class.getSimpleName() + ".";
  private static final String EXTENSION = ".json";
  private static final String RESULT_EXTENSION = ".xml";
  private static final FilenameFilter JSON_ONLY =
      (dir, name) -> name.startsWith(BASENAME) && name.endsWith(EXTENSION);
  private static final IngestRequestFactory FACTORY =
      new IngestRequestFactory();
  static boolean vitamTakeCareLocalFile = true;

  static {
    setBaseDir(new File(DEFAULT_INGEST_FACTORY));
  }

  private File baseDir;
  private File workDir;
  private IngestExternalClientFactory clientFactory =
      IngestExternalClientFactory.getInstance();

  private IngestRequestFactory() {
    // empty
  }

  /**
   * Common options
   *
   * @return common Options
   */
  static Option getDirectoryOption() {
    Option property =
        new Option("D", "Use value for property " + ORG_WAARP_INGEST_BASEDIR);
    property.setArgName("property=value");
    property.setArgs(2);
    property.setValueSeparator('=');
    return property;
  }

  /**
   * Common parse command line and setup Base Directory
   *
   * @param cmd
   */
  static void parseDirectoryOption(CommandLine cmd) {
    if (cmd.hasOption('D')) {
      Properties properties = cmd.getOptionProperties("D");
      setBaseDir(new File(properties.getProperty(ORG_WAARP_INGEST_BASEDIR,
                                                 DEFAULT_INGEST_FACTORY)));
    } else if (SystemPropertyUtil.contains(ORG_WAARP_INGEST_BASEDIR)) {
      setBaseDir(new File(SystemPropertyUtil.get(ORG_WAARP_INGEST_BASEDIR,
                                                 DEFAULT_INGEST_FACTORY)));
    } else {
      setBaseDir(new File(DEFAULT_INGEST_FACTORY));
    }
  }

  /**
   * Set Base Directory
   *
   * @param baseDirToSet
   */
  static void setBaseDir(File baseDirToSet) {
    FACTORY.baseDir = baseDirToSet;
    FACTORY.baseDir.mkdirs();
    FACTORY.workDir = new File(FACTORY.baseDir, WORK);
    FACTORY.workDir.mkdirs();
  }

  /**
   * @return the instance of the factory
   */
  public static IngestRequestFactory getInstance() {
    return FACTORY;
  }

  /**
   * Used in JUnit
   */
  void setBaseDir() {
    baseDir = FACTORY.baseDir;
    workDir = FACTORY.workDir;
  }

  /**
   * @return the base directory
   */
  File getBaseDir() {
    return baseDir;
  }

  /**
   * @return the Ingest Vitam client
   */
  public IngestExternalClient getClient() {
    return clientFactory.getClient();
  }

  /**
   * @param ingestRequest
   *
   * @return the associated ManagerToWaarp
   */
  public ManagerToWaarp getManagerToWaarp(IngestRequest ingestRequest) {
    return ManagerToWaarpFactory
        .getManagerToWaarp(ingestRequest.getWaarpModel());
  }

  /**
   * Save the IngestRequest as a new one, creating file
   *
   * @param ingestRequest
   *
   * @throws InvalidParseOperationException
   */
  synchronized void saveNewIngestRequest(IngestRequest ingestRequest)
      throws InvalidParseOperationException {
    File newFile = new File(baseDir, getNewName());
    ingestRequest.setJsonPath(newFile.getName());
    JsonHandler.writeAsFile(ingestRequest, newFile);
  }

  /**
   * @return the unique name for JSON
   */
  private static String getNewName() {
    synchronized (FACTORY) {
      GUID guid = new GUID();
      return BASENAME + guid.getId() + EXTENSION;
    }
  }

  /**
   * Update the IngestRequest
   *
   * @param ingestRequest
   *
   * @return true if saved
   *
   * @throws InvalidParseOperationException
   */
  synchronized boolean saveIngestRequest(IngestRequest ingestRequest)
      throws InvalidParseOperationException {
    File existingFile = new File(baseDir, ingestRequest.getJsonPath());
    if (existingFile.canRead()) {
      JsonHandler.writeAsFile(ingestRequest, existingFile);
      return true;
    }
    throw new InvalidParseOperationException("Json File does not exist");
  }

  /**
   * Clean and remove all traces of this IngestRequest
   *
   * @param ingestRequest
   *
   * @return true if totally done
   */
  synchronized boolean removeIngestRequest(IngestRequest ingestRequest) {
    if (ingestRequest.getJsonPath() != null) {
      File existingFile = new File(baseDir, ingestRequest.getJsonPath());
      boolean status = deleteFile(existingFile);
      // Vitam is supposed to take care of this
      if (vitamTakeCareLocalFile) {
        File sourceFile = new File(ingestRequest.getPath());
        status &= deleteFile(sourceFile);
      }
      // Delete the ATR file if any
      File xmlAtrFile = getXmlAtrFile(ingestRequest);
      status &= deleteFile(xmlAtrFile);
      IngestRequest.IngestStep.endSessionMachineSate(ingestRequest.step);
      // Ensure file are deleted there
      while (existingFile.exists()) {
        try {
          FACTORY.wait(10);
        } catch (InterruptedException ignore) {//NOSONAR
          logger.debug(ignore);
        }
      }
      return status;
    }
    return false;
  }

  /**
   * Internal
   *
   * @param file
   *
   * @return true if done
   */
  private boolean deleteFile(File file) {
    if (file.canRead()) {
      try {
        Files.delete(file.toPath());
      } catch (IOException e) {
        logger.warn("Cannot delete file", e);
        return false;
      }
    }
    return true;
  }

  /**
   * @param ingestRequest
   *
   * @return the File pointer to the XML ATR file
   */
  File getXmlAtrFile(IngestRequest ingestRequest) {
    return new File(workDir, ingestRequest.getJsonPath() + RESULT_EXTENSION);
  }

  /**
   * @return the list of existing IngestRequests. Some can be not ready or ended
   */
  synchronized List<IngestRequest> getExistingIngests() {
    List<IngestRequest> list = new ArrayList<>();
    File[] files = baseDir.listFiles(JSON_ONLY);
    if (files != null) {
      for (File file : files) {
        try {
          IngestRequest ingestRequest =
              JsonHandler.getFromFile(file, IngestRequest.class);
          list.add(ingestRequest);
        } catch (InvalidParseOperationException ignored) {
          // File could be deleted during read operation
          SysErrLogger.FAKE_LOGGER.ignoreLog(ignored);
        }
      }
    }
    return list;
  }

  /**
   * @param filename
   *
   * @return the IngestRequest if found
   *
   * @throws InvalidParseOperationException
   */
  synchronized IngestRequest getSpecificIngestRequest(String filename)
      throws InvalidParseOperationException {
    File file = new File(baseDir, filename);
    if (file.exists()) {
      return JsonHandler.getFromFile(file, IngestRequest.class);
    }
    throw new InvalidParseOperationException("Cannot find " + filename);
  }
}
