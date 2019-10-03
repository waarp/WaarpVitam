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
import org.waarp.vitam.dip.DipRequest;
import org.waarp.vitam.dip.DipRequestFactory;
import org.waarp.vitam.ingest.IngestRequest;
import org.waarp.vitam.ingest.IngestRequestFactory;

public interface ManagerToWaarp {
  /**
   * Launch a SubmitTransfer according to arguments for IngestRequest
   *
   * @param ingestRequestFactory
   * @param ingestRequest
   * @param filename
   *
   * @return True if done
   *
   * @throws InvalidParseOperationException
   */
  boolean sendBackInformation(IngestRequestFactory ingestRequestFactory,
                              IngestRequest ingestRequest, String filename,
                              String fileInfo)
      throws InvalidParseOperationException;

  /**
   * Launch a SubmitTransfer according to arguments for DipRequest
   *
   * @param dipRequestFactory
   * @param dipRequest
   * @param filename
   *
   * @return True if done
   *
   * @throws InvalidParseOperationException
   */
  boolean sendBackInformation(DipRequestFactory dipRequestFactory,
                              DipRequest dipRequest, String filename,
                              String fileInfo)
      throws InvalidParseOperationException;
}
