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

import fr.gouv.vitam.access.external.client.AccessExternalClient;
import fr.gouv.vitam.access.external.client.AdminExternalClient;
import fr.gouv.vitam.access.external.client.AdminExternalClientFactory;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.waarp.common.logging.WaarpLogger;
import org.waarp.common.logging.WaarpLoggerFactory;
import org.waarp.common.utility.Version;
import org.waarp.common.utility.WaarpShutdownHook;
import org.waarp.openr66.configuration.FileBasedConfiguration;
import org.waarp.openr66.protocol.configuration.Configuration;
import org.waarp.vitam.common.WaarpCommon;
import org.waarp.vitam.common.WaarpCommon.MonitorOption;
import org.waarp.vitam.common.WaarpMonitor;
import org.waarp.vitam.common.WaarpVitamShutdownHook;
import org.waarp.vitam.common.WaarpVitamShutdownHook.WaarpVitamShutdownConfiguration;

import java.io.File;

/**
 * DipMonitor is the daemon taking care of DipRequests through a
 * directory containing JSON files
 */
public class DipMonitor extends WaarpMonitor {
  /**
   * Internal Logger
   */
  private static final WaarpLogger logger =
      WaarpLoggerFactory.getLogger(DipMonitor.class);
  private static File waarpConfigurationFile;

  private final DipRequestFactory factory;
  private final DipManager dipManager;

  /**
   * Unique constructor
   *
   * @param elapseTime
   * @param stopFile
   * @param factory
   * @param adminFactory
   * @param dipManager
   */
  public DipMonitor(final long elapseTime, final File stopFile,
                    final DipRequestFactory factory,
                    final AdminExternalClientFactory adminFactory,
                    final DipManager dipManager) {
    super(stopFile, adminFactory, elapseTime);
    this.factory = factory;
    this.dipManager = dipManager;
    if (WaarpShutdownHook.shutdownHook == null) {
      new WaarpVitamShutdownHook(new WaarpVitamShutdownConfiguration(this));
      WaarpVitamShutdownHook.addShutdownHook();
    }
  }

  /**
   * Will try to start the DipMonitor according to arguments, else print
   * the help message
   *
   * @param args
   */
  public static void main(String[] args) {
    Options options = getOptions();

    if (args.length == 0 || WaarpCommon.checkHelp(args)) {
      printHelp(options);
      return;
    }
    final DipMonitor dipMonitor;
    try {
      dipMonitor = getDipMonitor(options, args);
    } catch (ParseException e) {
      logger
          .error("Error while initializing {}", DipMonitor.class.getName(), e);
      printHelp(options);
      return;
    }
    if (!FileBasedConfiguration
        .setSubmitClientConfigurationFromXml(Configuration.configuration,
                                             waarpConfigurationFile
                                                 .getAbsolutePath())) {
      logger.error("Cannot load Waarp Configuration");
      return;
    }
    dipMonitor.invoke();
  }

  /**
   * Define the options associated
   *
   * @return the Options
   */
  private static Options getOptions() {
    Options options = new Options();
    MonitorOption.setStandardMonitorOptions(options);
    MonitorOption.addRetryMonitorOptions(options);
    options.addOption(DipRequestFactory.getDirectoryOption());
    return options;
  }

  /**
   * Helper to print help
   *
   * @param options
   */
  private static void printHelp(Options options) {
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp(DipMonitor.class.getSimpleName(),
                        "Version: " + Version.fullIdentifier(), options,
                        WaarpCommon.FOR_SIMPLE_MANDATORY_ARGUMENTS, true);
  }

  /**
   * Build the DipMonitor according to arguments
   *
   * @param options
   * @param args
   *
   * @return the new DipMonitor
   *
   * @throws ParseException
   */
  private static DipMonitor getDipMonitor(Options options, String[] args)
      throws ParseException {
    CommandLineParser parser = new DefaultParser();
    CommandLine cmd = parser.parse(options, args);
    DipRequestFactory.parseDirectoryOption(cmd);
    MonitorOption monitorOption =
        WaarpCommon.MonitorOption.gestMonitorOption(cmd, args);
    waarpConfigurationFile = new File(monitorOption.getWaarpConfiguration());
    return new DipMonitor(monitorOption.getElapseInSecond() * 1000L,
                          new File(monitorOption.getStopFilePath()),
                          DipRequestFactory.getInstance(),
                          AdminExternalClientFactory.getInstance(),
                          new DipManager());
  }

  /**
   * Launch the DipMonitor
   */
  public void invoke() {
    try (AccessExternalClient client = factory.getClient();
         AdminExternalClient adminExternalClient = getAdminFactory()
             .getClient()) {
      logger.warn("Start of {}", DipMonitor.class.getName());
      while (!isShutdown()) {
        dipManager
            .retryAllExistingFiles(factory, client, adminExternalClient, this);
        Thread.sleep(getElapseTime());
      }
      setShutdown(true);
      logger.warn("Stop of {}", DipMonitor.class.getName());
    } catch (InterruptedException e) {//NOSONAR
      logger.error("{} will stop", DipMonitor.class.getName(), e);
    }
  }

}
