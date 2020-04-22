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

import com.fasterxml.jackson.databind.JsonNode;
import fr.gouv.vitam.access.external.client.AccessExternalClient;
import fr.gouv.vitam.access.external.client.AdminExternalClient;
import fr.gouv.vitam.common.GlobalDataRest;
import fr.gouv.vitam.common.client.VitamContext;
import fr.gouv.vitam.common.error.VitamError;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamClientException;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.stream.StreamUtils;
import org.apache.commons.io.FileUtils;
import org.waarp.common.logging.SysErrLogger;
import org.waarp.common.logging.WaarpLogger;
import org.waarp.common.logging.WaarpLoggerFactory;
import org.waarp.common.utility.WaarpThreadFactory;
import org.waarp.vitam.common.OperationCheck;
import org.waarp.vitam.dip.DipRequest.DIPStep;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardCopyOption.*;

/**
 * DipManager is the central logic for DIP management between Waarp and
 * Vitam
 */
public class DipManager implements Runnable {
  /**
   * Prefix of File Information for DIP_FAILED
   */
  public static final String DIP_FAILED = "DIP_FAILED";
  /**
   * Prefix of File Information for DIP
   */
  public static final String DIP = "DIP";
  protected static final String ERROR_MESSAGE = "{}\n\t{}";
  /**
   * Internal Logger
   */
  private static final WaarpLogger logger =
      WaarpLoggerFactory.getLogger(DipManager.class);
  private static final String ISSUE_SINCE_SELECT_PRODUCES_AN_ERROR =
      "Issue since Select produces an error";

  private DipRequest dipRequest;
  private AdminExternalClient adminExternalClient;
  private AccessExternalClient client;
  private DipRequestFactory dipRequestFactory;

  DipManager() {
    // Empty
  }

  private DipManager(final DipRequest dipRequest,
                     final AdminExternalClient adminExternalClient,
                     final AccessExternalClient client,
                     final DipRequestFactory dipRequestFactory) {
    this.dipRequest = dipRequest;
    this.adminExternalClient = adminExternalClient;
    this.client = client;
    this.dipRequestFactory = dipRequestFactory;
  }

