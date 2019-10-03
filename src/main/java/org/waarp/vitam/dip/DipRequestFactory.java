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

package org.waarp.vitam.dip;

import fr.gouv.vitam.access.external.client.AccessExternalClient;
import fr.gouv.vitam.access.external.client.AccessExternalClientFactory;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.json.JsonHandler;
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
 * Factory that handles DipRequest within a directory
 */
public class DipRequestFactory {
  static final String DEFAULT_DIP_FACTORY = "/waarp/data/r66/DipFactory";
  static final String ORG_WAARP_DIP_BASEDIR = "org.waarp.dip.basedir";
  /**
   * Internal Logger
   */
  private static final WaarpLogger logger =
      WaarpLoggerFactory.getLogger(DipRequestFactory.class);
  private static final String WORK = "work";
  private static final String BASENAME = DipRequest.class.getSimpleName() + ".";
  private static final String EXTENSION = ".json";
  private static final String RESULT_EXTENSION = ".zip";
  private static final FilenameFilter JSON_ONLY =
      (dir, name) -> name.startsWith(BASENAME) && name.endsWith(EXTENSION);
  private static final DipRequestFactory FACTORY = new DipRequestFactory();

  static {
    setBaseDir(new File(DEFAULT_DIP_FACTORY));
  }

  private File baseDir;
  private File workDir;
  private AccessExternalClientFactory clientFactory =
      AccessExternalClientFactory.getInstance();

  private DipRequestFactory() {
    // empty
  }

  /**
   * Common options
   *
   * @return common Options
   */
  static Option getDirectoryOption() {
    Option property =
        new Option("D", "Use value for property " + ORG_WAARP_DIP_BASEDIR);
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
      setBaseDir(new File(
          properties.getProperty(ORG_WAARP_DIP_BASEDIR, DEFAULT_DIP_FACTORY)));
    } else if (SystemPropertyUtil.contains(ORG_WAARP_DIP_BASEDIR)) {
      setBaseDir(new File(
          SystemPropertyUtil.get(ORG_WAARP_DIP_BASEDIR, DEFAULT_DIP_FACTORY)));
    } else {
      setBaseDir(new File(DEFAULT_DIP_FACTORY));
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
  public static DipRequestFactory getInstance() {
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
   * @return the Access Vitam client
   */
  public AccessExternalClient getClient() {
    return clientFactory.getClient();
  }

  /**
   * @param dipRequest
   *
   * @return the associated ManagerToWaarp
   */
  public ManagerToWaarp getManagerToWaarp(DipRequest dipRequest) {
    return ManagerToWaarpFactory.getManagerToWaarp(dipRequest.getWaarpModel());
  }

  /**
   * Save the DipRequest as a new one, creating file
   *
   * @param dipRequest
   *
   * @throws InvalidParseOperationException
   */
  synchronized void saveNewDipRequest(DipRequest dipRequest)
      throws InvalidParseOperationException {
    File newFile = new File(baseDir, getNewName());
    dipRequest.setJsonPath(newFile.getName());
    JsonHandler.writeAsFile(dipRequest, newFile);
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
   * Update the DipRequest
   *
   * @param dipRequest
   *
   * @return true if saved
   *
   * @throws InvalidParseOperationException
   */
  synchronized boolean saveDipRequest(DipRequest dipRequest)
      throws InvalidParseOperationException {
    File existingFile = new File(baseDir, dipRequest.getJsonPath());
    if (existingFile.canRead()) {
      JsonHandler.writeAsFile(dipRequest, existingFile);
      return true;
    }
    throw new InvalidParseOperationException("Json File does not exist");
  }

  /**
   * Clean and remove all traces of this DipRequest
   *
   * @param dipRequest
   *
   * @return true if totally done
   */
  synchronized boolean removeDipRequest(DipRequest dipRequest) {
    if (dipRequest.getJsonPath() != null) {
      File existingFile = new File(baseDir, dipRequest.getJsonPath());
      boolean status = deleteFile(existingFile);
      // Delete the ATR file if any
      File zipDipFile = getDipFile(dipRequest);
      status &= deleteFile(zipDipFile);
      File errorFile = getErrorFile(dipRequest);
      status &= deleteFile(errorFile);
      DipRequest.DIPStep.endSessionMachineSate(dipRequest.step);
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
   * @param dipRequest
   *
   * @return the File pointer to the DIP file
   */
  File getDipFile(DipRequest dipRequest) {
    return new File(workDir, dipRequest.getJsonPath() + RESULT_EXTENSION);
  }

  /**
   * @param dipRequest
   *
   * @return the error file pointer
   */
  File getErrorFile(DipRequest dipRequest) {
    return new File(workDir, dipRequest.getJsonPath() + EXTENSION);
  }

  /**
   * @return the list of existing DipRequests. Some can be not ready or ended
   */
  synchronized List<DipRequest> getExistingDips() {
    List<DipRequest> list = new ArrayList<>();
    File[] files = baseDir.listFiles(JSON_ONLY);
    if (files != null) {
      for (File file : files) {
        try {
          DipRequest dipRequest =
              JsonHandler.getFromFile(file, DipRequest.class);
          list.add(dipRequest);
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
   * @return the DipRequest if found
   *
   * @throws InvalidParseOperationException
   */
  synchronized DipRequest getSpecificDipRequest(String filename)
      throws InvalidParseOperationException {
    File file = new File(baseDir, filename);
    if (file.exists()) {
      return JsonHandler.getFromFile(file, DipRequest.class);
    }
    throw new InvalidParseOperationException("Cannot find " + filename);
  }
}
