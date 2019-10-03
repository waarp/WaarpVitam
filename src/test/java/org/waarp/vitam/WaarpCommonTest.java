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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.junit.Test;
import org.waarp.vitam.common.WaarpCommon.MonitorOption;
import org.waarp.vitam.common.WaarpCommon.TaskOption;

import static junit.framework.TestCase.*;

public class WaarpCommonTest {

  /**
   * Method: getTaskOption(final CommandLine cmd, final String[] args)
   */
  @Test
  public void testGetTaskOption() throws Exception {
    Options options = new Options();
    TaskOption.setStandardTaskOptions(options);
    CommandLineParser parser = new DefaultParser();
    String[] args = new String[] {
        "-t", "1", "-f", "/tmp/test", "-a", "access", "-p", "hosta", "-r",
        "send", "-w", "/tmp/test", "-c", "certificate", "-s", "session"
    };
    CommandLine cmd = parser.parse(options, args);
    TaskOption taskOption = TaskOption.getTaskOption(cmd, args);
    assertNotNull(taskOption);
    assertEquals(1, taskOption.getTenantId());
    assertEquals("access", taskOption.getAccessContract());
    assertEquals("/tmp/test", taskOption.getPath());
    assertEquals("certificate", taskOption.getPersonalCertificate());
    assertEquals("/tmp/test", taskOption.getWaarpConfigurationFile());
    assertEquals("session", taskOption.getApplicationSessionId());
    assertEquals("hosta", taskOption.getWaarpPartner());
    assertEquals("send", taskOption.getWaarpRule());
  }


  /**
   * Method: gestMonitorOption(CommandLine cmd, String[] args)
   */
  @Test
  public void testGestMonitorOption() throws Exception {
    Options options = new Options();
    MonitorOption.setStandardMonitorOptions(options);
    CommandLineParser parser = new DefaultParser();
    String[] args = new String[] {
        "-e", "1", "-s", "/tmp/test2", "-w", "/tmp/test"
    };
    CommandLine cmd = parser.parse(options, args);
    MonitorOption monitorOption = MonitorOption.gestMonitorOption(cmd, args);
    assertNotNull(monitorOption);
    assertEquals(1, monitorOption.getElapseInSecond());
    assertEquals("/tmp/test", monitorOption.getWaarpConfiguration());
    assertEquals("/tmp/test2", monitorOption.getStopFilePath());
  }


} 
