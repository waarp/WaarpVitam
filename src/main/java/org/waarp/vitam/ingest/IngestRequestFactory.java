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
import org.waarp.common.logging.WaarpLogger;
import org.waarp.common.logging.WaarpLoggerFactory;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;

public class IngestRequestFactory {
  /**
   * Internal Logger
   */
  private static final WaarpLogger logger =
      WaarpLoggerFactory.getLogger(IngestRequestFactory.class);
  static final String TMP_INGEST_FACTORY = "/tmp/IngestFactory";
  private static final String WORK = "work";
  static final String ORG_WAARP_INGEST_BASEDIR = "org.waarp.ingest.basedir";

  static boolean VITAM_TAKE_CARE_LOCAL_FILE = true;
  private static final String BASENAME =
      IngestRequest.class.getSimpleName() + ".";
  private static final String EXTENSION = ".json ";
  private static final FilenameFilter JSON_ONLY =
      (dir, name) -> name.startsWith(BASENAME) && name.endsWith(EXTENSION);
  private static final IngestRequestFactory FACTORY =
      new IngestRequestFactory();

  static {
    setBaseDir(new File(TMP_INGEST_FACTORY));
  }

  private static String getNewName() {
    return BASENAME + System.currentTimeMillis() + EXTENSION;
  }

  public static void setBaseDir(File baseDirToSet) {
    FACTORY.baseDir = baseDirToSet;
    FACTORY.baseDir.mkdirs();
    FACTORY.workDir = new File(FACTORY.baseDir, WORK);
    FACTORY.workDir.mkdirs();
  }

  public static IngestRequestFactory getInstance() {
    return FACTORY;
  }

  private File baseDir;
  private File workDir;
  private IngestExternalClientFactory clientFactory =
      IngestExternalClientFactory.getInstance();

  private IngestRequestFactory() {
    // empty
  }

  public IngestExternalClient getClient() {
    return clientFactory.getClient();
  }

  void saveNewIngestRequest(IngestRequest ingestRequest)
      throws InvalidParseOperationException {
    synchronized (FACTORY) {
      File newFile = new File(baseDir, getNewName());
      ingestRequest.setJsonPath(newFile.getName());
      JsonHandler.writeAsFile(ingestRequest, newFile);
    }
  }

  void saveIngestRequest(IngestRequest ingestRequest)
      throws InvalidParseOperationException {
    synchronized (FACTORY) {
      File existingFile = new File(baseDir, ingestRequest.getJsonPath());
      if (existingFile.canRead()) {
        JsonHandler.writeAsFile(ingestRequest, existingFile);
        return;
      }
    }
    throw new InvalidParseOperationException("Json File does not exist");
  }

  boolean removeIngestRequest(IngestRequest ingestRequest) {
    if (ingestRequest.getJsonPath() != null) {
      File existingFile = new File(baseDir, ingestRequest.getJsonPath());
      boolean status = true;
      if (existingFile.canRead()) {
        status &= existingFile.delete();
      }
      // Vitam is supposed to take care of this
      if (VITAM_TAKE_CARE_LOCAL_FILE) {
        File sourceFile = new File(ingestRequest.getPath());
        if (sourceFile.canRead()) {
          status &= sourceFile.delete();
        }
      }
      // Delete the ATR file if any
      File xmlAtrFile = getXmlAtrFile(ingestRequest);
      if (xmlAtrFile.canRead()) {
        status &= xmlAtrFile.delete();
      }
      IngestRequest.IngestStep.endSessionMachineSate(ingestRequest.step);
      return status;
    }
    return false;
  }

  File getXmlAtrFile(IngestRequest ingestRequest) {
    return new File(workDir, ingestRequest.getJsonPath() + EXTENSION);
  }

  List<IngestRequest> getExistingIngestFactory()
      throws InvalidParseOperationException {
    List<IngestRequest> list = new ArrayList<>();
    try {
      File[] files = baseDir.listFiles(JSON_ONLY);
      if (files != null) {
        for (File file : files) {
          IngestRequest ingestRequest =
              JsonHandler.getFromFile(file, IngestRequest.class);
          list.add(ingestRequest);
        }
      }
      return list;
    } catch (InvalidParseOperationException e) {
      logger.error(e);
      list.clear();
      throw e;
    }
  }
}
