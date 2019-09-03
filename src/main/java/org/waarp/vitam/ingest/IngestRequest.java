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

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import fr.gouv.vitam.common.GlobalDataRest;
import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.StringUtils;
import fr.gouv.vitam.common.client.VitamContext;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.model.LocalFile;
import fr.gouv.vitam.common.model.ProcessState;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.StatusCode;
import org.waarp.common.exception.IllegalFiniteStateException;
import org.waarp.common.logging.WaarpLogger;
import org.waarp.common.logging.WaarpLoggerFactory;
import org.waarp.common.state.MachineState;
import org.waarp.common.state.Transition;

import java.io.File;
import java.util.EnumSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IngestRequest is the unitary entry for Ingest operations made by Waarp to
 * Vitam
 */
public class IngestRequest {
  /**
   * Internal Logger
   */
  private static final WaarpLogger logger =
      WaarpLoggerFactory.getLogger(IngestRequest.class);
  /**
   * Only kind of action supported by Vitam and Waarp
   */
  static final String RESUME = "RESUME";

  /**
   * Context accepted by Vitam
   */
  enum CONTEXT {
    /**
     * Sip ingest
     */
    DEFAULT_WORKFLOW,
    /**
     * Tree
     */
    HOLDING_SCHEME,
    /**
     * Plan
     */
    FILING_SCHEME;

    public static boolean checkCorrectness(String arg) {
      try {
        CONTEXT.valueOf(arg);
        return true;
      } catch (IllegalArgumentException ignore) {
        return false;
      }
    }
  }

  /**
   * Different steps of Ingest from Waarp point of view
   */
  enum IngestStep {
    /**
     * IngestRequest not started yet
     */
    STARTUP(-1),
    /**
     * IngestRequest INGEST to retry
     */
    RETRY_INGEST(-2),
    /**
     * IngestRequest INGEST Id to retry
     */
    RETRY_INGEST_ID(-3),
    /**
     * IngestRequest ATR to get
     */
    RETRY_ATR(-4),
    /**
     * IngestRequest ATR to forward
     */
    RETRY_ATR_FORWARD(-5),
    /**
     * IngestRequest Error
     */
    ERROR(-7),
    /**
     * Final End step
     */
    END(-10);

    private final int statusMonitor;

    IngestStep(int status) {
      this.statusMonitor = status;
    }

    int getStatusMonitor() {
      return statusMonitor;
    }

    private enum IngestTransition {
      T_STARTUP(STARTUP, EnumSet.of(RETRY_INGEST, ERROR)),
      T_RETRY_INGEST(RETRY_INGEST, EnumSet.of(RETRY_INGEST_ID, ERROR)),
      T_RETRY_INGEST_ID(RETRY_INGEST_ID, EnumSet.of(RETRY_ATR, ERROR, END)),
      T_RETRY_ATR(RETRY_ATR, EnumSet.of(RETRY_ATR_FORWARD, ERROR)),
      T_RETRY_ATR_FORWARD(RETRY_ATR_FORWARD, EnumSet.of(ERROR, END)),
      T_ERROR(ERROR, EnumSet.of(ERROR, END)), T_END(END, EnumSet.of(END));

      private final Transition<IngestStep> elt;

      IngestTransition(IngestStep state, EnumSet<IngestStep> set) {
        elt = new Transition<>(state, set);
      }

    }

    private static final ConcurrentHashMap<IngestStep, EnumSet<IngestStep>>
        stateMap = new ConcurrentHashMap<>();

    /**
     * This method should be called once at startup to initialize the Finite
     * States association.
     */
    private static void initR66FiniteStates() {
      for (final IngestTransition trans : IngestTransition.values()) {
        stateMap.put(trans.elt.getState(), trans.elt.getSet());
      }
    }

    static {
      initR66FiniteStates();
    }

    /**
     * @return a new Session MachineState for OpenR66
     */
    private static MachineState<IngestStep> newSessionMachineState() {
      return new MachineState<>(STARTUP, stateMap);
    }

    /**
     * @param machine the Session MachineState to release
     */
    static void endSessionMachineSate(MachineState<IngestStep> machine) {
      if (machine != null) {
        machine.release();
      }
    }

    static IngestStep getFromInt(int status) {
      switch (status) {
        case -1:
          return STARTUP;
        case -2:
          return RETRY_INGEST;
        case -3:
          return RETRY_INGEST_ID;
        case -4:
          return RETRY_ATR;
        case -5:
          return RETRY_ATR_FORWARD;
        case -10:
          return END;
        case -7:
        default:
          return ERROR;
      }
    }
  }

