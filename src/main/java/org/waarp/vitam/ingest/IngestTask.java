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
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.waarp.common.logging.WaarpLogger;
import org.waarp.common.logging.WaarpLoggerFactory;
import org.waarp.openr66.configuration.FileBasedConfiguration;
import org.waarp.openr66.protocol.configuration.Configuration;

import java.io.File;
import java.util.Properties;

public class IngestTask {
  /**
   * Internal Logger
   */
  private static final WaarpLogger logger =
      WaarpLoggerFactory.getLogger(IngestTask.class);
  private static File baseDir = null;
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
  private final File waarpConfigurationFile;

  public IngestTask(final String path, final int tenantId,
                    final String applicationSessionId,
                    final String personalCertificate,
                    final String accessContract, final String contextId,
                    final String action, final String waarpPartner,
                    final String waarpRule, final boolean checkAtr,
                    final File waarpConfigurationFile) {
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
    this.waarpConfigurationFile = waarpConfigurationFile;
  }

  private static Options getOptions() {
    Options options = new Options();
    options.addRequiredOption("f", "file", true, "Path of the local file")
           .addOption(Option.builder("t").longOpt("tenant").hasArg(true)
                            .type(Number.class).desc("Tenant Id").required(true)
                            .build())
           .addRequiredOption("a", "access", true, "Access Contract")
           .addRequiredOption("x", "context", true, "Context Id, shall be one" +
                                                    " of DEFAULT_WORKFLOW, HOLDING_SCHEME, FILING_SCHEME")
           .addRequiredOption("p", "partner", true, "Waarp Partner")
           .addRequiredOption("r", "rule", true, "Waarp Rule")
           .addRequiredOption("w", "waarp", true, "Waarp configuration file")
           .addOption("k", "checkatr", false, "If set, after RequestId sent, " +
                                              "will check for ATR if first step is ok")
           .addOption("s", "session", true, "Application Session Id")
           .addOption("c", "certificate", true, "Personal Certificate")
           .addOption("n", "action", true, "Action, shall be always RESUME");
    Option property = OptionBuilder.withArgName("property=value").hasArgs(2)
                                   .withValueSeparator().withDescription(
            "use value for given property").create("D");
    options.addOption(property);
    return options;
  }

  private static IngestTask getIngestTask(Options options, String[] args)
      throws ParseException {
    CommandLineParser parser = new DefaultParser();
    CommandLine cmd = parser.parse(options, args);
    final String path = cmd.getOptionValue('f');
    final String accessContract = cmd.getOptionValue('a');
    final String waarpPartner = cmd.getOptionValue('p');
    final String waarpRule = cmd.getOptionValue('r');
    final String contextId = cmd.getOptionValue('x');
    final String waarpConfigurationFile = cmd.getOptionValue('w');
    if (!IngestRequest.CONTEXT.checkCorrectness(contextId)) {
      throw new ParseException(
          "Context should be one of DEFAULT_WORKFLOW (Sip ingest), " +
          "HOLDING_SCHEME (tree) or FILING_SCHEME");
    }
    final String action = cmd.getOptionValue('n', IngestRequest.RESUME);
    final String applicationSessionId;
    if (cmd.hasOption('s')) {
      String temp = cmd.getOptionValue('s');
      if (temp.isEmpty()) {
        temp = null;
      }
      applicationSessionId = temp;
    } else {
      applicationSessionId = null;
    }
    final String personalCertificate;
    if (cmd.hasOption('c')) {
      String temp = cmd.getOptionValue('c');
      if (temp.isEmpty()) {
        temp = null;
      }
      personalCertificate = temp;
    } else {
      personalCertificate = null;
    }
    final String stenant = cmd.getOptionValue('t');
    final int tenantId;
    try {
      tenantId = Integer.parseInt(stenant);
      if (tenantId < 0) {
        throw new NumberFormatException("Tenant Id must be positive");
      }
    } catch (NumberFormatException e) {
      throw new ParseException("Tenant Id must be a positive integer");
    }
    if (cmd.hasOption('D')) {
      Properties properties = cmd.getOptionProperties("D");
      baseDir = new File(properties.getProperty(
          IngestRequestFactory.ORG_WAARP_INGEST_BASEDIR,
          IngestRequestFactory.TMP_INGEST_FACTORY));
    }
    return new IngestTask(path, tenantId, applicationSessionId,
                          personalCertificate, accessContract, contextId,
                          action, waarpPartner, waarpRule, cmd.hasOption('k'),
                          new File(waarpConfigurationFile));
  }

  public static void main(String[] args) {
    Options options = getOptions();
    HelpFormatter formatter = new HelpFormatter();

    if (args.length == 0) {
      formatter.printHelp(IngestTask.class.getSimpleName(), options);
      return;
    }
    final IngestTask ingestTask;
    try {
      ingestTask = getIngestTask(options, args);
    } catch (ParseException e) {
      logger
          .error("Error while initializing " + IngestTask.class.getSimpleName(),
                 e);
      formatter.printHelp(IngestTask.class.getSimpleName(), options);
      return;
    }
    IngestRequestFactory.setBaseDir(baseDir);
    if (!FileBasedConfiguration
        .setSubmitClientConfigurationFromXml(Configuration.configuration,
                                             ingestTask.waarpConfigurationFile
                                                 .getAbsolutePath())) {
      logger.error("Cannot load Waarp Configuration");
      return;
    }
    ingestTask.invoke();
  }

  public void invoke() {
    try {
      IngestRequest ingestRequest =
          new IngestRequest(path, tenantId, applicationSessionId,
                            personalCertificate, accessContract, contextId,
                            action, waarpPartner, waarpRule, checkAtr);
      try (IngestExternalClient client = IngestRequestFactory.getInstance()
                                                             .getClient()) {
        IngestManager
            .ingestLocally(IngestRequestFactory.getInstance(), ingestRequest,
                           client);
      }
    } catch (InvalidParseOperationException e) {
      logger.error("Issue since IngestRequest cannot be saved", e);
    }
  }
}
