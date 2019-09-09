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

import fr.gouv.vitam.common.exception.InvalidParseOperationException;
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
import org.waarp.openr66.context.task.AbstractExecJavaTask;
import org.waarp.openr66.protocol.configuration.Configuration;
import org.waarp.vitam.WaarpCommon;
import org.waarp.vitam.WaarpCommon.TaskOption;

import java.io.File;

/**
 * IngestTask is a one shot command that will initiate one specific Ingest
 * operation from a Waarp post task on reception.
 */
public class IngestTask {
  /**
   * Internal Logger
   */
  private static final WaarpLogger logger =
      WaarpLoggerFactory.getLogger(IngestTask.class);
  private static File waarpConfigurationFile;
  private static int statusMain;
  private final String path;
  private final int tenantId;
  private final String applicationSessionId;
  private final String personalCertificate;
  private final String accessContract;
  private final String contextId;
  private final String waarpPartner;
  private final String waarpRule;
  private final String action;
  private final boolean checkAtr;
  private final IngestRequestFactory factory;
  private final IngestManager ingestManager;

  /**
   * Unique constructor
   *
   * @param path
   * @param tenantId
   * @param applicationSessionId
   * @param personalCertificate
   * @param accessContract
   * @param contextId
   * @param action
   * @param waarpPartner
   * @param waarpRule
   * @param checkAtr
   * @param factory
   * @param ingestManager
   */
  public IngestTask(final String path, final int tenantId,
                    final String applicationSessionId,
                    final String personalCertificate,
                    final String accessContract, final String contextId,
                    final String action, final String waarpPartner,
                    final String waarpRule, final boolean checkAtr,
                    final IngestRequestFactory factory,
                    final IngestManager ingestManager) {
    this.path = path;
    this.tenantId = tenantId;
    this.applicationSessionId = applicationSessionId;
    this.personalCertificate = personalCertificate;
    this.accessContract = accessContract;
    this.contextId = contextId;
    this.action = action;
    this.waarpPartner = waarpPartner;
    this.waarpRule = waarpRule;
    this.checkAtr = checkAtr;
    this.factory = factory;
    this.ingestManager = ingestManager;
  }

  /**
   * Will try to start the IngestTask according to arguments, else print
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
    final IngestTask ingestTask;
    try {
      ingestTask = getIngestTask(options, args);
    } catch (ParseException e) {
      logger
          .error("Error while initializing " + IngestTask.class.getSimpleName(),
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
    statusMain = ingestTask.invoke();
  }

  /**
   * Define the options associated
   *
   * @return the Options
   */
  private static Options getOptions() {
    Options options = new Options();
    TaskOption.setStandardTaskOptions(options);
    options.addOption("x", "context", true, "Context Id, shall be one" +
                                            " of DEFAULT_WORKFLOW (default), " +
                                            "HOLDING_SCHEME, FILING_SCHEME")
           .addOption("k", "checkatr", false, "If set, after RequestId sent, " +
                                              "will check for ATR if first step is ok")
           .addOption("n", "action", true, "Action, shall be always RESUME");
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
    formatter.printHelp(IngestTask.class.getSimpleName(),
                        "Version: " + Version.fullIdentifier(), options,
                        WaarpCommon.FOR_MANDATORY_ARGUMENTS, true);
  }

  /**
   * Build the IngestTask according to arguments
   *
   * @param options
   * @param args
   *
   * @return the new IngestTask
   *
   * @throws ParseException
   */
  private static IngestTask getIngestTask(Options options, String[] args)
      throws ParseException {
    CommandLineParser parser = new DefaultParser();
    CommandLine cmd = parser.parse(options, args);
    IngestRequestFactory.parseDirectoryOption(cmd);
    final String contextId = cmd.getOptionValue('x', "DEFAULT_WORKFLOW");
    if (!IngestRequest.CONTEXT.checkCorrectness(contextId)) {
      throw new ParseException(
          "Context should be one of DEFAULT_WORKFLOW (Sip ingest), " +
          "HOLDING_SCHEME (tree) or FILING_SCHEME");
    }
    final String action = cmd.getOptionValue('n', IngestRequest.RESUME);
    TaskOption taskOption = TaskOption.getTaskOption(cmd, args);
    waarpConfigurationFile = new File(taskOption.getWaarpConfigurationFile());
    return new IngestTask(taskOption.getPath(), taskOption.getTenantId(),
                          taskOption.getApplicationSessionId(),
                          taskOption.getPersonalCertificate(),
                          taskOption.getAccessContract(), contextId, action,
                          taskOption.getWaarpPartner(),
                          taskOption.getWaarpRule(), cmd.hasOption('k'),
                          IngestRequestFactory.getInstance(),
                          new IngestManager());
  }

  /**
   * Launch the IngestMonitor
   *
   * @return 0 if OK, 1 if Warning, 2 if error
   */
  public int invoke() {
    try (IngestExternalClient client = factory.getClient()) {
      IngestRequest ingestRequest =
          new IngestRequest(path, tenantId, applicationSessionId,
                            personalCertificate, accessContract, contextId,
                            action, waarpPartner, waarpRule, checkAtr, factory);
      return ingestManager.ingestLocally(factory, ingestRequest, client);
    } catch (InvalidParseOperationException e) {
      logger.error("Issue since IngestRequest cannot be saved", e);
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
