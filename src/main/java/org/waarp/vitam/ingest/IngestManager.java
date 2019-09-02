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

import com.google.common.base.Charsets;
import fr.gouv.vitam.common.LocalDateUtil;
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.client.VitamContext;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamClientException;
import fr.gouv.vitam.common.external.client.IngestCollection;
import fr.gouv.vitam.common.i18n.VitamLogbookMessages;
import fr.gouv.vitam.common.model.LocalFile;
import fr.gouv.vitam.common.model.ProcessState;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.stream.StreamUtils;
import fr.gouv.vitam.ingest.external.api.exception.IngestExternalException;
import fr.gouv.vitam.ingest.external.client.IngestExternalClient;
import org.apache.commons.io.FileUtils;
import org.waarp.common.logging.WaarpLogger;
import org.waarp.common.logging.WaarpLoggerFactory;
import org.waarp.openr66.client.SubmitTransfer;
import org.waarp.openr66.database.DbConstantR66;
import org.waarp.openr66.protocol.configuration.Configuration;
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
import java.util.Map;
import java.util.Map.Entry;

import static java.nio.file.StandardCopyOption.*;
import static org.waarp.vitam.ingest.IngestRequest.*;

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

  private IngestManager() {
    // Empty
  }

  static void retryAllExistingFiles(
      final IngestRequestFactory ingestRequestFactory,
      final IngestExternalClient client) {
    try {
      List<IngestRequest> ingestRequests =
          ingestRequestFactory.getExistingIngestFactory();
      for (IngestRequest ingestRequest : ingestRequests) {
        logger.info("Will run {}", ingestRequest);
        while (runStep(ingestRequestFactory, client, ingestRequest)) {
          // Executing next step
          logger.info("Will run {}", ingestRequest);
        }
      }
    } catch (InvalidParseOperationException e) {
      // very bad
      logger.error("Very bad since cannot save IngestRequest", e);
    }

  }

  private static boolean runStep(
      final IngestRequestFactory ingestRequestFactory,
      final IngestExternalClient client, final IngestRequest ingestRequest)
      throws InvalidParseOperationException {
    IngestStep step = ingestRequest.getStep();
    switch (step) {
      case STARTUP:
        // Ignore: request not ready for the manager
        break;
      case RETRY_INGEST:
        // restart from ingest
        logger.info("Restart from Ingest: {}", ingestRequest);
        ingestLocally(ingestRequestFactory, ingestRequest, client);
        break;
      case RETRY_INGEST_ID:
        // restart once ingest accepted but no feedback yet
        logger.info("Restart from Ingest Id: {}", ingestRequest);
        sendBackId(ingestRequestFactory, ingestRequest);
        break;
      case RETRY_ATR:
        // restart from ATR
        logger.info("Restart from ATR: {}", ingestRequest);
        getStatusOfATR(ingestRequestFactory, ingestRequest, client,
                       ingestRequest.getVitamContext());
        break;
      case RETRY_ATR_FORWARD:
        // Write back the content of the ATR through Waarp
        File targetFile = ingestRequest.getAtrFile();
        if (!sendBackInformation(ingestRequest, targetFile.getAbsolutePath())) {
          // ATR already there but not sent, so retry
          ingestRequest.setStep(IngestStep.RETRY_ATR_FORWARD, 0);
        } else {
          toDelete(ingestRequestFactory, ingestRequest);
        }
        break;
      case ERROR:
        sendErrorBack(ingestRequestFactory, ingestRequest);
        break;
      case END:
        // To be deleted
        toDelete(ingestRequestFactory, ingestRequest);
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + step);
    }
    IngestStep newStep = ingestRequest.getStep();
    return newStep != IngestStep.END && newStep != step;
  }

  static void ingestLocally(final IngestRequestFactory ingestRequestFactory,
                            final IngestRequest ingestRequest,
                            final IngestExternalClient client) {
    try {
      // Inform Vitam of an Ingest to proceed locally
      VitamContext vitamContext = ingestRequest.getVitamContext();
      LocalFile localFile = ingestRequest.getLocalFile();
      RequestResponse requestResponse = client
          .ingestLocal(vitamContext, localFile, ingestRequest.getContextId(),
                       ingestRequest.getAction());
      if (!requestResponse.isOk()) {
        Status status = Status.fromStatusCode(requestResponse.getStatus());
        switch (status) {
          case SERVICE_UNAVAILABLE:
            // Should retry later on
            logger.error("{}\n\t{}", "Issue since service or ATR unavailable",
                         requestResponse);
            ingestRequest.setStep(IngestStep.RETRY_INGEST, 0);
            break;
          default:
            // Very Bad: inform back of error
            logger
                .error("{}\n\t{}", ISSUE_SINCE_INGEST_PACKET_PRODUCES_AN_ERROR,
                       requestResponse);
            ingestRequest.setStep(IngestStep.ERROR, status.getStatusCode());
            // Will inform back of error which could not be fixed when reloaded
        }
        // Next step is RETRY or ERROR
        return;
      }
      // Ingest sent and accepted
      RequestResponseOK responseOK = (RequestResponseOK) requestResponse;
      ingestRequest.setFromRequestResponse(responseOK);

      // Log Debug only
      checkFeedbackLog(ingestRequest, responseOK);

      // Inform back of ID whatever: could be the last step
      sendBackId(ingestRequestFactory, ingestRequest);
    } catch (InvalidParseOperationException e) {
      logger.error("FATAL: Issue since backup of request produces an error", e);
    } catch (IngestExternalException e) {
      logger.error(ISSUE_SINCE_INGEST_PACKET_PRODUCES_AN_ERROR, e);
      // Should retry ingest from the beginning
      try {
        ingestRequest.setStep(IngestStep.RETRY_INGEST, 0);
      } catch (InvalidParseOperationException ex) {
        // very bad
        logger.error("FATAL: Very bad since cannot save IngestRequest", ex);
      }
    }
  }

  private static void sendBackId(
      final IngestRequestFactory ingestRequestFactory,
      final IngestRequest ingestRequest) throws InvalidParseOperationException {
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
        ingestRequest.setStep(IngestStep.RETRY_INGEST_ID, 0);
        return;
      }
      if (sendBackInformation(ingestRequest, idMessage.getAbsolutePath())) {
        // Possibly (optional) waiting for ATR back or not
        if (ingestRequest.isCheckAtr()) {
          ingestRequest.setStep(IngestStep.RETRY_ATR, 0);
        } else {
          // No ATR Back so Very end of this IngestRequest
          toDelete(ingestRequestFactory, ingestRequest);
        }
      } else {
        // Not sent, so retry later on
        ingestRequest.setStep(IngestStep.RETRY_INGEST_ID, 0);
      }
    } finally {
      idMessage.delete();
    }
  }

  private static void getStatusOfATR(
      final IngestRequestFactory ingestRequestFactory,
      final IngestRequest ingestRequest, final IngestExternalClient client,
      final VitamContext vitamContext) throws InvalidParseOperationException {
    Response response = null;
    try {
      response = client
          .downloadObjectAsync(vitamContext, ingestRequest.getRequestId(),
                               IngestCollection.ARCHIVETRANSFERREPLY);
      Status status = Status.fromStatusCode(response.getStatus());
      switch (status) {
        case OK:
          sendATR(ingestRequestFactory, ingestRequest, response);
          return;
        case SERVICE_UNAVAILABLE:
        case NOT_FOUND:
          // Should retry later on
          logger.error("Issue since service or ATR unavailable\n\t{}",
                       status.getReasonPhrase());
          ingestRequest.setStep(IngestStep.RETRY_ATR, 0);
          return;
        default:
          // Very Bad: inform back of error??
          logger.error("{}\n\t{}", ISSUE_SINCE_INGEST_PACKET_PRODUCES_AN_ERROR,
                       status.getReasonPhrase());
          ingestRequest.setStep(IngestStep.ERROR, response.getStatus());
      }
    } catch (VitamClientException e) {
      logger.error("Issue since ingest client produces an error", e);
      ingestRequest.setStep(IngestStep.RETRY_ATR, 0);
    } finally {
      // Shall read all InputStream
      StreamUtils.consumeAnyEntityAndClose(response);
    }
  }

  private static void sendATR(final IngestRequestFactory ingestRequestFactory,
                              final IngestRequest ingestRequest,
                              final Response response)
      throws InvalidParseOperationException {
    final InputStream inputStream = response.readEntity(InputStream.class);
    // Write file to be forwarded
    File targetFile = ingestRequest.getAtrFile();
    Path target = targetFile.toPath();
    try {
      Files.copy(inputStream, target, REPLACE_EXISTING);
    } catch (IOException e) {
      logger.error("File must be writable", e);
      ingestRequest.setStep(IngestStep.ERROR,
                            Status.INTERNAL_SERVER_ERROR.getStatusCode());
      return;
    }
    // Write back the content of the ATR through Waarp
    if (!sendBackInformation(ingestRequest, targetFile.getAbsolutePath())) {
      // ATR already there but not sent, so retry
      ingestRequest.setStep(IngestStep.RETRY_ATR_FORWARD, 0);
      return;
    }
    toDelete(ingestRequestFactory, ingestRequest);
  }

  private static boolean sendBackInformation(final IngestRequest ingestRequest,
                                             final String filename) {
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
    return future.isSuccess();
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
   * @param input to read
   *
   * @return String
   *
   * @throws IOException
   */
  private static String readInputStream(InputStream input) throws IOException {
    return readInputStreamLimited(input, Integer.MAX_VALUE);
  }

  private static void toDelete(final IngestRequestFactory ingestRequestFactory,
                               final IngestRequest ingestRequest)
      throws InvalidParseOperationException {
    // Ensure it will not be reloaded
    ingestRequest.setStep(IngestStep.END, 0);
    if (!ingestRequestFactory.removeIngestRequest(ingestRequest)) {
      logger
          .error("Issue while cleaning this IngestRequest: {}", ingestRequest);
    }
  }

  private static void sendErrorBack(
      final IngestRequestFactory ingestRequestFactory,
      final IngestRequest ingestRequest) throws InvalidParseOperationException {
    logger.error("Error to feedback since status not ok to restart: {}",
                 ingestRequest);
    // Feedback through Waarp
    File file = ingestRequest.getAtrFile();
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
        FileUtils.write(file, atr, Charsets.UTF_8);
      } catch (IOException e) {
        // very bad
        logger.error("Very bad since cannot save pseudo ATR", e);
        return;
      }
    }
    if (sendBackInformation(ingestRequest, file.getAbsolutePath())) {
      // Very end of this IngestRequest
      toDelete(ingestRequestFactory, ingestRequest);
    }
    // else Since not sent, will retry later on: keep as is
  }

  private static void checkFeedbackLog(final IngestRequest ingestRequest,
                                       final RequestResponseOK responseOK) {
    if (logger.isDebugEnabled()) {
      Map<String, String> vitamHeaders = responseOK.getVitamHeaders();
      for (Entry<String, String> entry : vitamHeaders.entrySet()) {
        logger.debug("{} = {}", entry.getKey(), entry.getValue());
      }
      ProcessState processState = ingestRequest.getProcessState();
      if (processState != ProcessState.PAUSE) {
        // Error
        logger.warn("Not PAUSE: {}", ingestRequest.getGlobalExecutionState());
      }
      StatusCode statusCode = ingestRequest.getStatusCode();
      if (statusCode != StatusCode.UNKNOWN) {
        // Error
        logger
            .error("Not UNKNOWN: {}", ingestRequest.getGlobalExecutionStatus());
      }
    }
  }

}
