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

import fr.gouv.vitam.common.GlobalDataRest;
import fr.gouv.vitam.common.LocalDateUtil;
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.client.VitamContext;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamClientException;
import fr.gouv.vitam.common.external.client.IngestCollection;
import fr.gouv.vitam.common.i18n.VitamLogbookMessages;
import fr.gouv.vitam.common.model.LocalFile;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.stream.StreamUtils;
import fr.gouv.vitam.ingest.external.api.exception.IngestExternalException;
import fr.gouv.vitam.ingest.external.client.IngestExternalClient;
import org.apache.commons.io.FileUtils;
import org.waarp.common.database.exception.WaarpDatabaseException;
import org.waarp.common.logging.WaarpLogger;
import org.waarp.common.logging.WaarpLoggerFactory;
import org.waarp.openr66.client.SubmitTransfer;
import org.waarp.openr66.database.DbConstantR66;
import org.waarp.openr66.database.data.DbHostAuth;
import org.waarp.openr66.database.data.DbTaskRunner;
import org.waarp.openr66.protocol.configuration.Configuration;
import org.waarp.openr66.protocol.exception.OpenR66ProtocolNoSslException;
import org.waarp.openr66.protocol.utils.R66Future;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static java.nio.file.StandardCopyOption.*;
import static org.waarp.vitam.ingest.IngestRequest.*;

/**
 * IngestManager is the central logic for Ingest management between Waarp and
 * Vitam
 */
public class IngestManager {
  /**
   * Internal Logger
   */
  private static final WaarpLogger logger =
      WaarpLoggerFactory.getLogger(IngestManager.class);
  private static final String INGEST_INT_UPLOAD = "STP_UPLOAD_SIP";
  private static final String ATR_KO_DEFAULT_XML = "ATR_KO_DEFAULT.xml";
  private static final String DATE = "#MADATE#";
  private static final String MESSAGE_IDENTIFIER = "#MESSAGE_IDENTIFIER#";
  private static final String ARCHIVAL_AGENCY = "#ARCHIVAL_AGENCY#";
  private static final String TRANSFERRING_AGENCY = "#TRANSFERRING_AGENCY#";
  private static final String COMMENT = "#COMMENT#";
  private static final String EVENT_TYPE = "#EVENT_TYPE#";
  private static final String EVENT_TYPE_CODE = "#EVENT_TYPE_CODE#";
  private static final String EVENT_DATE_TIME = "#EVENT_DATE_TIME#";
  private static final String OUTCOME = "#OUTCOME#";
  private static final String OUTCOME_DETAIL = "#OUTCOME_DETAIL#";
  private static final String OUTCOME_DETAIL_MESSAGE =
      "#OUTCOME_DETAIL_MESSAGE#";
  private static final String ISSUE_SINCE_INGEST_PACKET_PRODUCES_AN_ERROR =
      "Issue since ingest packet produces an error";
  protected static final String ERROR_MESSAGE = "{}\n\t{}";

  IngestManagerToWaarp ingestManagerToWaarp = new IngestManagerToWaarp();

  IngestManager() {
    // Empty
  }

  /**
   * Get all existing IngestRequest and try to continue according to their
   * status
   *
   * @param ingestRequestFactory
   * @param client
   * @param stopFile
   */
  void retryAllExistingFiles(final IngestRequestFactory ingestRequestFactory,
                             final IngestExternalClient client, File stopFile) {
    try {
      List<IngestRequest> ingestRequests =
          ingestRequestFactory.getExistingIngestFactory();
      for (IngestRequest ingestRequest : ingestRequests) {
        if (stopFile.exists()) {
          return;
        }
        logger.info("Will run {}", ingestRequest);
        while (runStep(ingestRequestFactory, client, ingestRequest)) {
          // Executing next step
          if (ingestRequest.getStep() == null) {
            // END
            break;
          }
          logger.debug("Will rerun {}", ingestRequest);
        }
      }
    } catch (InvalidParseOperationException e) {
      // very bad
      logger.error("Very bad since cannot save IngestRequest", e);
    }

  }

