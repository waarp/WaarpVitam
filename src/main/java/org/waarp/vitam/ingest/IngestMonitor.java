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

public class IngestMonitor {
  /**
   * Internal Logger
   */
  private static final WaarpLogger logger =
      WaarpLoggerFactory.getLogger(IngestMonitor.class);
  private static File baseDir = null;
  private final long elapseTime;
  private final File stopFile;
  private final File waarpConfigurationFile;

  public IngestMonitor(final long elapseTime, final File stopFile,
                       final File waarpConfigurationFile) {
    this.elapseTime = elapseTime;
    this.stopFile = stopFile;
    this.waarpConfigurationFile = waarpConfigurationFile;
  }

  private static Options getOptions() {
    Options options = new Options();
    options.addRequiredOption("s", "stopfile", true, "Path of the stop file")
           .addRequiredOption("w", "waarp", true, "Waarp configuration file")
           .addOption(Option.builder("e").longOpt("elapse").hasArg(true)
                            .type(Number.class).desc("Elapse time in seconds")
                            .build());
    Option property = OptionBuilder.withArgName("property=value").hasArgs(2)
                                   .withValueSeparator().withDescription(
            "use value for given property").create("D");
    options.addOption(property);
    return options;
  }

  private static IngestMonitor getIngestMonitor(Options options, String[] args)
      throws ParseException {
    CommandLineParser parser = new DefaultParser();
    CommandLine cmd = parser.parse(options, args);
    final String stopFilePath = cmd.getOptionValue('s');
    final String waarpConfigurationFile = cmd.getOptionValue('w');
    final int elapseInSecond;
    if (cmd.hasOption('e')) {
      final String selapse = cmd.getOptionValue('e');
      try {
        elapseInSecond = Integer.parseInt(selapse);
        if (elapseInSecond < 1) {
          throw new NumberFormatException("Elapse time must be positive");
        }
      } catch (NumberFormatException e) {
        throw new ParseException("Elapse time must be a positive integer");
      }
    } else {
      elapseInSecond = 10;
    }
    if (cmd.hasOption('D')) {
      Properties properties = cmd.getOptionProperties("D");
      baseDir = new File(properties.getProperty(
          IngestRequestFactory.ORG_WAARP_INGEST_BASEDIR,
          IngestRequestFactory.TMP_INGEST_FACTORY));
    }
    return new IngestMonitor(elapseInSecond * 1000, new File(stopFilePath),
                             new File(waarpConfigurationFile));
  }

  public static void main(String[] args) {
    Options options = getOptions();
    HelpFormatter formatter = new HelpFormatter();

    if (args.length == 0) {
      formatter.printHelp(IngestMonitor.class.getSimpleName(), options);
      return;
    }
    final IngestMonitor ingestMonitor;
    try {
      ingestMonitor = getIngestMonitor(options, args);
    } catch (ParseException e) {
      logger.error("Error while initializing {}", IngestMonitor.class.getName(),
                   e);
      formatter.printHelp(IngestMonitor.class.getSimpleName(), options);
      return;
    }
    IngestRequestFactory.setBaseDir(baseDir);
    if (!FileBasedConfiguration
        .setSubmitClientConfigurationFromXml(Configuration.configuration,
                                             ingestMonitor.waarpConfigurationFile
                                                 .getAbsolutePath())) {
      logger.error("Cannot load Waarp Configuration");
      return;
    }
    ingestMonitor.invoke();
  }

  public void invoke() {
    try (IngestExternalClient client = IngestRequestFactory.getInstance()
                                                           .getClient()) {
      logger.info("Start of {}", IngestMonitor.class.getName());
      while (!stopFile.exists()) {
        IngestManager
            .retryAllExistingFiles(IngestRequestFactory.getInstance(), client);
        Thread.sleep(elapseTime);
      }
      logger.info("Stop of {}", IngestMonitor.class.getName());
    } catch (InterruptedException e) {
      logger.error("{} will stop", IngestMonitor.class.getName(), e);
    }
  }
}