  private static final String CHECK_MESSAGE = "Check within ";

  @JsonProperty("path")
  private String path;
  @JsonProperty("tenantId")
  private int tenantId;
  @JsonProperty("applicationSessionId")
  private String applicationSessionId;
  @JsonProperty("personalCertificate")
  private String personalCertificate;
  @JsonProperty("accessContract")
  private String accessContract;
  /*
   contextId – a type of ingest among "DEFAULT_WORKFLOW" (Sip ingest),
   "HOLDING_SCHEME" (tree) "FILING_SCHEME" (plan)
   */
  @JsonProperty("contextId")
  private String contextId;
  @JsonProperty("action")
  private String action = RESUME;
  @JsonProperty("requestId")
  private String requestId;
  @JsonProperty("globalExecutionState")
  private String globalExecutionState;
  @JsonProperty("globalExecutionStatus")
  private String globalExecutionStatus;
  @JsonProperty("lastTryTime")
  private long lastTryTime;
  @JsonProperty("status")
  private int status;
  @JsonIgnore
  MachineState<IngestStep> step = IngestStep.newSessionMachineState();
  @JsonProperty("jsonPath")
  private String jsonPath;
  @JsonProperty("waarpPartner")
  private String waarpPartner;
  @JsonProperty("waarpRule")
  private String waarpRule;
  @JsonProperty("waarpId")
  private long waarpId;
  @JsonProperty("checkAtr")
  private boolean checkAtr;

  public IngestRequest() {
    // Empty constructor for Json
  }

  /**
   * Standard constructor
   *
   * @param path
   * @param tenantId
   * @param applicationSessionId
   * @param personalCertificate
   * @param accessContract
   * @param contextId
   * @param action
   * @param waarpPartner
   * @param waarpRule
   * @param checkAtr
   * @param factory
   *
   * @throws InvalidParseOperationException
   */
  public IngestRequest(final String path, final int tenantId,
                       final String applicationSessionId,
                       final String personalCertificate,
                       final String accessContract, final String contextId,
                       final String action, final String waarpPartner,
                       final String waarpRule, final boolean checkAtr,
                       final IngestRequestFactory factory)
      throws InvalidParseOperationException {
    try {
      ParametersChecker
          .checkParameterDefault(getCheckMessage(), path, accessContract,
                                 contextId, action, waarpPartner, waarpRule);
      StringUtils.checkSanityString(path, applicationSessionId != null?
                                        applicationSessionId : "", accessContract, contextId, action,
                                    personalCertificate != null?
                                        personalCertificate : "", waarpPartner,
                                    waarpRule);
    } catch (InvalidParseOperationException | IllegalArgumentException e) {
      logger.error(e);
      throw new InvalidParseOperationException(e);
    }
    this.path = path;
    this.tenantId = tenantId;
    this.applicationSessionId = applicationSessionId;
    this.personalCertificate = personalCertificate;
    this.accessContract = accessContract;
    this.contextId = contextId;
    this.action = action;
    this.waarpPartner = waarpPartner;
    this.waarpRule = waarpRule;
    this.checkAtr = checkAtr;
    this.status = this.step.getCurrent().getStatusMonitor();
    try {
      factory.saveNewIngestRequest(this);
    } catch (InvalidParseOperationException e) {
      logger.error("Will not be able to save: {}", this, e);
      throw e;
    }
  }

  @Override
  public String toString() {
    return "Ingest = Step: " + (step != null? step.getCurrent() : "noStep") +
           " " + JsonHandler.unprettyPrint(this);
  }

  @JsonGetter("path")
  public String getPath() {
    return path;
  }

  @JsonSetter("path")
  public IngestRequest setPath(final String path) {
    try {
      ParametersChecker.checkParameterDefault(getCheckMessage(), path);
      StringUtils.checkSanityString(path);
    } catch (InvalidParseOperationException | IllegalArgumentException e) {
      logger.error(e);
      throw new IllegalArgumentException(e.getMessage(), e);
    }
    this.path = path;
    return this;
  }

  @JsonGetter("tenantId")
  public int getTenantId() {
    return tenantId;
  }

  @JsonSetter("tenantId")
  public IngestRequest setTenantId(final int tenantId) {
    if (tenantId < 0) {
      logger.error("TenantId is negative");
      throw new IllegalArgumentException("TenantId is negative");
    }
    this.tenantId = tenantId;
    return this;
  }