  /**
   * Get all existing DipRequest and try to continue according to their
   * status
   *
   * @param dipRequestFactory
   * @param client
   * @param adminExternalClient
   * @param dipMonitor
   */
  void retryAllExistingFiles(final DipRequestFactory dipRequestFactory,
                             final AccessExternalClient client,
                             final AdminExternalClient adminExternalClient,
                             final DipMonitor dipMonitor) {
    List<DipRequest> dipRequests = dipRequestFactory.getExistingDips();
    if (dipRequests.isEmpty()) {
      return;
    }
    ExecutorService executorService = Executors
        .newFixedThreadPool(dipRequests.size(),
                            new WaarpThreadFactory("DipManager"));
    for (DipRequest dipRequest : dipRequests) {
      if (dipMonitor.isShutdown()) {
        return;
      }
      DipManager task = new DipManager(dipRequest, adminExternalClient, client,
                                       dipRequestFactory);
      executorService.execute(task);
    }
    try {
      Thread.sleep(dipMonitor.getElapseTime());
    } catch (InterruptedException e) {//NOSONAR
      SysErrLogger.FAKE_LOGGER.ignoreLog(e);
    }
    executorService.shutdown();
    while (!executorService.isTerminated()) {
      try {
        executorService.awaitTermination(dipMonitor.getElapseTime(),
                                         TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {//NOSONAR
        SysErrLogger.FAKE_LOGGER.ignoreLog(e);
      }
    }
    executorService.shutdownNow();
  }

  @Override
  public void run() {
    logger.warn("Will run {}", dipRequest);
    try {
      while (runStep(dipRequestFactory, client, adminExternalClient,
                     dipRequest)) {
        // Executing next step
        if (dipRequest.getStep() == null) {
          // END
          break;
        }
        logger.debug("Will rerun {}", dipRequest);
      }
    } catch (InvalidParseOperationException e) {
      // very bad
      logger.error("Very bad since cannot save DipRequest", e);
    }
  }

  /**
   * Rune next step for this DipRequest
   *
   * @param dipRequestFactory
   * @param client
   * @param adminExternalClient
   * @param dipRequest
   *
   * @return true if it is possible to run again the next step for this
   *     DipRequest
   *
   * @throws InvalidParseOperationException
   */
  private boolean runStep(final DipRequestFactory dipRequestFactory,
                          final AccessExternalClient client,
                          final AdminExternalClient adminExternalClient,
                          final DipRequest dipRequest)
      throws InvalidParseOperationException {
    DIPStep step = dipRequest.getStep();
    logger.debug("Step is {} from {}", step, dipRequest);
    switch (step) {
      case STARTUP:
        // Ignore: request not ready for the manager
        break;
      case RETRY_SELECT:
        // restart from Select
        logger.info("Start from Select: {}", dipRequest);
        select(dipRequestFactory, dipRequest, client);
        break;
      case RETRY_DIP:
        // restart from DIP
        logger.info("From DIP: {}", dipRequest);
        getDip(dipRequestFactory, dipRequest, client, adminExternalClient,
               dipRequest.getVitamContext());
        break;
      case RETRY_DIP_FORWARD:
        // Write back the content of the DIP through Waarp
        logger.info("From DIP_FORWARD: {}", dipRequest);
        File targetFile = dipRequest.getDipFile(dipRequestFactory);
        sendDipFile(dipRequestFactory, dipRequest, targetFile);
        break;
      case ERROR:
        logger.info("From Error: {}", dipRequest);
        sendErrorBack(dipRequestFactory, dipRequest);
        break;
      case END:
        // To be deleted
        logger.info("End of DIP: {}", dipRequest);
        toDelete(dipRequestFactory, dipRequest);
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + step);
    }
    DIPStep newStep = dipRequest.getStep();
    return newStep != DIPStep.END && newStep != step;
  }

  /**
   * Try to launch first step of DIP (step 1)
   *
   * @param dipRequestFactory
   * @param dipRequest
   * @param client
   *
   * @return 0 if OK, 1 if Warning, 2 if error
   */
  int select(final DipRequestFactory dipRequestFactory,
             final DipRequest dipRequest, final AccessExternalClient client) {
    try {
      // Inform Vitam of an Ingest to proceed locally
      dipRequest.setStep(DIPStep.RETRY_SELECT, 0, dipRequestFactory);
      VitamContext vitamContext = dipRequest.getVitamContext();
      JsonNode jsonNode = dipRequest.getSelectJson();
      RequestResponse requestResponse =
          client.exportDIP(vitamContext, jsonNode);
      if (!requestResponse.isOk()) {
        String requestIdNew =
            requestResponse.getHeaderString(GlobalDataRest.X_REQUEST_ID);
        if (requestIdNew == null || requestIdNew.isEmpty()) {
          requestIdNew = "FAKE_REQUEST_ID";
        }
        dipRequest.setRequestId(requestIdNew);
        Status status = Status.fromStatusCode(requestResponse.getStatus());
        switch (status) {
          case SERVICE_UNAVAILABLE:
            // Should retry later on
            logger.warn(ERROR_MESSAGE, "Issue since service unavailable",
                        requestResponse);
            dipRequest.setStep(DIPStep.RETRY_SELECT, 0, dipRequestFactory);
            // Next step is RETRY_SELECT
            return 1;
          default:
            // Very Bad: inform back of error
            logger.error(ERROR_MESSAGE, ISSUE_SINCE_SELECT_PRODUCES_AN_ERROR,
                         requestResponse);
            dipRequest.setStep(DIPStep.ERROR, status.getStatusCode(),
                               dipRequestFactory);
            // Will inform back of error which could not be fixed when reloaded
        }
        // Next step is ERROR
        return 2;
      }
      // Select sent and accepted
      RequestResponseOK responseOK = (RequestResponseOK) requestResponse;
      dipRequest.setFromRequestResponse(responseOK);

      // Now will start DIP pooling
      dipRequest
          .setStep(DIPStep.RETRY_DIP, DIPStep.RETRY_DIP.getStatusMonitor(),
                   dipRequestFactory);
      return 0;
    } catch (InvalidParseOperationException e) {
      logger.error("FATAL: Issue since backup of request produces an error", e);
    } catch (VitamClientException e) {
      logger.error(ISSUE_SINCE_SELECT_PRODUCES_AN_ERROR, e);
      // Should retry select from the beginning
      try {
        // FIXME this does not take into account various cases since Vitam masks the real reason
        dipRequest.setStep(DIPStep.ERROR, 500, dipRequestFactory);
        // Will inform back of error which could not be fixed when reloaded
        // Ignore: dipRequest.setStep(DIPStep.RETRY_SELECT, 0, dipRequestFactory);
      } catch (InvalidParseOperationException ex) {
        // very bad
        logger.error("FATAL: Very bad since cannot save DipRequest", ex);
      }
    }
    return 2;
  }

  /**
   * Get the DIP (step 2)
   *
   * @param dipRequestFactory
   * @param dipRequest
   * @param client
   * @param adminExternalClient
   * @param vitamContext
   *
   * @return True if OK
   *
   * @throws InvalidParseOperationException
   */
  boolean getDip(final DipRequestFactory dipRequestFactory,
                 final DipRequest dipRequest, final AccessExternalClient client,
                 final AdminExternalClient adminExternalClient,
                 final VitamContext vitamContext)
      throws InvalidParseOperationException {
    Response response = null;
    try {
      dipRequest.setStep(DIPStep.RETRY_DIP, 0, dipRequestFactory);
      OperationCheck operationCheck = new OperationCheck(adminExternalClient);
      if (operationCheck.checkAvailabilityAtr(dipRequest.getTenantId(),
                                              dipRequest.getRequestId())) {
        response = client.getDIPById(vitamContext, dipRequest.getRequestId());
        Status status = Status.fromStatusCode(response.getStatus());
        switch (status) {
          case OK:
          case ACCEPTED:
            sendDIP(dipRequestFactory, dipRequest, response);
            return true;
          case SERVICE_UNAVAILABLE:
          case NOT_FOUND:
            // Should retry later on
            logger.debug("Service or DIP unavailable yet\n\t{}",
                         status.getReasonPhrase());
            return false;
          default:
            // Very Bad: inform back of error
            logger.error(ERROR_MESSAGE, ISSUE_SINCE_SELECT_PRODUCES_AN_ERROR,
                         status.getReasonPhrase());
            dipRequest.setStep(DIPStep.ERROR, response.getStatus(),
                               dipRequestFactory);
        }
      }
    } catch (VitamClientException e) {
      logger.warn("Issue since access client produces an error", e);
    } finally {
      // Shall read all InputStream
      StreamUtils.consumeAnyEntityAndClose(response);
    }
    return false;
  }

  /**
   * Send the DIP back to the Waarp Partner, directly from step 2 (DIP
   * retrieve) (step 3)
   *
   * @param dipRequestFactory
   * @param dipRequest
   * @param response
   *
   * @throws InvalidParseOperationException
   */
  private void sendDIP(final DipRequestFactory dipRequestFactory,
                       final DipRequest dipRequest, final Response response)
      throws InvalidParseOperationException {
    try (final InputStream inputStream = response
        .readEntity(InputStream.class)) {
      // Write file to be forwarded
      File targetFile = dipRequest.getDipFile(dipRequestFactory);
      Path target = targetFile.toPath();
      Files.copy(inputStream, target, REPLACE_EXISTING);
      // Write back the content of the DIP through Waarp
      sendDipFile(dipRequestFactory, dipRequest, targetFile);
    } catch (IOException e) {
      logger
          .error("File must be writable or InputStream error during close", e);
      dipRequest
          .setStep(DIPStep.ERROR, Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                   dipRequestFactory);
    }
  }

  /**
   * Step to send DIP before finished (step 3)
   *
   * @param dipRequestFactory
   * @param dipRequest
   * @param targetFile
   *
   * @throws InvalidParseOperationException
   */
  private void sendDipFile(final DipRequestFactory dipRequestFactory,
                           final DipRequest dipRequest, final File targetFile)
      throws InvalidParseOperationException {
    dipRequest.setStep(DIPStep.RETRY_DIP_FORWARD, 0, dipRequestFactory);
    if (!dipRequestFactory.getManagerToWaarp(dipRequest)
                          .sendBackInformation(dipRequestFactory, dipRequest,
                                               targetFile.getAbsolutePath(),
                                               DIP)) {
      // ATR already there but not sent, so retry
      dipRequest.setStep(DIPStep.RETRY_DIP_FORWARD, 0, dipRequestFactory);
    } else {
      toDelete(dipRequestFactory, dipRequest);
    }
  }

  /**
   * Finalize DipRequest, whatever Done or in Error (final step 4 in case
   * of Done)
   *
   * @param dipRequestFactory
   * @param dipRequest
   *
   * @throws InvalidParseOperationException
   */
  private void toDelete(final DipRequestFactory dipRequestFactory,
                        final DipRequest dipRequest)
      throws InvalidParseOperationException {
    // Ensure it will not be reloaded
    dipRequest.setStep(DIPStep.END, 0, dipRequestFactory);
    if (!dipRequestFactory.removeDipRequest(dipRequest)) {
      logger.error("Issue while cleaning this DipRequest: {}", dipRequest);
    } else {
      logger.info("End of DipRequest: {}", dipRequest);
    }
  }

  /**
   * If in Error, will send back the status of the operation to the Waarp
   * Partner before ending.
   *
   * @param dipRequestFactory
   * @param dipRequest
   *
   * @throws InvalidParseOperationException
   */
  private void sendErrorBack(final DipRequestFactory dipRequestFactory,
                             final DipRequest dipRequest)
      throws InvalidParseOperationException {
    logger.warn("Error to feedback since status not ok to restart: {}",
                dipRequest);
    // Feedback through Waarp
    File file = dipRequest.getErrorFile(dipRequestFactory);
    if (!file.canRead()) {
      // Create a pseudo one
      Status status = Status.fromStatusCode(dipRequest.getStatus());
      VitamError error = getErrorEntity(status, status.getReasonPhrase(),
                                        "Internal error while processing the DIP");
      String err = error.toString();
      try {
        FileUtils.write(file, err, StandardCharsets.UTF_8);
      } catch (IOException e) {
        // very bad
        logger.error("Very bad since cannot save pseudo DIP", e);
        return;
      }
    }
    if (dipRequestFactory.getManagerToWaarp(dipRequest)
                         .sendBackInformation(dipRequestFactory, dipRequest,
                                              file.getAbsolutePath(),
                                              DIP_FAILED)) {
      // Very end of this IngestRequest
      toDelete(dipRequestFactory, dipRequest);
    }
    // else Since not sent, will retry later on: keep as is
  }

  private VitamError getErrorEntity(Status status, String msg,
                                    String description) {
    return new VitamError(status.name()).setHttpCode(status.getStatusCode())
                                        .setContext("access")
                                        .setState("code_vitam").setMessage(msg)
                                        .setDescription(description);
  }
}