  /**
   * Rune next step for this IngestRequest
   *
   * @param ingestRequestFactory
   * @param client
   * @param ingestRequest
   *
   * @return true if it is possible to run again the next step for this
   *     IngestRequest
   *
   * @throws InvalidParseOperationException
   */
  private boolean runStep(final IngestRequestFactory ingestRequestFactory,
                          final IngestExternalClient client,
                          final IngestRequest ingestRequest)
      throws InvalidParseOperationException {
    IngestStep step = ingestRequest.getStep();
    logger.debug("Step is {} from {}", step, ingestRequest);
    switch (step) {
      case STARTUP:
        // Ignore: request not ready for the manager
        break;
      case RETRY_INGEST:
        // restart from ingest
        logger.info("Start from Ingest: {}", ingestRequest);
        ingestLocally(ingestRequestFactory, ingestRequest, client);
        break;
      case RETRY_INGEST_ID:
        // restart once ingest accepted but no feedback yet
        logger.info("From Ingest Id: {}", ingestRequest);
        sendBackId(ingestRequestFactory, ingestRequest);
        break;
      case RETRY_ATR:
        // restart from ATR
        logger.info("From ATR: {}", ingestRequest);
        getStatusOfATR(ingestRequestFactory, ingestRequest, client,
                       ingestRequest.getVitamContext());
        break;
      case RETRY_ATR_FORWARD:
        // Write back the content of the ATR through Waarp
        logger.info("From ATR_FORWARD: {}", ingestRequest);
        File targetFile = ingestRequest.getAtrFile(ingestRequestFactory);
        sendATRFile(ingestRequestFactory, ingestRequest, targetFile);
        break;
      case ERROR:
        logger.info("From Error: {}", ingestRequest);
        sendErrorBack(ingestRequestFactory, ingestRequest);
        break;
      case END:
        // To be deleted
        logger.info("End of Ingest: {}", ingestRequest);
        toDelete(ingestRequestFactory, ingestRequest);
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + step);
    }
    IngestStep newStep = ingestRequest.getStep();
    return newStep != IngestStep.END && newStep != step;
  }

  /**
   * Try to launch first step of Ingest (step 1)
   *
   * @param ingestRequestFactory
   * @param ingestRequest
   * @param client
   *
   * @return True if OK
   */
  boolean ingestLocally(final IngestRequestFactory ingestRequestFactory,
                        final IngestRequest ingestRequest,
                        final IngestExternalClient client) {
    try {
      // Inform Vitam of an Ingest to proceed locally
      ingestRequest.setStep(IngestStep.RETRY_INGEST, 0, ingestRequestFactory);
      VitamContext vitamContext = ingestRequest.getVitamContext();
      LocalFile localFile = ingestRequest.getLocalFile();
      RequestResponse requestResponse = client
          .ingestLocal(vitamContext, localFile, ingestRequest.getContextId(),
                       ingestRequest.getAction());
      if (!requestResponse.isOk()) {
        String requestIdNew =
            requestResponse.getHeaderString(GlobalDataRest.X_REQUEST_ID);
        if (requestIdNew == null || requestIdNew.isEmpty()) {
          requestIdNew = "FAKE_REQUEST_ID";
        }
        ingestRequest.setRequestId(requestIdNew);
        Status status = Status.fromStatusCode(requestResponse.getStatus());
        switch (status) {
          case SERVICE_UNAVAILABLE:
            // Should retry later on
            logger.warn(ERROR_MESSAGE, "Issue since service or ATR unavailable",
                        requestResponse);
            ingestRequest
                .setStep(IngestStep.RETRY_INGEST, 0, ingestRequestFactory);
            break;
          default:
            // Very Bad: inform back of error
            logger.error(ERROR_MESSAGE,
                         ISSUE_SINCE_INGEST_PACKET_PRODUCES_AN_ERROR,
                         requestResponse);
            ingestRequest.setStep(IngestStep.ERROR, status.getStatusCode(),
                                  ingestRequestFactory);
            // Will inform back of error which could not be fixed when reloaded
        }
        // Next step is RETRY or ERROR
        return false;
      }
      // Ingest sent and accepted
      RequestResponseOK responseOK = (RequestResponseOK) requestResponse;
      ingestRequest.setFromRequestResponse(responseOK);

      // Inform back of ID whatever: could be the last step
      return sendBackId(ingestRequestFactory, ingestRequest);
    } catch (InvalidParseOperationException e) {
      logger.error("FATAL: Issue since backup of request produces an error", e);
    } catch (IngestExternalException e) {
      logger.error(ISSUE_SINCE_INGEST_PACKET_PRODUCES_AN_ERROR, e);
      // Should retry ingest from the beginning
      try {
        ingestRequest.setStep(IngestStep.RETRY_INGEST, 0, ingestRequestFactory);
      } catch (InvalidParseOperationException ex) {
        // very bad
        logger.error("FATAL: Very bad since cannot save IngestRequest", ex);
      }
    }
    return false;
  }

  /**
   * Once IngestRequest started, send back the Id of the corresponding Vitam
   * Ingest Id Operation (step 2)
   *
   * @param ingestRequestFactory
   * @param ingestRequest
   *
   * @return True if done
   *
   * @throws InvalidParseOperationException
   */
  private boolean sendBackId(final IngestRequestFactory ingestRequestFactory,
                             final IngestRequest ingestRequest)
      throws InvalidParseOperationException {
    File idMessage = new File("/tmp/" + ingestRequest.getRequestId() + ".xml");
    try {
      String atr = buildAtrInternal(ingestRequest.getRequestId(),
                                    "ArchivalAgencyToBeDefined",
                                    "TransferringAgencyToBeDefined",
                                    INGEST_INT_UPLOAD, "(Accepted by Vitam)",
                                    StatusCode.STARTED, LocalDateUtil.now());
      try {
        FileUtils.write(idMessage, atr, StandardCharsets.UTF_8);
      } catch (IOException e) {
        // very bad, so retry later on
        logger.error("Very bad since cannot save pseudo ATR", e);
        ingestRequest
            .setStep(IngestStep.RETRY_INGEST_ID, 0, ingestRequestFactory);
        return false;
      }
      ingestRequest
          .setStep(IngestStep.RETRY_INGEST_ID, 0, ingestRequestFactory);
      if (ingestManagerToWaarp
          .sendBackInformation(ingestRequestFactory, ingestRequest,
                               idMessage.getAbsolutePath())) {
        // Possibly (optional) waiting for ATR back or not
        if (ingestRequest.isCheckAtr()) {
          ingestRequest.setStep(IngestStep.RETRY_ATR, 0, ingestRequestFactory);
        } else {
          // No ATR Back so Very end of this IngestRequest
          toDelete(ingestRequestFactory, ingestRequest);
        }
        return true;
      } else {
        // Not sent, so retry later on
        return false;
      }
    } finally {
      try {
        Files.delete(idMessage.toPath());
      } catch (IOException e) {
        logger.debug("Temporary file not deleted {}",
                     idMessage.getAbsolutePath());
      }
    }
  }

  /**
   * Get the ATR (step 3 if allowed)
   *
   * @param ingestRequestFactory
   * @param ingestRequest
   * @param client
   * @param vitamContext
   *
   * @return True if OK
   *
   * @throws InvalidParseOperationException
   */
  boolean getStatusOfATR(final IngestRequestFactory ingestRequestFactory,
                         final IngestRequest ingestRequest,
                         final IngestExternalClient client,
                         final VitamContext vitamContext)
      throws InvalidParseOperationException {
    Response response = null;
    try {

      ingestRequest.setStep(IngestStep.RETRY_ATR, 0, ingestRequestFactory);
      response = client
          .downloadObjectAsync(vitamContext, ingestRequest.getRequestId(),
                               IngestCollection.ARCHIVETRANSFERREPLY);
      Status status = Status.fromStatusCode(response.getStatus());
      switch (status) {
        case OK:
          sendATR(ingestRequestFactory, ingestRequest, response);
          return true;
        case SERVICE_UNAVAILABLE:
        case NOT_FOUND:
          // Should retry later on
          logger.debug("Service or ATR unavailable yet\n\t{}",
                       status.getReasonPhrase());
          return false;
        default:
          // Very Bad: inform back of error
          logger
              .error(ERROR_MESSAGE, ISSUE_SINCE_INGEST_PACKET_PRODUCES_AN_ERROR,
                     status.getReasonPhrase());
          ingestRequest.setStep(IngestStep.ERROR, response.getStatus(),
                                ingestRequestFactory);
      }
    } catch (VitamClientException e) {
      logger.warn("Issue since ingest client produces an error", e);
    } finally {
      // Shall read all InputStream
      StreamUtils.consumeAnyEntityAndClose(response);
    }
    return false;
  }

  /**
   * Send the ATR back to the Waarp Partner, directly from step 3 (ingest ATR
   * retrieve) (step 4)
   *
   * @param ingestRequestFactory
   * @param ingestRequest
   * @param response
   *
   * @throws InvalidParseOperationException
   */
  private void sendATR(final IngestRequestFactory ingestRequestFactory,
                       final IngestRequest ingestRequest,
                       final Response response)
      throws InvalidParseOperationException {
    try (final InputStream inputStream = response
        .readEntity(InputStream.class)) {
      // Write file to be forwarded
      File targetFile = ingestRequest.getAtrFile(ingestRequestFactory);
      Path target = targetFile.toPath();
      Files.copy(inputStream, target, REPLACE_EXISTING);
      // Write back the content of the ATR through Waarp
      sendATRFile(ingestRequestFactory, ingestRequest, targetFile);
    } catch (IOException e) {
      logger
          .error("File must be writable or InputStream error during close", e);
      ingestRequest.setStep(IngestStep.ERROR,
                            Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                            ingestRequestFactory);
    }
  }

  /**
   * Step to send ATR before finished (step 4)
   *
   * @param ingestRequestFactory
   * @param ingestRequest
   * @param targetFile
   *
   * @throws InvalidParseOperationException
   */
  private void sendATRFile(final IngestRequestFactory ingestRequestFactory,
                           final IngestRequest ingestRequest,
                           final File targetFile)
      throws InvalidParseOperationException {
    ingestRequest
        .setStep(IngestStep.RETRY_ATR_FORWARD, 0, ingestRequestFactory);
    if (!ingestManagerToWaarp
        .sendBackInformation(ingestRequestFactory, ingestRequest,
                             targetFile.getAbsolutePath())) {
      // ATR already there but not sent, so retry
      ingestRequest
          .setStep(IngestStep.RETRY_ATR_FORWARD, 0, ingestRequestFactory);
    } else {
      toDelete(ingestRequestFactory, ingestRequest);
    }
  }

  /**
   * Finalize IngestRequest, whatever Done or in Error (final step 5 in case
   * of Done)
   *
   * @param ingestRequestFactory
   * @param ingestRequest
   *
   * @throws InvalidParseOperationException
   */
  private void toDelete(final IngestRequestFactory ingestRequestFactory,
                        final IngestRequest ingestRequest)
      throws InvalidParseOperationException {
    // Ensure it will not be reloaded
    ingestRequest.setStep(IngestStep.END, 0, ingestRequestFactory);
    if (!ingestRequestFactory.removeIngestRequest(ingestRequest)) {
      logger
          .error("Issue while cleaning this IngestRequest: {}", ingestRequest);
    } else {
      logger.info("End of IngestRequest: {}", ingestRequest);
    }
  }

  /**
   * If in Error, will send back the status of the operation to the Waarp
   * Partner before ending.
   *
   * @param ingestRequestFactory
   * @param ingestRequest
   *
   * @throws InvalidParseOperationException
   */
  private void sendErrorBack(final IngestRequestFactory ingestRequestFactory,
                             final IngestRequest ingestRequest)
      throws InvalidParseOperationException {
    logger.warn("Error to feedback since status not ok to restart: {}",
                ingestRequest);
    // Feedback through Waarp
    File file = ingestRequest.getAtrFile(ingestRequestFactory);
    if (!file.canRead()) {
      // Create a pseudo one
      String atr = buildAtrInternal(ingestRequest.getRequestId(),
                                    "ArchivalAgencyToBeDefined",
                                    "TransferringAgencyToBeDefined",
                                    INGEST_INT_UPLOAD,
                                    "(Issue during Ingest Step [" +
                                    ingestRequest.getStatus() +
                                    "] while Waarp accessed to Vitam)",
                                    StatusCode.FATAL, LocalDateUtil.now());
      try {
        FileUtils.write(file, atr, StandardCharsets.UTF_8);
      } catch (IOException e) {
        // very bad
        logger.error("Very bad since cannot save pseudo ATR", e);
        return;
      }
    }
    if (ingestManagerToWaarp
        .sendBackInformation(ingestRequestFactory, ingestRequest,
                             file.getAbsolutePath())) {
      // Very end of this IngestRequest
      toDelete(ingestRequestFactory, ingestRequest);
    }
    // else Since not sent, will retry later on: keep as is
  }

  /**
   * Class to allow Mock of Waarp sending back to Waarp Partner
   */
  static class IngestManagerToWaarp {
    IngestManagerToWaarp() {
      // nothing
    }

    /**
     * Launch a SubmitTransfer according to arguments
     *
     * @param ingestRequestFactory
     * @param ingestRequest
     * @param filename
     *
     * @return True if done
     *
     * @throws InvalidParseOperationException
     */
    boolean sendBackInformation(final IngestRequestFactory ingestRequestFactory,
                                final IngestRequest ingestRequest,
                                final String filename)
        throws InvalidParseOperationException {
      logger.debug("Will send {} while step is {}", filename,
                   ingestRequest.getStep());
      R66Future future = new R66Future(true);
      SubmitTransfer submitTransfer =
          new SubmitTransfer(future, ingestRequest.getWaarpPartner(), filename,
                             ingestRequest.getWaarpRule(),
                             ingestRequest.getRequestId() + ':' +
                             ingestRequest.getPath(), true,
                             Configuration.configuration.getBlockSize(),
                             DbConstantR66.ILLEGALVALUE, null);
      submitTransfer.run();
      future.awaitOrInterruptible();
      if (future.isSuccess()) {
        ingestRequest.setWaarpId(future.getResult().getRunner().getSpecialId())
                     .save(ingestRequestFactory);
        return waitForAllDone(ingestRequest);
      }
      return false;
    }

    /**
     * Ensure that SubmitTransfer is done totally (file sent) before continuing
     *
     * @param ingestRequest
     *
     * @return True if done
     */
    private boolean waitForAllDone(IngestRequest ingestRequest) {
      while (true) {
        try {
          DbHostAuth dbHostAuth =
              new DbHostAuth(ingestRequest.getWaarpPartner());
          DbTaskRunner checkedRunner =
              new DbTaskRunner(ingestRequest.getWaarpId(),
                               Configuration.configuration
                                   .getHostId(dbHostAuth.isSsl()),
                               ingestRequest.getWaarpPartner());
          if (checkedRunner.isAllDone()) {
            logger.info("DbTaskRunner done");
            return true;
          } else if (checkedRunner.isInError()) {
            logger.warn("DbTaskRunner in error for {}", ingestRequest);
            return false;
          }
          Thread.sleep(500);
        } catch (InterruptedException e) {//NOSONAR
          logger.error("Interrupted", e);
          return false;
        } catch (WaarpDatabaseException e) {
          logger.error("Cannot found DbTaskRunner", e);
          return false;
        } catch (OpenR66ProtocolNoSslException e) {
          logger.error("Cannot found HostSslId", e);
          return false;
        }
      }
    }
  }


  /**
   * To generate a default ATR KO from Ingest External on AV or MimeType checks.
   *
   * @param messageIdentifier
   * @param archivalAgency
   * @param transferringAgency
   * @param eventType
   * @param addedMessage might be null
   * @param code
   *
   * @return the corresponding InputStream with the ATR KO in XML format
   */
  private static String buildAtrInternal(String messageIdentifier,
                                         String archivalAgency,
                                         String transferringAgency,
                                         String eventType, String addedMessage,
                                         StatusCode code,
                                         LocalDateTime eventDateTime) {
    String xmlDefault;
    try {
      xmlDefault = readInputStream(
          PropertiesUtils.getResourceAsStream(ATR_KO_DEFAULT_XML));
    } catch (final IOException e) {
      // Should not be, but in case, get the String equivalent
      xmlDefault = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                   "<ArchiveTransferReply xmlns:xlink=\"http://www.w3.org/1999/xlink\"\n" +
                   " xmlns:pr=\"info:lc/xmlns/premis-v2\"\n" +
                   " xmlns=\"fr:gouv:culture:archivesdefrance:seda:v2.1\"\n" +
                   " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                   " xsi:schemaLocation=\"fr:gouv:culture:archivesdefrance:seda:v2.1 seda-2.1-main.xsd\">\n" +
                   "    <Comment>#COMMENT#</Comment>\n" +
                   "    <Date>#MADATE#</Date>\n" +
                   "    <MessageIdentifier>#MESSAGE_IDENTIFIER#</MessageIdentifier>\n" +
                   "    \n" + "    <CodeListVersions>\n" +
                   "        <ReplyCodeListVersion>ReplyCodeListVersion0</ReplyCodeListVersion>\n" +
                   "        <MessageDigestAlgorithmCodeListVersion>MessageDigestAlgorithmCodeListVersion0</MessageDigestAlgorithmCodeListVersion>\n" +
                   "        <FileFormatCodeListVersion>FileFormatCodeListVersion0</FileFormatCodeListVersion>\n" +
                   "    </CodeListVersions>\n" + "\n" +
                   "    <ReplyCode>#OUTCOME#</ReplyCode>\n" +
                   "    <Operation>\n" + "        <Event>\n" +
                   "            <EventTypeCode>#EVENT_TYPE_CODE#</EventTypeCode>\n" +
                   "            <EventType>#EVENT_TYPE#</EventType>\n" +
                   "            <EventDateTime>#EVENT_DATE_TIME#</EventDateTime>\n" +
                   "            <Outcome>#OUTCOME#</Outcome>\n" +
                   "            <OutcomeDetail>#OUTCOME_DETAIL#</OutcomeDetail>\n" +
                   "            <OutcomeDetailMessage>#OUTCOME_DETAIL_MESSAGE#</OutcomeDetailMessage>\n" +
                   "        </Event>\n" + "    </Operation>\n" + "\n" +
                   "    <MessageRequestIdentifier>Unknown</MessageRequestIdentifier>\n" +
                   "    <ArchivalAgency>\n" +
                   "        <Identifier>#ARCHIVAL_AGENCY#</Identifier>\n" +
                   "    </ArchivalAgency>\n" + "    <TransferringAgency>\n" +
                   "        <Identifier>#TRANSFERRING_AGENCY#</Identifier>\n" +
                   "    </TransferringAgency>\n" + "</ArchiveTransferReply>\n";
    }
    String detail = VitamLogbookMessages.getCodeOp(eventType, code);
    if (addedMessage != null) {
      detail += addedMessage;
    }
    String event = VitamLogbookMessages.getLabelOp(eventType);
    return xmlDefault.replace(DATE, LocalDateUtil.now().toString())
                     .replace(MESSAGE_IDENTIFIER, messageIdentifier)
                     .replace(ARCHIVAL_AGENCY, archivalAgency)
                     .replace(TRANSFERRING_AGENCY, transferringAgency)
                     .replace(COMMENT, detail)
                     .replace(EVENT_TYPE_CODE, eventType)
                     .replace(EVENT_TYPE, event)
                     .replace(EVENT_DATE_TIME, eventDateTime.toString())
                     .replaceAll(OUTCOME, code.name())
                     .replace(OUTCOME_DETAIL, eventType + "." + code.name())
                     .replace(OUTCOME_DETAIL_MESSAGE, detail);
  }

  /**
   * Internal
   *
   * @param input to read
   *
   * @return String
   *
   * @throws IOException
   */
  private static String readInputStreamLimited(InputStream input, int limit)
      throws IOException {
    final StringBuilder builder = new StringBuilder();
    try (final InputStreamReader reader = new InputStreamReader(input)) {
      try (final BufferedReader buffered = new BufferedReader(reader)) {
        String line;
        while ((line = buffered.readLine()) != null) {
          builder.append(line).append('\n');
          if (builder.length() >= limit) {
            break;
          }
        }
      }
    }
    return builder.toString();
  }

  /**
   * Internal
   *
   * @param input to read
   *
   * @return String
   *
   * @throws IOException
   */
  private static String readInputStream(InputStream input) throws IOException {
    return readInputStreamLimited(input, Integer.MAX_VALUE);
  }

}
