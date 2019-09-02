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

import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.gouv.vitam.common.CharsetUtils;
import fr.gouv.vitam.common.GlobalDataRest;
import fr.gouv.vitam.common.client.VitamContext;
import fr.gouv.vitam.common.error.VitamCode;
import fr.gouv.vitam.common.error.VitamCodeHelper;
import fr.gouv.vitam.common.error.VitamError;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamClientException;
import fr.gouv.vitam.common.external.client.AbstractMockClient;
import fr.gouv.vitam.common.external.client.ClientMockResultHelper;
import fr.gouv.vitam.common.external.client.IngestCollection;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.model.LocalFile;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.server.application.junit.ResteasyTestApplication;
import fr.gouv.vitam.common.serverv2.VitamServerTestRunner;
import fr.gouv.vitam.ingest.external.client.IngestExternalClient;
import fr.gouv.vitam.ingest.external.client.IngestExternalClientFactory;
import org.apache.commons.io.IOUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

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
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class IngestTaskTest extends ResteasyTestApplication {

  protected static final String HOSTNAME = "localhost";
  protected static final String PATH = "/ingest-external/v1";
  private static final String MOCK_INPUTSTREAM_CONTENT =
      "VITAM-Ingest External Client Rest Mock InputStream";
  private static final String FAKE_X_REQUEST_ID =
      GUIDFactory.newRequestIdGUID(0).getId();
  private static final String CONTEXT_ID = "defaultContext";
  private static final String EXECUTION_MODE = "defaultContext";
  private static final String ID = "id1";
  private final static ExpectedResults mock = mock(ExpectedResults.class);
  protected static IngestExternalClient client;
  static IngestExternalClientFactory factory =
      IngestExternalClientFactory.getInstance();
  public static VitamServerTestRunner vitamServerTestRunner =
      new VitamServerTestRunner(IngestTaskTest.class, factory);
  final int TENANT_ID = 0;

  @BeforeClass
  public static void setUpBeforeClass() throws Throwable {
    vitamServerTestRunner.start();
    client = (IngestExternalClient) vitamServerTestRunner.getClient();
  }

  @AfterClass
  public static void tearDownAfterClass() throws Throwable {
    vitamServerTestRunner.runAfter();
  }

  @Override
  public Set<Object> getResources() {
    HashSet<Object> set = new HashSet<Object>();
    set.add(new MockResource(mock));
    return set;

  }

  @Test
  public void givenErrorWhenUploadThenReturnBadRequestErrorWithBody()
      throws Exception {
    final MultivaluedHashMap<String, Object> headers =
        new MultivaluedHashMap<String, Object>();
    headers.add(GlobalDataRest.X_REQUEST_ID, FAKE_X_REQUEST_ID);

    ObjectNode objectNode = JsonHandler.createObjectNode();
    objectNode.put(GlobalDataRest.X_REQUEST_ID, FAKE_X_REQUEST_ID);

    when(mock.post()).thenReturn(Response.accepted()
                                         .header(GlobalDataRest.X_REQUEST_ID,
                                                 FAKE_X_REQUEST_ID).build());

    final InputStream streamToUpload =
        IOUtils.toInputStream(MOCK_INPUTSTREAM_CONTENT, CharsetUtils.UTF_8);

    RequestResponse<Void> resp = client
        .ingest(new VitamContext(TENANT_ID), streamToUpload, CONTEXT_ID,
                EXECUTION_MODE);
    assertEquals(resp.getHttpCode(), Status.ACCEPTED.getStatusCode());
  }

  @Test
  public void givenNotFoundWhenDownloadObjectThenReturn404()
      throws VitamClientException, InvalidParseOperationException, IOException {
    VitamError error = VitamCodeHelper
        .toVitamError(VitamCode.INGEST_EXTERNAL_NOT_FOUND, "NOT FOUND");
    AbstractMockClient.FakeInboundResponse fakeResponse =
        new AbstractMockClient.FakeInboundResponse(Status.NOT_FOUND, JsonHandler
            .writeToInpustream(error), MediaType.APPLICATION_OCTET_STREAM_TYPE,
                                                   new MultivaluedHashMap<String, Object>());
    when(mock.get()).thenReturn(fakeResponse);
    InputStream input = client
        .downloadObjectAsync(new VitamContext(TENANT_ID), "1",
                             IngestCollection.MANIFESTS)
        .readEntity(InputStream.class);
    VitamError response =
        JsonHandler.getFromInputStream(input, VitamError.class);
    assertEquals(Status.NOT_FOUND.getStatusCode(), response.getHttpCode());
  }

  @Test
  public void givenUploadLocalFileThenReturnOK() throws Exception {
    final MultivaluedHashMap<String, Object> headers =
        new MultivaluedHashMap<String, Object>();
    headers.add(GlobalDataRest.X_REQUEST_ID, FAKE_X_REQUEST_ID);

    ObjectNode objectNode = JsonHandler.createObjectNode();
    objectNode.put(GlobalDataRest.X_REQUEST_ID, FAKE_X_REQUEST_ID);

    when(mock.post()).thenReturn(Response.accepted()
                                         .header(GlobalDataRest.X_REQUEST_ID,
                                                 FAKE_X_REQUEST_ID).build());

    RequestResponse<Void> resp = client
        .ingestLocal(new VitamContext(TENANT_ID), new LocalFile("path"),
                     CONTEXT_ID, EXECUTION_MODE);
    assertEquals(resp.getHttpCode(), Status.ACCEPTED.getStatusCode());
  }

  @Test
  public void givenUploadLocalFileThenReturnKO() throws Exception {
    final MultivaluedHashMap<String, Object> headers =
        new MultivaluedHashMap<String, Object>();
    headers.add(GlobalDataRest.X_REQUEST_ID, FAKE_X_REQUEST_ID);

    ObjectNode objectNode = JsonHandler.createObjectNode();
    objectNode.put(GlobalDataRest.X_REQUEST_ID, FAKE_X_REQUEST_ID);

    when(mock.post()).thenReturn(
        Response.status(Status.INTERNAL_SERVER_ERROR.getStatusCode())
                .header(GlobalDataRest.X_REQUEST_ID, FAKE_X_REQUEST_ID)
                .build());

    RequestResponse<Void> resp = client
        .ingestLocal(new VitamContext(TENANT_ID), new LocalFile("path"),
                     CONTEXT_ID, EXECUTION_MODE);
    assertEquals(resp.getHttpCode(),
                 Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void givenInputstreamWhenDownloadObjectThenReturnOK()
      throws VitamClientException {

    when(mock.get()).thenReturn(ClientMockResultHelper.getObjectStream());

    Response response = client
        .downloadObjectAsync(new VitamContext(TENANT_ID), "1",
                             IngestCollection.MANIFESTS);
    final InputStream fakeUploadResponseInputStream =
        response.readEntity(InputStream.class);
    assertNotNull(fakeUploadResponseInputStream);

    try {
      assertTrue(IOUtils.contentEquals(fakeUploadResponseInputStream, IOUtils
          .toInputStream("test", CharsetUtils.UTF_8)));
    } catch (final IOException e) {
      e.printStackTrace();
      fail();
    }
    response.close();
  }

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