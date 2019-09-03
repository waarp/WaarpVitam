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
import fr.gouv.vitam.common.error.VitamError;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.external.client.AbstractMockClient;
import fr.gouv.vitam.common.external.client.ClientMockResultHelper;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.model.LocalFile;
import fr.gouv.vitam.common.model.ProcessState;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.server.application.junit.ResteasyTestApplication;
import fr.gouv.vitam.common.serverv2.VitamServerTestRunner;
import fr.gouv.vitam.ingest.external.client.IngestExternalClient;
import fr.gouv.vitam.ingest.external.client.IngestExternalClientFactory;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.waarp.common.logging.WaarpLogLevel;
import org.waarp.common.logging.WaarpLogger;
import org.waarp.common.logging.WaarpLoggerFactory;
import org.waarp.common.logging.WaarpSlf4JLoggerFactory;
import org.waarp.vitam.ingest.IngestManager.IngestManagerToWaarp;
import org.waarp.vitam.ingest.IngestRequest.IngestStep;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class IngestTaskTest extends ResteasyTestApplication {
  /**
   * Internal Logger
   */
  private static final WaarpLogger logger =
      WaarpLoggerFactory.getLogger(IngestTaskTest.class);

  private static final String HOSTNAME = "localhost";
  private static final String PATH = "/ingest-external/v1";
  private static final String MOCK_INPUTSTREAM_CONTENT =
      "VITAM-Ingest External Client Rest Mock InputStream";
  private static final String FAKE_X_REQUEST_ID =
      GUIDFactory.newRequestIdGUID(0).getId();
  private static final String CONTEXT_ID = "defaultContext";
  private static final String EXECUTION_MODE = IngestRequest.RESUME;
  private static final String ID = "id1";
  private final static ExpectedResults mock = mock(ExpectedResults.class);
  private static IngestExternalClient client;
  private static IngestExternalClientFactory factory =
      IngestExternalClientFactory.getInstance();
  private static VitamServerTestRunner vitamServerTestRunner =
      new VitamServerTestRunner(IngestTaskTest.class, factory);
  private static IngestRequestFactory ingestRequestFactory;
  private static IngestManagerToWaarp ingestManagerToWaarp;
  private static IngestManager ingestManager;
  private final int TENANT_ID = 0;

  @BeforeClass
  public static void setUpBeforeClass() throws Throwable {
    WaarpLoggerFactory
        .setDefaultFactory(new WaarpSlf4JLoggerFactory(WaarpLogLevel.WARN));
    vitamServerTestRunner.start();
    client = (IngestExternalClient) vitamServerTestRunner.getClient();
    ingestRequestFactory = mock(IngestRequestFactory.class);
    doCallRealMethod().when(ingestRequestFactory).setBaseDir();
    doCallRealMethod().when(ingestRequestFactory).getExistingIngestFactory();
    doCallRealMethod().when(ingestRequestFactory)
                      .removeIngestRequest(any(IngestRequest.class));
    doCallRealMethod().when(ingestRequestFactory)
                      .getXmlAtrFile(any(IngestRequest.class));
    doCallRealMethod().when(ingestRequestFactory)
                      .saveIngestRequest(any(IngestRequest.class));
    doCallRealMethod().when(ingestRequestFactory)
                      .saveNewIngestRequest(any(IngestRequest.class));
    doCallRealMethod().when(ingestRequestFactory)
                      .getSpecificIngestRequest(anyString());
    when(ingestRequestFactory.getClient()).thenReturn(client);
    ingestRequestFactory.setBaseDir();
    ingestManager = new IngestManager();
    ingestManagerToWaarp = mock(IngestManagerToWaarp.class);
    ingestManager.ingestManagerToWaarp = ingestManagerToWaarp;
    setSendMessage(true);

    final ClassLoader classLoader = IngestTaskTest.class.getClassLoader();
    final File file = new File(classLoader.getResource("vitam.conf").getFile());
    CommonUtil.launchServers();
  }

  @AfterClass
  public static void tearDownAfterClass() throws Throwable {
    vitamServerTestRunner.runAfter();
    CommonUtil.stopServers();
  }

  @Override
  public Set<Object> getResources() {
    HashSet<Object> set = new HashSet<Object>();
    set.add(new MockResource(mock));
    return set;
  }

  @Before
  public void cleanIngestRequest() {
    List<IngestRequest> list = ingestRequestFactory.getExistingIngestFactory();
    if (list != null && !list.isEmpty()) {
      for (IngestRequest ingestRequest : list) {
        logger.info("Clean {}", ingestRequest);
        ingestRequestFactory.removeIngestRequest(ingestRequest);
      }
    }
  }

  @Test
  public void testOptions() {
    IngestTask.main(new String[] {});
    IngestMonitor.main(new String[] {});
    IngestTask.main(new String[] {
        "-t", "notANumber", "-f", "/tmp/test", "-a", "access", "-x",
        "DEFAULT_WORKFLOW", "-p", "hosta", "-r", "send", "-w", "/tmp/test",
        "-c", "certificate", "-k", "-n", "RESUME", "-s", "session"
    });
    IngestMonitor.main(new String[] {
        "-e", "notANumber", "-s", "/tmp/test", "-w", "/tmp/test",
        "-D" + IngestRequestFactory.ORG_WAARP_INGEST_BASEDIR + "=" +
        IngestRequestFactory.TMP_INGEST_FACTORY
    });
  }

  @Test
  public void givenUploadLocalFileOK() throws Exception {
    when(mock.post()).thenReturn(Response.accepted()
                                         .header(GlobalDataRest.X_REQUEST_ID,
                                                 "FAKE_X_REQUEST_ID").header(
            GlobalDataRest.X_GLOBAL_EXECUTION_STATE, ProcessState.PAUSE).header(
            GlobalDataRest.X_GLOBAL_EXECUTION_STATUS, StatusCode.UNKNOWN)
                                         .build());
    setSendMessage(true);
    IngestTask task = new IngestTask("path", TENANT_ID, "applicationSessionId",
                                     "personalCertificate", "accessContract",
                                     CONTEXT_ID, EXECUTION_MODE, "hosta",
                                     "send", true, ingestRequestFactory,
                                     ingestManager);
    assertEquals(true, task.invoke());
    List<IngestRequest> list = ingestRequestFactory.getExistingIngestFactory();
    if (list != null && !list.isEmpty()) {
      for (IngestRequest ingestRequest : list) {
        logger.warn("XXXXXXXXXXX {}", ingestRequest);
        ingestRequest.getProcessState();
        ingestRequest.getStatusCode();
      }
    }
  }

  @Test
  public void givenUploadLocalFileKO() throws InvalidParseOperationException {
    when(mock.post()).thenReturn(
        Response.status(Status.INTERNAL_SERVER_ERROR.getStatusCode())
                .header(GlobalDataRest.X_REQUEST_ID, FAKE_X_REQUEST_ID)
                .build());
    setSendMessage(true);

    IngestTask task =
        new IngestTask("path", TENANT_ID, "applicationSessionId", null,
                       "accessContract", CONTEXT_ID, EXECUTION_MODE, "hosta",
                       "send", true, ingestRequestFactory, ingestManager);
    assertEquals(false, task.invoke());
  }

  private VitamError getVitamError(Status status) {
    return new VitamError(status.getReasonPhrase());
  }

  @Test
  public void givenUploadLocalFileUnavailable()
      throws InvalidParseOperationException {
    final MultivaluedHashMap<String, Object> headers =
        new MultivaluedHashMap<String, Object>();
    headers.add(GlobalDataRest.X_REQUEST_ID, FAKE_X_REQUEST_ID);
    VitamError error = getVitamError(Status.SERVICE_UNAVAILABLE);
    AbstractMockClient.FakeInboundResponse fakeResponse =
        new AbstractMockClient.FakeInboundResponse(Status.SERVICE_UNAVAILABLE,
                                                   JsonHandler
                                                       .writeToInpustream(
                                                           error),
                                                   MediaType.APPLICATION_OCTET_STREAM_TYPE,
                                                   headers);
    when(mock.post()).thenReturn(fakeResponse);

    IngestTask task =
        new IngestTask("path", TENANT_ID, "applicationSessionId", null,
                       "accessContract", CONTEXT_ID, EXECUTION_MODE, "hosta",
                       "send", true, ingestRequestFactory, ingestManager);
    assertEquals(false, task.invoke());
  }

  @Test
  public void givenStreamWhenDownloadObjectOK()
      throws InvalidParseOperationException, IOException {

    when(mock.get()).thenReturn(ClientMockResultHelper.getObjectStream());
    setSendMessage(true);
    File file = new File(IngestRequestFactory.TMP_INGEST_FACTORY + "/test");
    FileUtils.write(file, "testContent");
    when(ingestRequestFactory.getXmlAtrFile(any(IngestRequest.class)))
        .thenReturn(file);
    IngestRequest ingestRequest =
        new IngestRequest("path", TENANT_ID, "applicationSessionId", null,
                          "accessContract", CONTEXT_ID, EXECUTION_MODE, "hosta",
                          "send", true, ingestRequestFactory)
            .setRequestId(FAKE_X_REQUEST_ID)
            .setStatus(IngestStep.RETRY_INGEST_ID.getStatusMonitor())
            .setStep(IngestStep.RETRY_ATR, 0, ingestRequestFactory);
    assertEquals(true, ingestManager
        .getStatusOfATR(ingestRequestFactory, ingestRequest, client,
                        ingestRequest.getVitamContext()));
  }

  @Test
  public void givenErrorWhenDownloadObjectKO()
      throws InvalidParseOperationException {

    when(mock.get()).thenReturn(
        Response.status(Status.INTERNAL_SERVER_ERROR.getStatusCode())
                .header(GlobalDataRest.X_REQUEST_ID, FAKE_X_REQUEST_ID)
                .build());

    IngestRequest ingestRequest =
        new IngestRequest("path", TENANT_ID, "applicationSessionId", null,
                          "accessContract", CONTEXT_ID, EXECUTION_MODE, "hosta",
                          "send", true, ingestRequestFactory)
            .setRequestId(FAKE_X_REQUEST_ID)
            .setStatus(IngestStep.RETRY_INGEST_ID.getStatusMonitor())
            .setStep(IngestStep.RETRY_ATR, 0, ingestRequestFactory);
    assertEquals(false, ingestManager
        .getStatusOfATR(ingestRequestFactory, ingestRequest, client,
                        ingestRequest.getVitamContext()));
  }

  @Test
  public void givenErrorWhenDownloadObjectUnavailable()
      throws InvalidParseOperationException {

    when(mock.get()).thenReturn(
        Response.status(Status.SERVICE_UNAVAILABLE.getStatusCode())
                .header(GlobalDataRest.X_REQUEST_ID, FAKE_X_REQUEST_ID)
                .build());

    IngestRequest ingestRequest =
        new IngestRequest("path", TENANT_ID, "applicationSessionId", null,
                          "accessContract", CONTEXT_ID, EXECUTION_MODE, "hosta",
                          "send", true, ingestRequestFactory)
            .setRequestId(FAKE_X_REQUEST_ID)
            .setStatus(IngestStep.RETRY_INGEST_ID.getStatusMonitor())
            .setStep(IngestStep.RETRY_ATR, 0, ingestRequestFactory);
    assertEquals(false, ingestManager
        .getStatusOfATR(ingestRequestFactory, ingestRequest, client,
                        ingestRequest.getVitamContext()));
  }

  @Test
  public void givenErrorWhenDownloadObjectNotFound()
      throws InvalidParseOperationException {

    when(mock.get()).thenReturn(
        Response.status(Status.NOT_FOUND.getStatusCode())
                .header(GlobalDataRest.X_REQUEST_ID, FAKE_X_REQUEST_ID)
                .build());

    IngestRequest ingestRequest =
        new IngestRequest("path", TENANT_ID, "applicationSessionId", null,
                          "accessContract", CONTEXT_ID, EXECUTION_MODE, "hosta",
                          "send", true, ingestRequestFactory)
            .setRequestId(FAKE_X_REQUEST_ID)
            .setStatus(IngestStep.RETRY_INGEST_ID.getStatusMonitor())
            .setStep(IngestStep.RETRY_ATR, 0, ingestRequestFactory);
    assertEquals(false, ingestManager
        .getStatusOfATR(ingestRequestFactory, ingestRequest, client,
                        ingestRequest.getVitamContext()));
  }

  static class MonitorThread extends Thread {
    IngestMonitor monitor;
    AtomicBoolean running = new AtomicBoolean(false);

    @Override
    public void run() {
      running.set(true);
      monitor.invoke();
      running.set(false);
    }
  }

  private IngestRequest newIngestRequest()
      throws InvalidParseOperationException {
    return new IngestRequest("path", TENANT_ID, "applicationSessionId", null,
                             "accessContract", CONTEXT_ID, EXECUTION_MODE,
                             "hosta", "send", true, ingestRequestFactory);
  }

  private static void setSendMessage(boolean success)
      throws InvalidParseOperationException {
    when(ingestManagerToWaarp
             .sendBackInformation(any(IngestRequestFactory.class),
                                  any(IngestRequest.class), anyString()))
        .thenReturn(success);
  }

  private void startIngestRequest(IngestRequest ingestRequest)
      throws InvalidParseOperationException {
    logger.info("RETRY_INGEST");
    ingestRequest.setStep(IngestStep.RETRY_INGEST,
                          IngestStep.RETRY_INGEST.getStatusMonitor(),
                          ingestRequestFactory);
  }

  private MonitorThread startMonitor()
      throws InvalidParseOperationException, InterruptedException {
    logger.warn("START MONITOR\n");
    File stopFile = new File("/tmp/stopMonitor.txt");
    stopFile.delete();

    MonitorThread monitorThread = new MonitorThread();
    monitorThread.monitor =
        new IngestMonitor(100, stopFile, ingestRequestFactory, ingestManager);
    setSendMessage(true);
    monitorThread.setDaemon(true);
    monitorThread.start();
    Thread.sleep(100);
    assertTrue(monitorThread.running.get());
    return monitorThread;
  }

  private void stopMonitor(MonitorThread monitorThread)
      throws InterruptedException, IOException {
    File stopFile = new File("/tmp/stopMonitor.txt");
    logger.warn("STOP MONITOR\n");
    FileUtils.write(stopFile, "Stop");
    Thread.sleep(200);
    assertFalse(monitorThread.running.get());
    logger.info("END of test");
  }

  @Test
  public void ingestMonitorEarlyErrorTest()
      throws InvalidParseOperationException, InterruptedException, IOException {
    MonitorThread monitorThread = startMonitor();

    // Start but early error, then send back Id (not any indeed) and END
    logger.warn("\n\tSTARTUP Scenario early error");
    IngestRequest ingestRequest = newIngestRequest();
    assertTrue(checkIngestRequest(ingestRequestFactory, ingestRequest,
                                  IngestStep.STARTUP));
    when(mock.post()).thenReturn(
        Response.status(Status.INTERNAL_SERVER_ERROR.getStatusCode())
                .header(GlobalDataRest.X_REQUEST_ID, FAKE_X_REQUEST_ID)
                .build());
    startIngestRequest(ingestRequest);
    assertTrue(
        checkIngestRequest(ingestRequestFactory, ingestRequest, IngestStep.END,
                           1000));
    stopMonitor(monitorThread);
  }

  @Test
  public void ingestMonitorErrorAtrTest()
      throws InvalidParseOperationException, InterruptedException, IOException {
    MonitorThread monitorThread = startMonitor();

    // Start but error during ATR, then send back Id and END
    logger.warn("\n\tSTARTUP Scenario error during ATR");
    IngestRequest ingestRequest = newIngestRequest();
    assertTrue(checkIngestRequest(ingestRequestFactory, ingestRequest,
                                  IngestStep.STARTUP));
    when(mock.post()).thenReturn(Response.accepted()
                                         .header(GlobalDataRest.X_REQUEST_ID,
                                                 "FAKE_X_REQUEST_ID").header(
            GlobalDataRest.X_GLOBAL_EXECUTION_STATE, ProcessState.PAUSE).header(
            GlobalDataRest.X_GLOBAL_EXECUTION_STATUS, StatusCode.UNKNOWN)
                                         .build());
    when(mock.get()).thenReturn(
        Response.status(Status.INTERNAL_SERVER_ERROR.getStatusCode())
                .header(GlobalDataRest.X_REQUEST_ID, FAKE_X_REQUEST_ID)
                .build());
    startIngestRequest(ingestRequest);
    assertTrue(
        checkIngestRequest(ingestRequestFactory, ingestRequest, IngestStep.END,
                           1000));
    stopMonitor(monitorThread);
  }

  @Test
  public void ingestMonitorOkTest()
      throws InvalidParseOperationException, InterruptedException, IOException {
    MonitorThread monitorThread = startMonitor();

    // Start but first NotFound during ATR, then OK but ATR could not be sent
    // yet, and finally send back ATR and END
    logger.warn("\n\tSTARTUP full Scenario no error but several tentatives");
    setSendMessage(false);
    IngestRequest ingestRequest = newIngestRequest();
    assertTrue(checkIngestRequest(ingestRequestFactory, ingestRequest,
                                  IngestStep.STARTUP));
    when(mock.post()).thenReturn(Response.accepted()
                                         .header(GlobalDataRest.X_REQUEST_ID,
                                                 "FAKE_X_REQUEST_ID").header(
            GlobalDataRest.X_GLOBAL_EXECUTION_STATE, ProcessState.PAUSE).header(
            GlobalDataRest.X_GLOBAL_EXECUTION_STATUS, StatusCode.UNKNOWN)
                                         .build());
    when(mock.get()).thenReturn(
        Response.status(Status.NOT_FOUND.getStatusCode())
                .header(GlobalDataRest.X_REQUEST_ID, FAKE_X_REQUEST_ID)
                .build());
    startIngestRequest(ingestRequest);
    assertTrue(checkIngestRequest(ingestRequestFactory, ingestRequest,
                                  IngestStep.RETRY_INGEST_ID, 1000));
    setSendMessage(true);
    assertTrue(checkIngestRequest(ingestRequestFactory, ingestRequest,
                                  IngestStep.RETRY_ATR, 1000));
    logger.info("RETRY_ATR");
    setSendMessage(false);
    when(mock.get()).thenReturn(ClientMockResultHelper.getObjectStream());
    assertTrue(checkIngestRequest(ingestRequestFactory, ingestRequest,
                                  IngestStep.RETRY_ATR_FORWARD, 1000));
    setSendMessage(true);
    assertTrue(
        checkIngestRequest(ingestRequestFactory, ingestRequest, IngestStep.END,
                           1000));

    stopMonitor(monitorThread);
  }

  private boolean checkIngestRequest(IngestRequestFactory trueFactory,
                                     IngestRequest ingestRequest,
                                     IngestStep step, int timeout)
      throws InterruptedException {
    for (int i = 0; i < 10; i++) {
      if (checkIngestRequest(trueFactory, ingestRequest, step)) {
        return true;
      }
      Thread.sleep(timeout / 10);
    }
    return false;
  }

  private boolean checkIngestRequest(IngestRequestFactory trueFactory,
                                     IngestRequest ingestRequest,
                                     IngestStep step) {
    IngestRequest loaded = null;
    try {
      loaded =
          trueFactory.getSpecificIngestRequest(ingestRequest.getJsonPath());
    } catch (InvalidParseOperationException e) {
      logger.info("Not found {}", ingestRequest.getJsonPath());
      return step.equals(IngestStep.END);
    }
    if (loaded.getStep().equals(step)) {
      logger.info("Step equals: {}", step);
      return true;
    }
    logger.info("Status {} not equal to {}", step, loaded.getStep());
    return false;
  }

  /**
   * Mock implementation of Ingest External
   */
  @Path("/ingest-external/v1")
  public static class MockResource {
    private final ExpectedResults expectedResponse;

    public MockResource(ExpectedResults expectedResponse) {
      this.expectedResponse = expectedResponse;
    }

    @POST
    @Path("ingests")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public Response upload(InputStream stream) {
      Response resp = expectedResponse.post();
      return resp;
    }

    @POST
    @Path("ingests")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response uploadLocal(LocalFile localFile) {
      Response resp = expectedResponse.post();
      return resp;
    }

    @GET
    @Path("/ingests/{objectId}/{type}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadObject(@PathParam("objectId") String objectId,
                                   @PathParam("type") String type) {
      return expectedResponse.get();
    }

  }


}