  @JsonGetter("applicationSessionId")
  public String getApplicationSessionId() {
    return applicationSessionId;
  }

  @JsonSetter("applicationSessionId")
  public IngestRequest setApplicationSessionId(
      final String applicationSessionId) {
    try {
      ParametersChecker
          .checkParameterDefault(getCheckMessage(), applicationSessionId);
      StringUtils.checkSanityString(applicationSessionId);
    } catch (InvalidParseOperationException | IllegalArgumentException e) {
      logger.error(e);
      throw new IllegalArgumentException(e.getMessage(), e);
    }
    this.applicationSessionId = applicationSessionId;
    return this;
  }

  @JsonGetter("personalCertificate")
  public String getPersonalCertificate() {
    return personalCertificate;
  }

  @JsonSetter("personalCertificate")
  public IngestRequest setPersonalCertificate(
      final String personalCertificate) {
    try {
      ParametersChecker
          .checkParameterDefault(getCheckMessage(), personalCertificate);
      StringUtils.checkSanityString(personalCertificate);
    } catch (InvalidParseOperationException | IllegalArgumentException e) {
      logger.error(e);
      throw new IllegalArgumentException(e.getMessage(), e);
    }
    this.personalCertificate = personalCertificate;
    return this;
  }

  @JsonGetter("accessContract")
  public String getAccessContract() {
    return accessContract;
  }

  @JsonSetter("accessContract")
  public IngestRequest setAccessContract(final String accessContract) {
    try {
      ParametersChecker
          .checkParameterDefault(getCheckMessage(), accessContract);
      StringUtils.checkSanityString(accessContract);
    } catch (InvalidParseOperationException | IllegalArgumentException e) {
      logger.error(e);
      throw new IllegalArgumentException(e.getMessage(), e);
    }
    this.accessContract = accessContract;
    return this;
  }

  @JsonGetter("contextId")
  public String getContextId() {
    return contextId;
  }

  /**
   * @param contextId a type of ingest among "DEFAULT_WORKFLOW" (Sip ingest),
   *     "HOLDING_SCHEME" (tree) "FILING_SCHEME" (plan)
   *
   * @return this
   */
  @JsonSetter("contextId")
  public IngestRequest setContextId(final String contextId) {
    try {
      ParametersChecker.checkParameterDefault(getCheckMessage(), contextId);
      StringUtils.checkSanityString(contextId);
    } catch (InvalidParseOperationException | IllegalArgumentException e) {
      logger.error(e);
      throw new IllegalArgumentException(e.getMessage(), e);
    }
    this.contextId = contextId;
    return this;
  }

  @JsonGetter("action")
  public String getAction() {
    return action;
  }

  /**
   * @param action shall be "RESUME" only
   *
   * @return this
   */
  @JsonSetter("action")
  public IngestRequest setAction(final String action) {
    try {
      ParametersChecker.checkParameterDefault(getCheckMessage(), action);
      StringUtils.checkSanityString(action);
    } catch (InvalidParseOperationException | IllegalArgumentException e) {
      logger.error(e);
      throw new IllegalArgumentException(e.getMessage(), e);
    }
    this.action = action;
    return this;
  }

  @JsonGetter("waarpPartner")
  public String getWaarpPartner() {
    return waarpPartner;
  }

  @JsonSetter("waarpPartner")
  public IngestRequest setWaarpPartner(final String waarpPartner) {
    try {
      ParametersChecker.checkParameterDefault(getCheckMessage(), waarpPartner);
      StringUtils.checkSanityString(waarpPartner);
    } catch (InvalidParseOperationException | IllegalArgumentException e) {
      logger.error(e);
      throw new IllegalArgumentException(e.getMessage(), e);
    }
    this.waarpPartner = waarpPartner;
    return this;
  }

  @JsonGetter("waarpRule")
  public String getWaarpRule() {
    return waarpRule;
  }

  @JsonSetter("waarpRule")
  public IngestRequest setWaarpRule(final String waarpRule) {
    try {
      ParametersChecker.checkParameterDefault(getCheckMessage(), waarpRule);
      StringUtils.checkSanityString(waarpRule);
    } catch (InvalidParseOperationException | IllegalArgumentException e) {
      logger.error(e);
      throw new IllegalArgumentException(e.getMessage(), e);
    }
    this.waarpRule = waarpRule;
    return this;
  }

