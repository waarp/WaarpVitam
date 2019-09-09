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

package org.waarp.vitam;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.StringUtils;
import fr.gouv.vitam.common.client.VitamContext;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.model.ProcessState;
import fr.gouv.vitam.common.model.StatusCode;
import org.waarp.common.logging.WaarpLogger;
import org.waarp.common.logging.WaarpLoggerFactory;

/**
 * Common part for Vitam Request
 */
public abstract class AbstractVitamRequest {
  /**
   * Internal Logger
   */
  private static final WaarpLogger logger =
      WaarpLoggerFactory.getLogger(AbstractVitamRequest.class);
  private static final String CHECK_MESSAGE = "Check within ";
  @JsonProperty("status")
  protected int status;
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
  @JsonProperty("requestId")
  private String requestId;
  @JsonProperty("globalExecutionState")
  private String globalExecutionState;
  @JsonProperty("globalExecutionStatus")
  private String globalExecutionStatus;
  @JsonProperty("lastTryTime")
  private long lastTryTime;
  @JsonProperty("jsonPath")
  private String jsonPath;
  @JsonProperty("waarpPartner")
  private String waarpPartner;
  @JsonProperty("waarpRule")
  private String waarpRule;
  @JsonProperty("waarpId")
  private long waarpId;

  public AbstractVitamRequest() {
    // Empty constructor for Json
  }

  /**
   * Standard constructor
   *
   * @param tenantId
   * @param applicationSessionId
   * @param personalCertificate
   * @param accessContract
   * @param waarpPartner
   * @param waarpRule
   *
   * @throws InvalidParseOperationException
   */
  public AbstractVitamRequest(final String path, final int tenantId,
                              final String applicationSessionId,
                              final String personalCertificate,
                              final String accessContract,
                              final String waarpPartner, final String waarpRule)
      throws InvalidParseOperationException {
    try {
      ParametersChecker
          .checkParameterDefault(getCheckMessage(), path, accessContract,
                                 waarpPartner, waarpRule);
      StringUtils.checkSanityString(path, applicationSessionId != null?
                                        applicationSessionId : "", accessContract,
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
    this.waarpPartner = waarpPartner;
    this.waarpRule = waarpRule;
  }

  protected static String getCheckMessage() {
    return CHECK_MESSAGE +
           Thread.currentThread().getStackTrace()[2].getMethodName();
  }

  @JsonGetter("path")
  public String getPath() {
    return path;
  }

  @JsonSetter("path")
  public AbstractVitamRequest setPath(final String path) {
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
  public AbstractVitamRequest setTenantId(final int tenantId) {
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
  public AbstractVitamRequest setApplicationSessionId(
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
  public AbstractVitamRequest setPersonalCertificate(
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
  public AbstractVitamRequest setAccessContract(final String accessContract) {
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

  @JsonGetter("waarpPartner")
  public String getWaarpPartner() {
    return waarpPartner;
  }

  @JsonSetter("waarpPartner")
  public AbstractVitamRequest setWaarpPartner(final String waarpPartner) {
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
  public AbstractVitamRequest setWaarpRule(final String waarpRule) {
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
  public AbstractVitamRequest setWaarpId(final long waarpId) {
    this.waarpId = waarpId;
    return this;
  }

  @JsonGetter("requestId")
  public String getRequestId() {
    return requestId;
  }

  @JsonSetter("requestId")
  public AbstractVitamRequest setRequestId(final String requestId) {
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
  public AbstractVitamRequest setGlobalExecutionState(
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
  public AbstractVitamRequest setGlobalExecutionStatus(
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
  public AbstractVitamRequest setLastTryTime(final long lastTryTime) {
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
  public abstract AbstractVitamRequest setStatus(final int status);

  @JsonGetter("jsonPath")
  public String getJsonPath() {
    return jsonPath;
  }

  @JsonSetter("jsonPath")
  public AbstractVitamRequest setJsonPath(final String jsonPath) {
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
      if (processState != ProcessState.PAUSE &&
          processState != ProcessState.COMPLETED) {
        // Error
        logger.warn("Not PAUSE not COMPLETED: {}", globalExecutionState);
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
      return StatusCode.valueOf(globalExecutionStatus);
    } catch (IllegalArgumentException ignored) {
      // Error
      logger.error("Not UNKNOWN: {}", globalExecutionStatus);
    }
    return null;
  }

}
