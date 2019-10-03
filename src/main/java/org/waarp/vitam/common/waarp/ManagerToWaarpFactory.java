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

/**
 * Simple factory to get the appropriate ManagerToWaarp
 */
public class ManagerToWaarpFactory {

  public enum WaarpModel {
    /**
     * Native R66 model
     */
    R66,
    /**
     * Script base model
     */
    SCRIPT
  }

  /**
   * @param commandLine
   *
   * @return the associated ManagerToWaarp
   */
  public static ManagerToWaarp getManagerToWaarp(String commandLine) {
    if (commandLine == null) {
      return new ManagerToWaarpR66();
    } else {
      return new ManagerToWaarpScript(commandLine);
    }
  }
}
