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

package org.waarp.vitam.common;

import fr.gouv.vitam.access.external.client.AdminExternalClient;
import fr.gouv.vitam.access.external.client.AdminExternalClientFactory;
import fr.gouv.vitam.access.external.client.VitamPoolingClient;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamClientException;
import fr.gouv.vitam.common.exception.VitamException;
import fr.gouv.vitam.common.json.JsonHandler;
import org.waarp.common.logging.WaarpLogger;
import org.waarp.common.logging.WaarpLoggerFactory;
import org.waarp.vitam.dip.DipRequest;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * Class that pool the result from an asynchrone Vitam operation
 */
public class OperationCheck {
  /**
   * Internal Logger
   */
  private static final WaarpLogger logger =
      WaarpLoggerFactory.getLogger(OperationCheck.class);

  private static int retry = 3;
  private static int delay = 100;
  private static boolean result;
  private final AdminExternalClient client;

  public OperationCheck(AdminExternalClient client) {
    this.client = client;
  }

  /**
   * Change the default retry and delay for pooling of operation
   *
   * @param retryToSet
   * @param delayToSet
   */
  public static void setRetry(int retryToSet, int delayToSet) {
    retry = retryToSet;
    delay = delayToSet;
  }

  /**
   * @return the number of retries for pooling of operation
   */
  public static int getRetry() {
    return retry;
  }

  /**
   * @return the current delay between 2 retries for pooling of operation
   */
  public static int getDelay() {
    return delay;
  }

  public static boolean getResult() {
    return result;
  }

  public static void main(String[] args) {
    if (args.length < 1) {
      logger.error("{} needs 1 argument: json_request_file",
                   OperationCheck.class.getSimpleName());
      return;
    }
    File file = new File(args[0]);
    if (!file.canRead()) {
      logger.error("{} needs 1 valid argument: json_request_file",
                   OperationCheck.class.getSimpleName());
      result = false;
      return;
    }
    DipRequest dipRequest;
    try {
      dipRequest = JsonHandler.getFromFile(file, DipRequest.class);
    } catch (InvalidParseOperationException e) {
      logger.error("{} needs 1 valid argument: json_request_file",
                   OperationCheck.class.getSimpleName());
      result = false;
      return;
    }
    try (AdminExternalClient client = AdminExternalClientFactory.getInstance()
                                                                .getClient()) {
      OperationCheck operationCheck = new OperationCheck(client);
      if (operationCheck.checkAvailabilityAtr(dipRequest.getTenantId(),
                                              dipRequest.getRequestId())) {
        logger.warn("Operation {} for tenant {} is finished",
                    dipRequest.getRequestId(), dipRequest.getTenantId());
        result = true;
      } else {
        logger.warn("Operation {} for tenant {} is started or unknown",
                    dipRequest.getRequestId(), dipRequest.getTenantId());
        result = false;
      }
    }
  }

  /**
   * Check if the corresponding operation is done
   *
   * @param tenantId the tenantId associated with the operation
   * @param requestId the operation Id
   *
   * @return True if done
   */
  public boolean checkAvailabilityAtr(int tenantId, String requestId) {
    try {
      VitamPoolingClient vitamPoolingClient = new VitamPoolingClient(client);
      return vitamPoolingClient
          .wait(tenantId, requestId, retry, delay, TimeUnit.MILLISECONDS);
    } catch (VitamClientException e) {
      logger.warn(e);
      return false;
    } catch (VitamException e) {
      logger.info(e);
      return false;
    }
  }
}
