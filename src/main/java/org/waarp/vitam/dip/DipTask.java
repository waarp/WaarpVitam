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
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
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
import org.waarp.openr66.context.task.AbstractExecJavaTask;
import org.waarp.openr66.protocol.configuration.Configuration;
import org.waarp.vitam.WaarpCommon;
import org.waarp.vitam.WaarpCommon.TaskOption;

import java.io.File;

/**
 * DipTask is a one shot command that will initiate one specific DIP export
 * operation from a Waarp post task on reception.
 */
public class DipTask {
  /**
   * Internal Logger
   */
  private static final WaarpLogger logger =
      WaarpLoggerFactory.getLogger(DipTask.class);
  private static File waarpConfigurationFile;
  private static int statusMain;
  private final String path;
  private final int tenantId;
  private final String applicationSessionId;
  private final String personalCertificate;
  private final String accessContract;
  private final String waarpPartner;
  private final String waarpRule;
  private final DipRequestFactory factory;
  private final DipManager dipManager;

  /**
   * Unique constructor
   *
   * @param path
   * @param tenantId
   * @param applicationSessionId
   * @param personalCertificate
   * @param accessContract
   * @param waarpPartner
   * @param waarpRule
   * @param factory
   * @param dipManager
   */
  public DipTask(final String path, final int tenantId,
                 final String applicationSessionId,
                 final String personalCertificate, final String accessContract,
                 final String waarpPartner, final String waarpRule,
                 final DipRequestFactory factory, final DipManager dipManager) {
    this.path = path;
    this.tenantId = tenantId;
    this.applicationSessionId = applicationSessionId;
    this.personalCertificate = personalCertificate;
    this.accessContract = accessContract;
    this.waarpPartner = waarpPartner;
    this.waarpRule = waarpRule;
    this.factory = factory;
    this.dipManager = dipManager;
  }

  /**
   * Will try to start the DipTask according to arguments, else print
   * the help message
   *
   * @param args
   */
  public static void main(String[] args) {
    Options options = getOptions();

    if (args.length == 0 || WaarpCommon.checkHelp(args)) {
      printHelp(options);
      statusMain = 2;
      return;
    }
    final DipTask dipTask;
    try {
      dipTask = getDipTask(options, args);
    } catch (ParseException e) {
      logger.error("Error while initializing " + DipTask.class.getSimpleName(),
                   e);
      printHelp(options);
      statusMain = 2;
      return;
    }
    if (!FileBasedConfiguration
        .setSubmitClientConfigurationFromXml(Configuration.configuration,
                                             waarpConfigurationFile
                                                 .getAbsolutePath())) {
      logger.error("Cannot load Waarp Configuration");
      statusMain = 2;
      return;
    }
    statusMain = dipTask.invoke();
  }

  /**
   * Define the options associated
   *
   * @return the Options
   */
  private static Options getOptions() {
    Options options = new Options();
    TaskOption.setStandardTaskOptions(options);
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
    formatter.printHelp(DipTask.class.getSimpleName(),
                        "Version: " + Version.fullIdentifier(), options,
                        WaarpCommon.FOR_MANDATORY_ARGUMENTS, true);
  }

  /**
   * Build the DipTask according to arguments
   *
   * @param options
   * @param args
   *
   * @return the new DipTask
   *
   * @throws ParseException
   */
  private static DipTask getDipTask(Options options, String[] args)
      throws ParseException {
    CommandLineParser parser = new DefaultParser();
    CommandLine cmd = parser.parse(options, args);
    DipRequestFactory.parseDirectoryOption(cmd);
    TaskOption taskOption = TaskOption.getTaskOption(cmd, args);
    waarpConfigurationFile = new File(taskOption.getWaarpConfigurationFile());
    return new DipTask(taskOption.getPath(), taskOption.getTenantId(),
                       taskOption.getApplicationSessionId(),
                       taskOption.getPersonalCertificate(),
                       taskOption.getAccessContract(),
                       taskOption.getWaarpPartner(), taskOption.getWaarpRule(),
                       DipRequestFactory.getInstance(), new DipManager());
  }

  /**
   * Launch the DipMonitor
   *
   * @return 0 if OK, 1 if Warning, 2 if error
   */
  public int invoke() {
    try (AccessExternalClient client = factory.getClient()) {
      DipRequest dipRequest =
          new DipRequest(path, tenantId, applicationSessionId,
                         personalCertificate, accessContract, waarpPartner,
                         waarpRule, factory);
      return dipManager.select(factory, dipRequest, client);
    } catch (InvalidParseOperationException e) {
      logger.error("Issue since DipRequest cannot be saved", e);
    }
    return 2;
  }

  /**
   * Equivalent JavaTask
   */
  public static class JavaTask extends AbstractExecJavaTask {
    @Override
    public void run() {
      final String[] args = BLANK.split(fullarg);
      main(args);
      status = statusMain;
    }
  }
}
