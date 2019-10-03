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
import fr.gouv.vitam.common.StringUtils;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.model.LocalFile;
import fr.gouv.vitam.common.model.RequestResponseOK;
import org.waarp.common.exception.IllegalFiniteStateException;
import org.waarp.common.exception.InvalidArgumentException;
import org.waarp.common.logging.WaarpLogger;
import org.waarp.common.logging.WaarpLoggerFactory;
import org.waarp.common.state.MachineState;
import org.waarp.common.state.Transition;
import org.waarp.common.utility.ParametersChecker;
import org.waarp.vitam.common.AbstractVitamRequest;
import org.waarp.vitam.common.WaarpCommon.TaskOption;

import java.io.File;
import java.util.EnumSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IngestRequest is the unitary entry for Ingest operations made by Waarp to
 * Vitam
 */
public class IngestRequest extends AbstractVitamRequest {
  /**
   * Only kind of action supported by Vitam and Waarp
   */
  static final String RESUME = "RESUME";
  /**
   * Internal Logger
   */
  private static final WaarpLogger logger =
      WaarpLoggerFactory.getLogger(IngestRequest.class);
  @JsonIgnore
  MachineState<IngestStep> step = IngestStep.newSessionMachineState();
  /*
   contextId – a type of ingest among "DEFAULT_WORKFLOW" (Sip ingest),
   "HOLDING_SCHEME" (tree) "FILING_SCHEME" (plan)
   */
  @JsonProperty("contextId")
  private String contextId;
  @JsonProperty("action")
  private String action = RESUME;
  @JsonProperty("checkAtr")
  private boolean checkAtr;

  public IngestRequest() {
    // Empty constructor for Json
  }

  /**
   * Standard constructor
   *
   * @param taskOption
   * @param contextId
   * @param action
   * @param checkAtr
   * @param factory
   *
   * @throws InvalidParseOperationException
   */
  public IngestRequest(final TaskOption taskOption, final String contextId,
                       final String action, final boolean checkAtr,
                       final IngestRequestFactory factory)
      throws InvalidParseOperationException {
    super(taskOption);
    try {
      ParametersChecker
          .checkParameterDefault(getCheckMessage(), contextId, action);
      ParametersChecker.checkSanityString(contextId, action);
    } catch (IllegalArgumentException | InvalidArgumentException e) {
      logger.error(e);
      throw new InvalidParseOperationException(e);
    }
    this.contextId = contextId;
    this.action = action;
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

  @JsonIgnore
  public IngestStep getStep() {
    if (step == null) {
      return null;
    }
    return step.getCurrent();
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
   * Set extra information from first response from operation submission
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
      ParametersChecker.checkSanityString(requestIdNew, globalExecutionStateNew,
                                          globalExecutionStatusNew);
    } catch (IllegalArgumentException | InvalidArgumentException e) {
      logger.error(e);
      throw new IllegalArgumentException(e.getMessage(), e);
    }
    setGlobalExecutionState(globalExecutionStateNew)
        .setGlobalExecutionStatus(globalExecutionStatusNew)
        .setRequestId(requestIdNew);
    return this;
  }

  /**
   * @return the LocalFile according to this
   */
  @JsonIgnore
  public LocalFile getLocalFile() {
    return new LocalFile(getPath());
  }

  /**
   * @return the ATR File pointer according to this
   */
  @JsonIgnore
  public File getAtrFile(IngestRequestFactory factory) {
    return factory.getXmlAtrFile(this);
  }

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

    private static final ConcurrentHashMap<IngestStep, EnumSet<IngestStep>>
        stateMap = new ConcurrentHashMap<>();

    static {
      initR66FiniteStates();
    }

    private final int statusMonitor;

    IngestStep(int status) {
      this.statusMonitor = status;
    }

    /**
     * This method should be called once at startup to initialize the Finite
     * States association.
     */
    private static void initR66FiniteStates() {
      for (final IngestTransition trans : IngestTransition.values()) {
        stateMap.put(trans.elt.getState(), trans.elt.getSet());
      }
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
  }

}