  @JsonGetter("waarpId")
  public long getWaarpId() {
    return waarpId;
  }

  @JsonSetter("waarpId")
  public IngestRequest setWaarpId(final long waarpId) {
    this.waarpId = waarpId;
    return this;
  }

  @JsonGetter("requestId")
  public String getRequestId() {
    return requestId;
  }

  @JsonSetter("requestId")
  public IngestRequest setRequestId(final String requestId) {
    try {
      ParametersChecker.checkParameterDefault(getCheckMessage(), requestId);
      StringUtils.checkSanityString(requestId);
    } catch (InvalidParseOperationException | IllegalArgumentException e) {
      logger.error(e);
      throw new IllegalArgumentException(e.getMessage(), e);
    }
    this.requestId = requestId;
    return this;
  }

  @JsonGetter("globalExecutionState")
  public String getGlobalExecutionState() {
    return globalExecutionState;
  }

  @JsonSetter("globalExecutionState")
  public IngestRequest setGlobalExecutionState(
      final String globalExecutionState) {
    try {
      ParametersChecker
          .checkParameterDefault(getCheckMessage(), globalExecutionState);
      StringUtils.checkSanityString(globalExecutionState);
    } catch (InvalidParseOperationException | IllegalArgumentException e) {
      logger.error(e);
      throw new IllegalArgumentException(e.getMessage(), e);
    }
    this.globalExecutionState = globalExecutionState;
    return this;
  }

  @JsonGetter("globalExecutionStatus")
  public String getGlobalExecutionStatus() {
    return globalExecutionStatus;
  }

  @JsonSetter("globalExecutionStatus")
  public IngestRequest setGlobalExecutionStatus(
      final String globalExecutionStatus) {
    try {
      ParametersChecker
          .checkParameterDefault(getCheckMessage(), globalExecutionStatus);
      StringUtils.checkSanityString(globalExecutionStatus);
    } catch (InvalidParseOperationException | IllegalArgumentException e) {
      logger.error(e);
      throw new IllegalArgumentException(e.getMessage(), e);
    }
    this.globalExecutionStatus = globalExecutionStatus;
    return this;
  }

  @JsonGetter("lastTryTime")
  public long getLastTryTime() {
    return lastTryTime;
  }

  @JsonSetter("lastTryTime")
  public IngestRequest setLastTryTime(final long lastTryTime) {
    this.lastTryTime = lastTryTime;
    return this;
  }

  @JsonGetter("status")
  public int getStatus() {
    return status;
  }

  /**
   * Set the status AND the step according to the value of the status (if
   * less than 0, it is a step value, not a final status), but in dry mode
   * (no check, used by Json deserialization)
   *
   * @param status
   *
   * @return this
   */
  @JsonSetter("status")
  public IngestRequest setStatus(final int status) {
    this.status = status;
    if (step != null) {
      step.setDryCurrent(IngestStep.getFromInt(status));
    }
    return this;
  }

  /**
   * Use to set the step and status accordingly.
   *
   * @param step
   * @param status
   * @param factory
   *
   * @return this
   *
   * @throws InvalidParseOperationException
   */
  @JsonIgnore
  public IngestRequest setStep(final IngestStep step, final int status,
                               IngestRequestFactory factory)
      throws InvalidParseOperationException {
    if (this.step == null) {
      if (!step.equals(IngestStep.END)) {
        logger.debug("Step {} could not be set since IngestRequest done", step);
      }
      // Nothing to do since already done
      return this;
    }
    if (!step.equals(IngestStep.ERROR) && this.step.getCurrent().equals(step)) {
      // nothing to do
      return this;
    }
    try {
      this.step.setCurrent(step);
    } catch (IllegalFiniteStateException e) {
      logger.error(e);
      this.step.setDryCurrent(step);
    }
    setStatus(step != IngestStep.ERROR? step.getStatusMonitor() : status)
        .setLastTryTime(System.currentTimeMillis());
    return save(factory);
  }

  @JsonIgnore
  public IngestStep getStep() {
    if (step == null) {
      return null;
    }
    return step.getCurrent();
  }

  /**
   * Save this IngestRequest
   *
   * @param factory
   *
   * @return this
   *
   * @throws InvalidParseOperationException
   */
  @JsonIgnore
  public IngestRequest save(IngestRequestFactory factory)
      throws InvalidParseOperationException {
    factory.saveIngestRequest(this);
    return this;
  }

  @JsonGetter("jsonPath")
  public String getJsonPath() {
    return jsonPath;
  }

