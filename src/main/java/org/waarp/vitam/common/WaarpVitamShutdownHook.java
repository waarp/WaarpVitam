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

import org.waarp.common.logging.WaarpLogger;
import org.waarp.common.logging.WaarpLoggerFactory;
import org.waarp.common.utility.WaarpShutdownHook;

import java.io.IOException;
import java.nio.file.Files;

/**
 * Waarp-Vitam Shutdown Hook
 */
public class WaarpVitamShutdownHook extends WaarpShutdownHook {
  /**
   * Internal Logger
   */
  private static final WaarpLogger logger =
      WaarpLoggerFactory.getLogger(WaarpVitamShutdownHook.class);

  public WaarpVitamShutdownHook(final ShutdownConfiguration configuration) {
    super(configuration);
  }

  @Override
  protected void exitService() {
    try {
      Files.createFile(
          ((WaarpVitamShutdownConfiguration) getShutdownConfiguration()).waarpMonitor
              .getStopFile().toPath());
    } catch (IOException e) {
      logger.error(e);
    }
  }

  /**
   * Shutdown configuration including current Monitor
   */
  public static class WaarpVitamShutdownConfiguration
      extends ShutdownConfiguration {
    private final WaarpMonitor waarpMonitor;

    /**
     * Unique constructor
     *
     * @param waarpMonitor
     */
    public WaarpVitamShutdownConfiguration(final WaarpMonitor waarpMonitor) {
      this.waarpMonitor = waarpMonitor;
    }
  }
}
