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

package org.waarp.vitam.common.waarp;

import fr.gouv.vitam.common.exception.InvalidParseOperationException;
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
import org.waarp.vitam.common.AbstractVitamRequest;
import org.waarp.vitam.dip.DipRequest;
import org.waarp.vitam.dip.DipRequestFactory;
import org.waarp.vitam.ingest.IngestRequest;
import org.waarp.vitam.ingest.IngestRequestFactory;

/**
 * Class for Waarp sending back to Waarp Partner from Monitor using native R66
 */
class ManagerToWaarpR66 implements ManagerToWaarp {
  /**
   * Internal Logger
   */
  private static final WaarpLogger logger =
      WaarpLoggerFactory.getLogger(ManagerToWaarpR66.class);

  ManagerToWaarpR66() {
    // nothing
  }

  @Override
  public boolean sendBackInformation(
      final IngestRequestFactory ingestRequestFactory,
      final IngestRequest ingestRequest, final String filename,
      final String fileInfo) throws InvalidParseOperationException {
    logger.debug("Will send {} while step is {}", filename,
                 ingestRequest.getStep());
    R66Future future = new R66Future(true);
    SubmitTransfer submitTransfer =
        new SubmitTransfer(future, ingestRequest.getWaarpPartner(), filename,
                           ingestRequest.getWaarpRule(),
                           ingestRequest.getRequestId() + ' ' + fileInfo, true,
                           Configuration.configuration.getBlockSize(),
                           DbConstantR66.ILLEGALVALUE, null);
    submitTransfer.run();
    future.awaitOrInterruptible();
    if (future.isSuccess()) {
      ingestRequest.setWaarpId(future.getResult().getRunner().getSpecialId());
      ingestRequest.save(ingestRequestFactory);
      return waitForAllDone(ingestRequest);
    }
    return false;
  }

  @Override
  public boolean sendBackInformation(final DipRequestFactory dipRequestFactory,
                                     final DipRequest dipRequest,
                                     final String filename,
                                     final String fileInfo)
      throws InvalidParseOperationException {
    logger
        .debug("Will send {} while step is {}", filename, dipRequest.getStep());
    R66Future future = new R66Future(true);
    SubmitTransfer submitTransfer =
        new SubmitTransfer(future, dipRequest.getWaarpPartner(), filename,
                           dipRequest.getWaarpRule(),
                           dipRequest.getRequestId() + ' ' + fileInfo, true,
                           Configuration.configuration.getBlockSize(),
                           DbConstantR66.ILLEGALVALUE, null);
    submitTransfer.run();
    future.awaitOrInterruptible();
    if (future.isSuccess()) {
      dipRequest.setWaarpId(future.getResult().getRunner().getSpecialId());
      dipRequest.save(dipRequestFactory);
      return waitForAllDone(dipRequest);
    }
    return false;
  }

  /**
   * Ensure that SubmitTransfer is done totally (file sent) before continuing
   *
   * @param abstractVitamRequest
   *
   * @return True if done
   */
  private boolean waitForAllDone(AbstractVitamRequest abstractVitamRequest) {
    while (true) {
      try {
        DbHostAuth dbHostAuth =
            new DbHostAuth(abstractVitamRequest.getWaarpPartner());
        DbTaskRunner checkedRunner =
            new DbTaskRunner(abstractVitamRequest.getWaarpId(),
                             Configuration.configuration
                                 .getHostId(dbHostAuth.isSsl()),
                             abstractVitamRequest.getWaarpPartner());
        if (checkedRunner.isAllDone()) {
          logger.info("DbTaskRunner done");
          return true;
        } else if (checkedRunner.isInError()) {
          logger.warn("DbTaskRunner in error for {}", abstractVitamRequest);
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