  @JsonSetter("jsonPath")
  public IngestRequest setJsonPath(final String jsonPath) {
    try {
      ParametersChecker.checkParameterDefault(getCheckMessage(), jsonPath);
      StringUtils.checkSanityString(jsonPath);
    } catch (InvalidParseOperationException | IllegalArgumentException e) {
      logger.error(e);
      throw new IllegalArgumentException(e.getMessage(), e);
    }
    this.jsonPath = jsonPath;
    return this;
  }

  @JsonGetter("checkAtr")
  public boolean isCheckAtr() {
    return checkAtr;
  }

  @JsonSetter("checkAtr")
  public IngestRequest setCheckAtr(final boolean checkAtr) {
    this.checkAtr = checkAtr;
    return this;
  }

  /**
   * Set extra information from first response from Ingest submission
   *
   * @param requestResponse
   *
   * @return this
   */
  @JsonIgnore
  public IngestRequest setFromRequestResponse(
      RequestResponseOK requestResponse) {
    String requestIdNew =
        requestResponse.getHeaderString(GlobalDataRest.X_REQUEST_ID);
    String globalExecutionStateNew = requestResponse
        .getHeaderString(GlobalDataRest.X_GLOBAL_EXECUTION_STATE);
    String globalExecutionStatusNew = requestResponse
        .getHeaderString(GlobalDataRest.X_GLOBAL_EXECUTION_STATUS);
    try {
      ParametersChecker.checkParameterDefault(getCheckMessage(), requestIdNew,
                                              globalExecutionStateNew,
                                              globalExecutionStatusNew);
      StringUtils.checkSanityString(requestIdNew, globalExecutionStateNew,
                                    globalExecutionStatusNew);
    } catch (InvalidParseOperationException | IllegalArgumentException e) {
      logger.error(e);
      throw new IllegalArgumentException(e.getMessage(), e);
    }
    setRequestId(requestIdNew).setGlobalExecutionState(globalExecutionStateNew)
                              .setGlobalExecutionStatus(
                                  globalExecutionStatusNew);
    return this;
  }

  /**
   * @return the VitamContext according to this
   */
  @JsonIgnore
  public VitamContext getVitamContext() {
    return new VitamContext(tenantId)
        .setApplicationSessionId(applicationSessionId)
        .setPersonalCertificate(personalCertificate)
        .setAccessContract(accessContract);
  }

  /**
   * @return the LocalFile according to this
   */
  @JsonIgnore
  public LocalFile getLocalFile() {
    return new LocalFile(path);
  }

  /**
   * @return the ATR File pointer according to this
   */
  @JsonIgnore
  public File getAtrFile(IngestRequestFactory factory) {
    return factory.getXmlAtrFile(this);
  }

  @JsonIgnore
  public ProcessState getProcessState() {
    try {
      ParametersChecker
          .checkParameterDefault(getCheckMessage(), globalExecutionState);
      StringUtils.checkSanityString(globalExecutionState);
    } catch (InvalidParseOperationException | IllegalArgumentException e) {
      logger.error(e);
      return null;
    }
    try {
      final ProcessState processState =
          ProcessState.valueOf(globalExecutionState);
      if (processState != ProcessState.PAUSE) {
        // Error
        logger.warn("Not PAUSE: {}", globalExecutionState);
      }
      return processState;
    } catch (IllegalArgumentException ignored) {
      // Error
      logger.error("Not PAUSE: {}", globalExecutionState);
    }
    return null;
  }

  @JsonIgnore
  public StatusCode getStatusCode() {
    try {
      ParametersChecker
          .checkParameterDefault(getCheckMessage(), globalExecutionStatus);
      StringUtils.checkSanityString(globalExecutionStatus);
    } catch (InvalidParseOperationException | IllegalArgumentException e) {
      logger.error(e);
      return null;
    }
    try {
      final StatusCode statusCode = StatusCode.valueOf(globalExecutionStatus);
      if (statusCode != StatusCode.UNKNOWN) {
        // Error
        logger.warn("Not UNKNOWN: {}", globalExecutionStatus);
      }
      return statusCode;
    } catch (IllegalArgumentException ignored) {
      // Error
      logger.error("Not UNKNOWN: {}", globalExecutionStatus);
    }
    return null;
  }

  private static String getCheckMessage() {
    return CHECK_MESSAGE +
           Thread.currentThread().getStackTrace()[2].getMethodName();
  }
}
