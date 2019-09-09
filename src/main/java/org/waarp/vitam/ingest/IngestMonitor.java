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

package org.waarp.vitam.ingest;

import fr.gouv.vitam.access.external.client.AdminExternalClient;
import fr.gouv.vitam.access.external.client.AdminExternalClientFactory;
import fr.gouv.vitam.ingest.external.client.IngestExternalClient;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.waarp.common.logging.WaarpLogger;
import org.waarp.common.logging.WaarpLoggerFactory;
import org.waarp.common.utility.Version;
import org.waarp.openr66.configuration.FileBasedConfiguration;
import org.waarp.openr66.protocol.configuration.Configuration;
import org.waarp.vitam.WaarpCommon;
import org.waarp.vitam.WaarpCommon.MonitorOption;

import java.io.File;

/**
 * IngestMonitor is the daemon taking care of IngestRequests through a
 * directory containing JSON files
 */
public class IngestMonitor {
  /**
   * Internal Logger
   */
  private static final WaarpLogger logger =
      WaarpLoggerFactory.getLogger(IngestMonitor.class);
  private static File waarpConfigurationFile;
  private final long elapseTime;
  private final File stopFile;
  private final IngestRequestFactory factory;
  private final IngestManager ingestManager;
  private final AdminExternalClientFactory adminFactory;

  /**
   * Unique constructor
   *
   * @param elapseTime
   * @param stopFile
   * @param factory
   * @param ingestManager
   */
  IngestMonitor(final long elapseTime, final File stopFile,
                final IngestRequestFactory factory,
                final AdminExternalClientFactory adminFactory,
                final IngestManager ingestManager) {
    this.elapseTime = elapseTime;
    this.stopFile = stopFile;
    this.factory = factory;
    this.adminFactory = adminFactory;
    this.ingestManager = ingestManager;
  }

  /**
   * Will try to start the IngestMonitor according to arguments, else print
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
    final IngestMonitor ingestMonitor;
    try {
      ingestMonitor = getIngestMonitor(options, args);
    } catch (ParseException e) {
      logger.error("Error while initializing {}", IngestMonitor.class.getName(),
                   e);
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
    ingestMonitor.invoke();
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
    options.addOption(IngestRequestFactory.getDirectoryOption());
    return options;
  }

  /**
   * Helper to print help
   *
   * @param options
   */
  private static void printHelp(Options options) {
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp(IngestMonitor.class.getSimpleName(),
                        "Version: " + Version.fullIdentifier(), options,
                        WaarpCommon.FOR_SIMPLE_MANDATORY_ARGUMENTS, true);
  }

  /**
   * Build the IngestMonitor according to arguments
   *
   * @param options
   * @param args
   *
   * @return the new IngestMonitor
   *
   * @throws ParseException
   */
  private static IngestMonitor getIngestMonitor(Options options, String[] args)
      throws ParseException {
    CommandLineParser parser = new DefaultParser();
    CommandLine cmd = parser.parse(options, args);
    IngestRequestFactory.parseDirectoryOption(cmd);
    MonitorOption monitorOption =
        WaarpCommon.MonitorOption.gestMonitorOption(cmd, args);
    waarpConfigurationFile = new File(monitorOption.getWaarpConfiguration());
    return new IngestMonitor(monitorOption.getElapseInSecond() * 1000L,
                             new File(monitorOption.getStopFilePath()),
                             IngestRequestFactory.getInstance(),
                             AdminExternalClientFactory.getInstance(),
                             new IngestManager());
  }

  /**
   * Launch the IngestMonitor
   */
  public void invoke() {
    try (IngestExternalClient client = factory.getClient();
         AdminExternalClient adminExternalClient = adminFactory.getClient()) {
      logger.warn("Start of {}", IngestMonitor.class.getName());
      while (!stopFile.exists()) {
        ingestManager
            .retryAllExistingFiles(factory, client, adminExternalClient,
                                   stopFile);
        Thread.sleep(elapseTime);
      }
      logger.warn("Stop of {}", IngestMonitor.class.getName());
    } catch (InterruptedException e) {//NOSONAR
      logger.error("{} will stop", IngestMonitor.class.getName(), e);
    }
  }
}
