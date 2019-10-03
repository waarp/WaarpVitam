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

import fr.gouv.vitam.access.external.client.AdminExternalClientFactory;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Interface of all Waarp-Vitam Monitor
 */
public abstract class WaarpMonitor {
  protected final long elapseTime;
  private final File stopFile;
  private final AdminExternalClientFactory adminFactory;
  private final AtomicBoolean shutdown = new AtomicBoolean(false);

  /**
   * @param stopFile
   * @param adminFactory
   */
  protected WaarpMonitor(final File stopFile,
                         final AdminExternalClientFactory adminFactory,
                         final long elapseTime) {
    this.stopFile = stopFile;
    this.adminFactory = adminFactory;
    this.elapseTime = elapseTime;
  }

  /**
   * @return the stop File for this Monitor
   */
  public File getStopFile() {
    return stopFile;
  }

  /**
   * @return the {@link AdminExternalClientFactory} for this Monitor
   */
  public AdminExternalClientFactory getAdminFactory() {
    return adminFactory;
  }

  /**
   * @param isShutdown
   *
   * @return this
   */
  public WaarpMonitor setShutdown(boolean isShutdown) {
    shutdown.set(isShutdown);
    return this;
  }

  /**
   * @return true if this Monitor is in shutdown
   */
  public boolean isShutdown() {
    return stopFile.exists() || shutdown.get();
  }

  public long getElapseTime() {
    return elapseTime;
  }
}
