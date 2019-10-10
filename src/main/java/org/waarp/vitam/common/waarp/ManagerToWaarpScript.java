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

package org.waarp.vitam.common.waarp;

import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.PumpStreamHandler;
import org.waarp.common.file.FileUtils;
import org.waarp.common.logging.SysErrLogger;
import org.waarp.common.logging.WaarpLogger;
import org.waarp.common.logging.WaarpLoggerFactory;
import org.waarp.common.utility.WaarpStringUtils;
import org.waarp.vitam.dip.DipRequest;
import org.waarp.vitam.dip.DipRequestFactory;
import org.waarp.vitam.ingest.IngestRequest;
import org.waarp.vitam.ingest.IngestRequestFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * Class for Waarp sending back to Waarp Partner from Monitor using external
 * scripts
 */
class ManagerToWaarpScript implements ManagerToWaarp {
  /**
   * Internal Logger
   */
  private static final WaarpLogger logger =
      WaarpLoggerFactory.getLogger(ManagerToWaarpScript.class);
  private static final String EXCEPTION_WHILE_ANSWERED =
      "Exception while answered: ";
  private static final String EXEC_IN_ERROR_WITH = " Exec in error with ";
  private static final String EXCEPTION = "Exception: ";

  final private String command;

  /**
   * @param commandLine the command line to use
   */
  ManagerToWaarpScript(String commandLine) {
    this.command = commandLine;
  }

  @Override
  public boolean sendBackInformation(
      final IngestRequestFactory ingestRequestFactory,
      final IngestRequest ingestRequest, final String filename,
      final String fileInfo) throws InvalidParseOperationException {
    logger.debug("Will send {} while step is {}", filename,
                 ingestRequest.getStep());
    String baseDir = command.split(" ")[0];
    File homeDir = new File(baseDir);
    // Create command with parameters
    final CommandLine commandLine = new CommandLine(command);
    commandLine.addArgument(ingestRequest.getWaarpPartner());
    commandLine.addArgument(ingestRequest.getWaarpRule());
    commandLine.addArgument(ingestRequest.getRequestId());
    commandLine.addArgument(ingestRequest.getApplicationSessionId());
    commandLine.addArgument(Integer.toString(ingestRequest.getTenantId()));
    commandLine.addArgument(filename);
    commandLine.addArgument(fileInfo);

    StatusIdResult statusIdResult = new StatusIdResult(commandLine).invoke();
    if (statusIdResult.isKO()) {
      return false;
    }
    int status = statusIdResult.getStatus();
    long waarpId = statusIdResult.getWaarpId();
    if (status == 0) {
      ingestRequest.setWaarpId(waarpId);
      ingestRequest.save(ingestRequestFactory);
    }
    return status == 0;
  }

  @Override
  public boolean sendBackInformation(final DipRequestFactory dipRequestFactory,
                                     final DipRequest dipRequest,
                                     final String filename,
                                     final String fileInfo)
      throws InvalidParseOperationException {
    logger
        .debug("Will send {} while step is {}", filename, dipRequest.getStep());
    String baseDir = command.split(" ")[0];
    File homeDir = new File(baseDir);
    // Create command with parameters
    final CommandLine commandLine = new CommandLine(command);
    commandLine.addArgument(dipRequest.getWaarpPartner());
    commandLine.addArgument(dipRequest.getWaarpRule());
    commandLine.addArgument(dipRequest.getRequestId());
    commandLine.addArgument(dipRequest.getApplicationSessionId());
    commandLine.addArgument(Integer.toString(dipRequest.getTenantId()));
    commandLine.addArgument(filename);
    commandLine.addArgument(fileInfo);

    StatusIdResult statusIdResult = new StatusIdResult(commandLine).invoke();
    if (statusIdResult.isKO()) {
      return false;
    }
    int status = statusIdResult.getStatus();
    long waarpId = statusIdResult.getWaarpId();
    if (status == 0) {
      dipRequest.setWaarpId(waarpId);
      dipRequest.save(dipRequestFactory);
    }
    return status == 0;
  }

  private class StatusIdResult {
    private final CommandLine commandLine;
    private boolean myResult;
    private int status;
    private long waarpId;

    public StatusIdResult(final CommandLine commandLine) {
      this.commandLine = commandLine;
    }

    boolean isKO() {
      return myResult;
    }

    public int getStatus() {
      return status;
    }

    public long getWaarpId() {
      return waarpId;
    }

    public StatusIdResult invoke() {
      final DefaultExecutor defaultExecutor = new DefaultExecutor();
      ByteArrayOutputStream outputStream;
      outputStream = new ByteArrayOutputStream();
      final PumpStreamHandler pumpStreamHandler =
          new PumpStreamHandler(outputStream);
      defaultExecutor.setStreamHandler(pumpStreamHandler);
      final int[] correctValues = { 0, 1 };
      defaultExecutor.setExitValues(correctValues);
      status = -1;
      try {
        // Execute the command
        status = defaultExecutor.execute(commandLine);//NOSONAR
      } catch (final ExecuteException e) {
        if (e.getExitValue() == -559038737) {
          // Cannot run immediately so retry once
          try {
            Thread.sleep(10);
          } catch (final InterruptedException e1) {//NOSONAR
            SysErrLogger.FAKE_LOGGER.ignoreLog(e1);
          }
          try {
            status = defaultExecutor.execute(commandLine);//NOSONAR
          } catch (final ExecuteException e1) {
            try {
              pumpStreamHandler.stop();
            } catch (final IOException ignored) {
              // nothing
            }
            logger.error(
                EXCEPTION + e.getMessage() + EXEC_IN_ERROR_WITH + commandLine);
            FileUtils.close(outputStream);
            myResult = true;
            return this;
          } catch (final IOException e1) {
            try {
              pumpStreamHandler.stop();
            } catch (final IOException ignored) {
              // nothing
            }
            logger.error(
                EXCEPTION + e.getMessage() + EXEC_IN_ERROR_WITH + commandLine);
            FileUtils.close(outputStream);
            myResult = true;
            return this;
          }
        } else {
          try {
            pumpStreamHandler.stop();
          } catch (final IOException ignored) {
            // nothing
          }
          logger.error(
              EXCEPTION + e.getMessage() + EXEC_IN_ERROR_WITH + commandLine);
          FileUtils.close(outputStream);
          myResult = true;
          return this;
        }
      } catch (final IOException e) {
        try {
          pumpStreamHandler.stop();
        } catch (final IOException ignored) {
          // nothing
        }
        logger
            .error(EXCEPTION + e.getMessage() + EXEC_IN_ERROR_WITH + commandLine);
        FileUtils.close(outputStream);
        myResult = true;
        return this;
      }
      try {
        pumpStreamHandler.stop();
      } catch (final IOException ignored) {
        // nothing
      }
      String response;
      try {
        response = outputStream.toString(WaarpStringUtils.UTF8.name());
      } catch (final UnsupportedEncodingException e) {
        response = outputStream.toString();
      }
      waarpId = Long.parseLong(response);
      FileUtils.close(outputStream);
      myResult = false;
      return this;
    }
  }
}
