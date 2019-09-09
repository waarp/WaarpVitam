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
import com.google.common.base.Charsets;
import fr.gouv.vitam.access.external.client.AccessExternalClient;
import fr.gouv.vitam.access.external.client.AccessExternalClientFactory;
import fr.gouv.vitam.access.external.client.AdminExternalClient;
import fr.gouv.vitam.access.external.client.AdminExternalClientFactory;
import fr.gouv.vitam.common.GlobalDataRest;
import fr.gouv.vitam.common.client.VitamContext;
import fr.gouv.vitam.common.error.VitamCode;
import fr.gouv.vitam.common.error.VitamCodeHelper;
import fr.gouv.vitam.common.error.VitamError;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamClientException;
import fr.gouv.vitam.common.external.client.AbstractMockClient.FakeInboundResponse;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.ProcessState;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.server.application.junit.ResteasyTestApplication;
import fr.gouv.vitam.common.serverv2.VitamServerTestRunner;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.waarp.common.logging.WaarpLogLevel;
import org.waarp.common.logging.WaarpLogger;
import org.waarp.common.logging.WaarpLoggerFactory;
import org.waarp.common.logging.WaarpSlf4JLoggerFactory;
import org.waarp.openr66.context.R66Session;
import org.waarp.vitam.CommonUtil;
import org.waarp.vitam.OperationCheck;
import org.waarp.vitam.dip.DipManager.DipManagerToWaarp;
import org.waarp.vitam.dip.DipRequest.DIPStep;
import org.waarp.vitam.dip.DipTask.JavaTask;
import org.waarp.vitam.ingest.IngestTask;

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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DipTaskTest extends ResteasyTestApplication {
  protected static final String VITAM_QUERY_DSL = "/tmp/vitamQuery.dsl";
  /**
   * Internal Logger
   */
  private static final WaarpLogger logger =
      WaarpLoggerFactory.getLogger(DipTaskTest.class);
  private static final String MOCK_INPUTSTREAM_CONTENT =
      "VITAM-Access External Client Rest Mock InputStream";
  private static final String CONTRACT = "accessContract";
  private static final String QUERY_DSQL =
      "{ \"$query\" : [ { \"$eq\" : { \"title\" : \"test\" } } ], \"$projection\" : {} }";
  private static final String RESPONSE_OK =
      "{\n" + "  \"#id\": \"idDip\",\n" + "  \"httpCode\" : 202,\n" +
      "  \"code\" : \"vitamcode\",\n" + "  \"context\": \"access\",\n" +
      "  \"state\": \"Running\",\n" +
      "  \"message\": \"The DIP is in progress\",\n" +
      "  \"description\": \"The application 'Xxxx' requested a DIP operation and this operation is in progress.\",\n" +
      "  \"start_date\": \"2014-01-10T03:06:17.396Z\"\n" + "}";
  private static final String FULL_RESPONSE_OK =
      "{\n" + "  \"httpCode\": 202,\n" + "  \"$hits\": {\n" +
      "    \"total\": 1,\n" + "    \"size\": 1,\n" + "    \"offset\": 0,\n" +
      "    \"limit\": 1000\n" + "  },\n" + "  \"$context\": {\n" +
      "    \"$roots\": [ \"id0\" ],\n" +
      "    \"$query\" : [ { \"$eq\" : { \"title\" : \"test\" } } ], \"$projection\" : {} \n" +
      "  },\n" + "  \"$results\": [\n" + RESPONSE_OK + "  ]\n" + "}\n";
  private static final JsonNode JSON_RESPONSE_OK;
  private static final String FAKE_X_REQUEST_ID =
      GUIDFactory.newRequestIdGUID(0).getId();
  private final static ExpectedResults mock = mock(ExpectedResults.class);
  private static AccessExternalClient client;
  private static AccessExternalClientFactory factory =
      AccessExternalClientFactory.getInstance();
  private static AdminExternalClientFactory adminFactory =
      mock(AdminExternalClientFactory.class);
  private static AdminExternalClient adminExternalClient =
      mock(AdminExternalClient.class);
  private static VitamServerTestRunner vitamServerTestRunner =
      new VitamServerTestRunner(DipTaskTest.class, factory);
  private static DipRequestFactory dipRequestFactory;
  private static DipManagerToWaarp dipManagerToWaarp;
  private static DipManager dipManager;

  static {
    JsonNode node;
    try {
      node = JsonHandler.getFromString(FULL_RESPONSE_OK);
    } catch (InvalidParseOperationException e) {
      node = JsonHandler.createObjectNode();
    }
    JSON_RESPONSE_OK = node;
  }

  private final int TENANT_ID = 0;

  @BeforeClass
  public static void setUpBeforeClass() throws Throwable {
    WaarpLoggerFactory
        .setDefaultFactory(new WaarpSlf4JLoggerFactory(WaarpLogLevel.WARN));
    vitamServerTestRunner.start();
    client = (AccessExternalClient) vitamServerTestRunner.getClient();
    dipRequestFactory = mock(DipRequestFactory.class);
    doCallRealMethod().when(dipRequestFactory).setBaseDir();
    doCallRealMethod().when(dipRequestFactory).getExistingDips();
    doCallRealMethod().when(dipRequestFactory)
                      .removeDipRequest(any(DipRequest.class));
    doCallRealMethod().when(dipRequestFactory)
                      .getDipFile(any(DipRequest.class));
    doCallRealMethod().when(dipRequestFactory)
                      .getErrorFile(any(DipRequest.class));
    doCallRealMethod().when(dipRequestFactory)
                      .saveDipRequest(any(DipRequest.class));
    doCallRealMethod().when(dipRequestFactory)
                      .saveNewDipRequest(any(DipRequest.class));
    doCallRealMethod().when(dipRequestFactory)
                      .getSpecificDipRequest(anyString());
    when(dipRequestFactory.getClient()).thenReturn(client);
    when(adminFactory.getClient()).thenReturn(adminExternalClient);
    OperationCheck.setRetry(1, 10);
    dipRequestFactory.setBaseDir();
    dipManager = new DipManager();
    dipManagerToWaarp = mock(DipManagerToWaarp.class);
    dipManager.dipManagerToWaarp = dipManagerToWaarp;
    setSendMessage(true);
    File file = new File(VITAM_QUERY_DSL);
    FileUtils.write(file, QUERY_DSQL, Charsets.UTF_8);
    CommonUtil.launchServers(DipRequestFactory.TMP_DIP_FACTORY);
  }

  private static void setSendMessage(boolean success)
      throws InvalidParseOperationException {
    when(dipManagerToWaarp.sendBackInformation(any(DipRequestFactory.class),
                                               any(DipRequest.class),
                                               anyString(), anyString()))
        .thenReturn(success);
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
  public void cleanDipRequest() {
    List<DipRequest> list = dipRequestFactory.getExistingDips();
    if (list != null && !list.isEmpty()) {
      for (DipRequest dipRequest : list) {
        logger.info("Clean {}", dipRequest);
        dipRequestFactory.removeDipRequest(dipRequest);
      }
    }
  }

  @Test
  public void testOptions() {
    DipTask.main(new String[] {});
    DipMonitor.main(new String[] {});
    DipTask.main(new String[] {
        "-t", "notANumber", "-f", "/tmp/test", "-a", "access", "-p", "hosta",
        "-r", "send", "-w", "/tmp/test", "-c", "certificate", "-s", "session"
    });
    DipMonitor.main(new String[] {
        "-e", "notANumber", "-s", "/tmp/test", "-w", "/tmp/test",
        "-D" + DipRequestFactory.ORG_WAARP_DIP_BASEDIR + "=" +
        DipRequestFactory.TMP_DIP_FACTORY
    });
    DipTask.main(new String[] {
        "-h"
    });
    DipMonitor.main(new String[] {
        "-h"
    });
    DipTask.main(new String[] {
        "-f", "/tmp/test", "-c", "certificate", "-s", "session"
    });
    Properties properties = new Properties();
    properties.put("tenant", "NotANumber");
    properties.put("access", "access");
    properties.put("partner", "partner");
    properties.put("rule", "rule");
    properties.put("waarp", "/tmp/test");
    try (OutputStream outputStream = new FileOutputStream(
        "/tmp/config.property")) {
      properties.store(outputStream, "Test propoerty file");
      outputStream.flush();
    } catch (IOException e) {
      e.printStackTrace();
    }
    DipTask.main(new String[] {
        "-f", "/tmp/test", "-c", "certificate", "-s", "session", "-o",
        "/tmp/config.property"
    });
    DipMonitor.main(new String[] {
        "-e", "10", "-D" + DipRequestFactory.ORG_WAARP_DIP_BASEDIR + "=" +
                    DipRequestFactory.TMP_DIP_FACTORY
    });
  }

  @Test
  @RunWithCustomExecutor
  public void givenBadInitThroughJavaTaskKO()
      throws InvalidParseOperationException {
    when(mock.post()).thenReturn(
        Response.status(Status.INTERNAL_SERVER_ERROR.getStatusCode())
                .header(GlobalDataRest.X_REQUEST_ID, FAKE_X_REQUEST_ID)
                .build());
    setSendMessage(true);

    DipTask.JavaTask javaTask = new JavaTask();
    String arg = "-t notANumber -f /tmp/test -a access " +
                 "-p hosta -r send -w /tmp/test -c certificate " +
                 "-s session";
    javaTask
        .setArgs(new R66Session(), true, false, 0, DipTask.class.getName(),
                 arg, false, false);
    javaTask.run();
    assertEquals(2, javaTask.getFinalStatus());
  }

  @Test
  @RunWithCustomExecutor
  public void exportDIP() throws Exception {
    when(mock.post()).thenReturn(getDslResponseStream());
    assertThat(client.exportDIP(
        new VitamContext(TENANT_ID).setAccessContract(CONTRACT),
        JsonHandler.getFromString(QUERY_DSQL))).isNotNull();
  }

  private static Response getDslResponseStream() {
    MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap();
    headers.add(GlobalDataRest.X_REQUEST_ID, FAKE_X_REQUEST_ID);
    return new FakeInboundResponse(Status.ACCEPTED, new ByteArrayInputStream(
        FULL_RESPONSE_OK.getBytes()), MediaType.APPLICATION_JSON_TYPE, headers);
  }

  private static RequestResponse<ItemStatus> returnCheckOk(Status status) {
    ItemStatus itemStatus = new ItemStatus().setGlobalState(
        status.equals(Status.OK)? ProcessState.COMPLETED : ProcessState.RUNNING)
                                            .increment(status.equals(Status.OK)?
                                                           StatusCode.OK :
                                                           StatusCode.STARTED);
    return new RequestResponseOK<ItemStatus>().addResult(itemStatus)
                                              .setHttpCode(
                                                  status.getStatusCode());
  }

  private static RequestResponse<ItemStatus> returnCheckKo(VitamCode code) {
    return VitamCodeHelper.toVitamError(code, "INTERNAL_SERVER_ERROR");
  }

  @Test
  @RunWithCustomExecutor
  public void givenCorrectSelectOK() throws Exception {
    when(mock.post()).thenReturn(getDslResponseStream());
    setSendMessage(true);
    DipTask task =
        new DipTask(VITAM_QUERY_DSL, TENANT_ID, "applicationSessionId",
                    "personalCertificate", CONTRACT, "hosta", "send",
                    dipRequestFactory, dipManager);
    assertEquals(0, task.invoke());
    List<DipRequest> list = dipRequestFactory.getExistingDips();
    if (list != null && !list.isEmpty()) {
      for (DipRequest dipRequest : list) {
        logger.warn("XXXXXXXXXXX {}", dipRequest);
      }
    }
  }

  @Test
  @RunWithCustomExecutor
  public void givenSelectNotFoundKO() throws InvalidParseOperationException {
    when(mock.post()).thenReturn(Response.status(Status.NOT_FOUND).build());
    setSendMessage(true);

    DipTask task =
        new DipTask(VITAM_QUERY_DSL, TENANT_ID, "applicationSessionId", null,
                    CONTRACT, "hosta", "send", dipRequestFactory, dipManager);
    assertEquals(2, task.invoke());
  }

  @Test
  @RunWithCustomExecutor
  public void givenSelectUnsupportedMediaType()
      throws InvalidParseOperationException {
    when(mock.post()).thenReturn(Response.status(Status.UNSUPPORTED_MEDIA_TYPE)
                                         .header(GlobalDataRest.X_REQUEST_ID,
                                                 FAKE_X_REQUEST_ID).build());
    DipTask task =
        new DipTask(VITAM_QUERY_DSL, TENANT_ID, "applicationSessionId", null,
                    CONTRACT, "hosta", "send", dipRequestFactory, dipManager);
    assertEquals(2, task.invoke());
  }

  @Test
  @RunWithCustomExecutor
  public void givenStreamWhenDownloadObjectOK()
      throws InvalidParseOperationException, IOException, VitamClientException {
    doReturn(returnCheckOk(Status.OK)).when(adminExternalClient)
                                      .getOperationProcessStatus(
                                          any(VitamContext.class), anyString());
    when(mock.get()).thenReturn(getObjectStream());
    setSendMessage(true);
    File file = new File(DipRequestFactory.TMP_DIP_FACTORY + "/test");
    FileUtils.write(file, "testContent");
    when(dipRequestFactory.getDipFile(any(DipRequest.class))).thenReturn(file);
    DipRequest dipRequest = newDipRequest();
    dipRequest.setRequestId(FAKE_X_REQUEST_ID)
              .setStatus(DIPStep.RETRY_SELECT.getStatusMonitor());
    dipRequest.setStep(DIPStep.RETRY_DIP, 0, dipRequestFactory);
    assertEquals(true, dipManager
        .getDip(dipRequestFactory, dipRequest, client, adminExternalClient,
                dipRequest.getVitamContext()));
  }

  private static Response getObjectStream() {
    MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap();
    headers.add("Content-Disposition", "filename=\"test.zip\"");
    headers.add(GlobalDataRest.X_REQUEST_ID, FAKE_X_REQUEST_ID);
    return new FakeInboundResponse(Status.ACCEPTED, new ByteArrayInputStream(
        MOCK_INPUTSTREAM_CONTENT.getBytes()),
                                   MediaType.APPLICATION_OCTET_STREAM_TYPE,
                                   headers);
  }

  private DipRequest newDipRequest() throws InvalidParseOperationException {
    return new DipRequest(VITAM_QUERY_DSL, TENANT_ID, "applicationSessionId",
                          null, CONTRACT, "hosta", "send", dipRequestFactory);
  }

  @Test
  @RunWithCustomExecutor
  public void givenErrorWhenDownloadObjectKO()
      throws InvalidParseOperationException, VitamClientException {
    doReturn(returnCheckOk(Status.OK)).when(adminExternalClient)
                                      .getOperationProcessStatus(
                                          any(VitamContext.class), anyString());
    when(mock.get())
        .thenReturn(getErrorObjectStream(Status.INTERNAL_SERVER_ERROR));

    DipRequest dipRequest = newDipRequest();
    dipRequest.setRequestId(FAKE_X_REQUEST_ID)
              .setStatus(DIPStep.RETRY_SELECT.getStatusMonitor());
    dipRequest.setStep(DIPStep.RETRY_DIP, 0, dipRequestFactory);
    assertEquals(false, dipManager
        .getDip(dipRequestFactory, dipRequest, client, adminExternalClient,
                dipRequest.getVitamContext()));
  }

  @Test
  @RunWithCustomExecutor
  public void givenErrorWhenCheckKO()
      throws InvalidParseOperationException, VitamClientException {
    doReturn(returnCheckKo(VitamCode.INGEST_EXTERNAL_INTERNAL_SERVER_ERROR))
        .when(adminExternalClient)
        .getOperationProcessStatus(any(VitamContext.class), anyString());
    // Should not be called
    when(mock.get()).thenReturn(getObjectStream());

    DipRequest dipRequest = newDipRequest();
    dipRequest.setRequestId(FAKE_X_REQUEST_ID)
              .setStatus(DIPStep.RETRY_SELECT.getStatusMonitor());
    dipRequest.setStep(DIPStep.RETRY_DIP, 0, dipRequestFactory);
    OperationCheck.main(new String[] {
        DipRequestFactory.TMP_DIP_FACTORY + "/" + dipRequest.getJsonPath()
    });
    assertFalse(OperationCheck.getResult());
    assertEquals(false, dipManager
        .getDip(dipRequestFactory, dipRequest, client, adminExternalClient,
                dipRequest.getVitamContext()));
  }

  private static Response getErrorObjectStream(Status status) {
    String aMessage =
        status.getReasonPhrase() != null? status.getReasonPhrase() :
            status.name();
    VitamError error =
        new VitamError(status.name()).setHttpCode(status.getStatusCode())
                                     .setContext("ACCESS_EXTERNAL")
                                     .setState("code_vitam")
                                     .setMessage(status.getReasonPhrase())
                                     .setDescription(aMessage);
    MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap();
    headers.add(GlobalDataRest.X_REQUEST_ID, FAKE_X_REQUEST_ID);
    return new FakeInboundResponse(status, new ByteArrayInputStream(
        error.toString().getBytes()), MediaType.APPLICATION_OCTET_STREAM_TYPE,
                                   headers);
  }

  @Test
  @RunWithCustomExecutor
  public void givenErrorWhenDownloadObjectUnavailable()
      throws InvalidParseOperationException, VitamClientException {
    doReturn(returnCheckOk(Status.OK)).when(adminExternalClient)
                                      .getOperationProcessStatus(
                                          any(VitamContext.class), anyString());
    when(mock.get())
        .thenReturn(getErrorObjectStream(Status.SERVICE_UNAVAILABLE));

    DipRequest dipRequest = newDipRequest();
    dipRequest.setRequestId(FAKE_X_REQUEST_ID)
              .setStatus(DIPStep.RETRY_SELECT.getStatusMonitor());
    dipRequest.setStep(DIPStep.RETRY_DIP, 0, dipRequestFactory);
    assertEquals(false, dipManager
        .getDip(dipRequestFactory, dipRequest, client, adminExternalClient,
                dipRequest.getVitamContext()));
  }

  @Test
  @RunWithCustomExecutor
  public void givenErrorWhenDownloadObjectNotFound()
      throws InvalidParseOperationException, VitamClientException {
    doReturn(returnCheckOk(Status.OK)).when(adminExternalClient)
                                      .getOperationProcessStatus(
                                          any(VitamContext.class), anyString());
    when(mock.get()).thenReturn(getErrorObjectStream(Status.NOT_FOUND));

    DipRequest dipRequest = newDipRequest();
    dipRequest.setRequestId(FAKE_X_REQUEST_ID)
              .setStatus(DIPStep.RETRY_SELECT.getStatusMonitor());
    dipRequest.setStep(DIPStep.RETRY_DIP, 0, dipRequestFactory);
    assertEquals(false, dipManager
        .getDip(dipRequestFactory, dipRequest, client, adminExternalClient,
                dipRequest.getVitamContext()));
  }

  @Test
  @RunWithCustomExecutor
  public void dipMonitorEarlyErrorTest()
      throws InvalidParseOperationException, InterruptedException, IOException {
    MonitorThread monitorThread = startMonitor();

    // Start but early error, then send back Id (not any indeed) and END
    logger.warn("\n\tSTARTUP Scenario early error");
    DipRequest dipRequest = newDipRequest();
    assertTrue(checkDipRequest(dipRequestFactory, dipRequest, DIPStep.STARTUP));
    when(mock.post())
        .thenReturn(getErrorObjectStream(Status.INTERNAL_SERVER_ERROR));
    startDipRequest(dipRequest);
    assertTrue(
        checkDipRequest(dipRequestFactory, dipRequest, DIPStep.END, 1000));
    stopMonitor(monitorThread);
  }

  private MonitorThread startMonitor()
      throws InvalidParseOperationException, InterruptedException {
    logger.warn("START MONITOR\n");
    File stopFile = new File("/tmp/stopMonitor.txt");
    stopFile.delete();

    MonitorThread monitorThread = new MonitorThread();
    monitorThread.monitor =
        new DipMonitor(100, stopFile, dipRequestFactory, adminFactory,
                       dipManager);
    setSendMessage(true);
    monitorThread.setDaemon(true);
    monitorThread.start();
    Thread.sleep(100);
    assertTrue(monitorThread.running.get());
    return monitorThread;
  }

  private boolean checkDipRequest(DipRequestFactory trueFactory,
                                  DipRequest dipRequest, DIPStep step) {
    DipRequest loaded = null;
    try {
      loaded = trueFactory.getSpecificDipRequest(dipRequest.getJsonPath());
    } catch (InvalidParseOperationException e) {
      logger.info("Not found {}", dipRequest.getJsonPath());
      return step.equals(DIPStep.END);
    }
    if (loaded.getStep().equals(step)) {
      logger.info("Step equals: {}", step);
      return true;
    }
    logger.info("Status {} not equal to {}", step, loaded.getStep());
    return false;
  }

  private void startDipRequest(DipRequest dipRequest)
      throws InvalidParseOperationException {
    logger.info("RETRY_INGEST");
    dipRequest
        .setStep(DIPStep.RETRY_SELECT, DIPStep.RETRY_SELECT.getStatusMonitor(),
                 dipRequestFactory);
  }

  private boolean checkDipRequest(DipRequestFactory trueFactory,
                                  DipRequest dipRequest, DIPStep step,
                                  int timeout) throws InterruptedException {
    for (int i = 0; i < 10; i++) {
      if (checkDipRequest(trueFactory, dipRequest, step)) {
        return true;
      }
      Thread.sleep(timeout / 10);
    }
    return false;
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
  @RunWithCustomExecutor
  public void dipMonitorErrorDipTest()
      throws InvalidParseOperationException, InterruptedException, IOException,
             VitamClientException {
    MonitorThread monitorThread = startMonitor();

    // Start but error during DIP, then send back Id and END
    logger.warn("\n\tSTARTUP Scenario error during DIP");
    DipRequest dipRequest = newDipRequest();
    assertTrue(checkDipRequest(dipRequestFactory, dipRequest, DIPStep.STARTUP));
    when(mock.post()).thenReturn(getDslResponseStream());
    doReturn(returnCheckOk(Status.OK)).when(adminExternalClient)
                                      .getOperationProcessStatus(
                                          any(VitamContext.class), anyString());
    when(mock.get())
        .thenReturn(getErrorObjectStream(Status.INTERNAL_SERVER_ERROR));
    startDipRequest(dipRequest);
    assertTrue(
        checkDipRequest(dipRequestFactory, dipRequest, DIPStep.END, 1000));
    stopMonitor(monitorThread);
  }

  @Test
  @RunWithCustomExecutor
  public void dipMonitorOkTest()
      throws InvalidParseOperationException, InterruptedException, IOException,
             VitamClientException {
    MonitorThread monitorThread = startMonitor();

    // Start but first NotFound during DIP, then OK but DIP could not be sent
    // yet, and finally send back DIP and END
    logger.warn("\n\tSTARTUP full Scenario no error but several tentatives");
    setSendMessage(false);
    DipRequest dipRequest = newDipRequest();
    assertTrue(checkDipRequest(dipRequestFactory, dipRequest, DIPStep.STARTUP));
    when(mock.post()).thenReturn(getDslResponseStream());
    doReturn(returnCheckOk(Status.OK)).when(adminExternalClient)
                                      .getOperationProcessStatus(
                                          any(VitamContext.class), anyString());
    when(mock.get()).thenReturn(getErrorObjectStream(Status.NOT_FOUND));
    startDipRequest(dipRequest);
    assertTrue(checkDipRequest(dipRequestFactory, dipRequest, DIPStep.RETRY_DIP,
                               1000));
    when(mock.get()).thenReturn(getObjectStream());
    assertTrue(checkDipRequest(dipRequestFactory, dipRequest,
                               DIPStep.RETRY_DIP_FORWARD, 1000));
    setSendMessage(true);
    assertTrue(
        checkDipRequest(dipRequestFactory, dipRequest, DIPStep.END, 1000));

    stopMonitor(monitorThread);
  }

  @Test
  @RunWithCustomExecutor
  public void dipMonitorCheckOkTest()
      throws InvalidParseOperationException, InterruptedException, IOException,
             VitamClientException {
    MonitorThread monitorThread = startMonitor();

    // Start but first NotFound during DIP, then OK but DIP could not be sent
    // yet, and finally send back DIP and END
    logger.warn("\n\tSTARTUP full Scenario no error but several tentatives");
    setSendMessage(false);
    DipRequest dipRequest = newDipRequest();
    assertTrue(checkDipRequest(dipRequestFactory, dipRequest, DIPStep.STARTUP));
    when(mock.post()).thenReturn(getDslResponseStream());
    doReturn(returnCheckOk(Status.ACCEPTED)).when(adminExternalClient)
                                            .getOperationProcessStatus(
                                                any(VitamContext.class),
                                                anyString());
    startDipRequest(dipRequest);
    assertTrue(checkDipRequest(dipRequestFactory, dipRequest, DIPStep.RETRY_DIP,
                               1000));
    when(mock.get()).thenReturn(getObjectStream());
    doReturn(returnCheckOk(Status.OK)).when(adminExternalClient)
                                      .getOperationProcessStatus(
                                          any(VitamContext.class), anyString());
    assertTrue(checkDipRequest(dipRequestFactory, dipRequest,
                               DIPStep.RETRY_DIP_FORWARD, 1000));
    setSendMessage(true);
    assertTrue(
        checkDipRequest(dipRequestFactory, dipRequest, DIPStep.END, 1000));

    stopMonitor(monitorThread);
  }

  static class MonitorThread extends Thread {
    DipMonitor monitor;
    AtomicBoolean running = new AtomicBoolean(false);

    @Override
    public void run() {
      running.set(true);
      monitor.invoke();
      running.set(false);
    }
  }

  /**
   * Mock implementation of Dip External
   */
  @Path("/access-external/v1")
  public static class MockResource {
    private final ExpectedResults expectedResponse;

    public MockResource(ExpectedResults expectedResponse) {
      this.expectedResponse = expectedResponse;
    }

    @POST
    @Path("/dipexport")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response exportDIP(JsonNode queryJson) {
      return expectedResponse.post();
    }

    @GET
    @Path("/dipexport/{id}/dip")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response findDIPByID(@PathParam("id") String id) {
      return expectedResponse.get();
    }

  }


}