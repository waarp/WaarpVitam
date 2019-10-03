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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import fr.gouv.vitam.common.GlobalDataRest;
import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.StringUtils;
import fr.gouv.vitam.common.database.builder.query.VitamFieldsHelper;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.model.RequestResponseOK;
import org.waarp.common.exception.IllegalFiniteStateException;
import org.waarp.common.logging.WaarpLogger;
import org.waarp.common.logging.WaarpLoggerFactory;
import org.waarp.common.state.MachineState;
import org.waarp.common.state.Transition;
import org.waarp.vitam.common.AbstractVitamRequest;
import org.waarp.vitam.common.WaarpCommon.TaskOption;

import java.io.File;
import java.util.EnumSet;
import java.util.concurrent.ConcurrentHashMap;

public class DipRequest extends AbstractVitamRequest {
  /**
   * Internal Logger
   */
  private static final WaarpLogger logger =
      WaarpLoggerFactory.getLogger(DipRequest.class);
  @JsonIgnore
  MachineState<DIPStep> step = DIPStep.newSessionMachineState();

  public DipRequest() {
    // Empty constructor for Json
  }

  /**
   * Standard constructor
   *
   * @param taskOption
   * @param factory
   *
   * @throws InvalidParseOperationException
   */
  public DipRequest(final TaskOption taskOption,
                    final DipRequestFactory factory)
      throws InvalidParseOperationException {
    super(taskOption);
    this.status = this.step.getCurrent().getStatusMonitor();
    try {
      factory.saveNewDipRequest(this);
    } catch (InvalidParseOperationException e) {
      logger.error("Will not be able to save: {}", this, e);
      throw e;
    }
  }

  @Override
  public String toString() {
    return "DIP = Step: " + (step != null? step.getCurrent() : "noStep") + " " +
           JsonHandler.unprettyPrint(this);
  }

  /**
   * Set extra information from first response from operation submission
   *
   * @param requestResponse
   *
   * @return this
   */
  @JsonIgnore
  public DipRequest setFromRequestResponse(RequestResponseOK requestResponse) {
    String requestIdNew =
        requestResponse.getHeaderString(GlobalDataRest.X_REQUEST_ID);
    // Shall be the same but get from Result preferred
    JsonNode node = (JsonNode) requestResponse.getFirstResult();
    if (node != null) {
      node = node.get(VitamFieldsHelper.id());
      if (node != null) {
        requestIdNew = node.asText();
      }
    }
    try {
      ParametersChecker.checkParameterDefault(getCheckMessage(), requestIdNew);
      StringUtils.checkSanityString(requestIdNew);
    } catch (InvalidParseOperationException | IllegalArgumentException e) {
      logger.error(e);
      throw new IllegalArgumentException(e.getMessage(), e);
    }
    setRequestId(requestIdNew);
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
  public DipRequest setStep(final DIPStep step, final int status,
                            DipRequestFactory factory)
      throws InvalidParseOperationException {
    if (this.step == null) {
      if (!step.equals(DIPStep.END)) {
        logger.debug("Step {} could not be set since IngestRequest done", step);
      }
      // Nothing to do since already done
      return this;
    }
    if (!step.equals(DIPStep.ERROR) && this.step.getCurrent().equals(step)) {
      // nothing to do
      return this;
    }
    try {
      this.step.setCurrent(step);
    } catch (IllegalFiniteStateException e) {
      logger.error(e);
      this.step.setDryCurrent(step);
    }
    setStatus(step != DIPStep.ERROR? step.getStatusMonitor() : status)
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
  public DipRequest setStatus(final int status) {
    this.status = status;
    if (step != null) {
      step.setDryCurrent(DIPStep.getFromInt(status));
    }
    return this;
  }

  /**
   * Save this DipRequest
   *
   * @param factory
   *
   * @return this
   *
   * @throws InvalidParseOperationException
   */
  @JsonIgnore
  public DipRequest save(DipRequestFactory factory)
      throws InvalidParseOperationException {
    factory.saveDipRequest(this);
    return this;
  }

  @JsonIgnore
  public DIPStep getStep() {
    if (step == null) {
      return null;
    }
    return step.getCurrent();
  }

  /**
   * @return the DIP File pointer according to this
   */
  @JsonIgnore
  public File getDipFile(DipRequestFactory factory) {
    return factory.getDipFile(this);
  }

  /**
   * @return the Error File pointer according to this
   */
  @JsonIgnore
  public File getErrorFile(DipRequestFactory factory) {
    return factory.getErrorFile(this);
  }

  /**
   * @return the JsonNode for Vitam Request associated with this
   *
   * @throws InvalidParseOperationException
   */
  @JsonIgnore
  public JsonNode getSelectJson() throws InvalidParseOperationException {
    String path = getPath();
    return JsonHandler.getFromFile(new File(path));
  }

  /**
   * Different steps of DIP export from Waarp point of view
   */
  enum DIPStep {
    /**
     * DipRequest not started yet
     */
    STARTUP(-1),
    /**
     * DipRequest SELECT to retry
     */
    RETRY_SELECT(-2),
    /**
     * DipRequest DIP get to retry
     */
    RETRY_DIP(-3),
    /**
     * DipRequest DIP to forward
     */
    RETRY_DIP_FORWARD(-4),
    /**
     * For Ingest compatibility
     */
    NO_STEP(-5),
    /**
     * DipRequest Error
     */
    ERROR(-7),
    /**
     * Final End step
     */
    END(-10);

    private static final ConcurrentHashMap<DIPStep, EnumSet<DIPStep>> stateMap =
        new ConcurrentHashMap<>();

    static {
      initR66FiniteStates();
    }

    private final int statusMonitor;

    DIPStep(int status) {
      this.statusMonitor = status;
    }

    /**
     * This method should be called once at startup to initialize the Finite
     * States association.
     */
    private static void initR66FiniteStates() {
      for (final DIPStep.IngestTransition trans : DIPStep.IngestTransition
          .values()) {
        stateMap.put(trans.elt.getState(), trans.elt.getSet());
      }
    }

    /**
     * @return a new Session MachineState for OpenR66
     */
    private static MachineState<DIPStep> newSessionMachineState() {
      return new MachineState<>(STARTUP, stateMap);
    }

    /**
     * @param machine the Session MachineState to release
     */
    static void endSessionMachineSate(MachineState<DIPStep> machine) {
      if (machine != null) {
        machine.release();
      }
    }

    static DIPStep getFromInt(int status) {
      switch (status) {
        case -1:
          return STARTUP;
        case -2:
          return RETRY_SELECT;
        case -3:
          return RETRY_DIP;
        case -4:
          return RETRY_DIP_FORWARD;
        case -5:
          return NO_STEP;
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
      T_STARTUP(STARTUP, EnumSet.of(RETRY_SELECT, ERROR)),
      T_RETRY_SELECT(RETRY_SELECT, EnumSet.of(RETRY_DIP, ERROR)),
      T_RETRY_DIP(RETRY_DIP, EnumSet.of(RETRY_DIP_FORWARD, ERROR, END)),
      T_RETRY_DIP_FORWARD(RETRY_DIP_FORWARD, EnumSet.of(END, ERROR)),
      T_ERROR(ERROR, EnumSet.of(ERROR, END)), T_END(END, EnumSet.of(END));

      private final Transition<DIPStep> elt;

      IngestTransition(DIPStep state, EnumSet<DIPStep> set) {
        elt = new Transition<>(state, set);
      }

    }
  }
}
