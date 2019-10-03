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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.waarp.common.exception.InvalidArgumentException;
import org.waarp.common.logging.SysErrLogger;
import org.waarp.common.utility.ParametersChecker;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Utility common to Ingest and DIP
 */
public class WaarpCommon {
  public static final String FOR_SIMPLE_MANDATORY_ARGUMENTS =
      "(*) for mandatory arguments";
  public static final String FOR_MANDATORY_ARGUMENTS =
      "(*) for mandatory arguments, (+) except if -o config option";
  public static final String CONFIGURATION_FILE_CANNOT_BE_READ =
      "Configuration file cannot be read: ";
  private static final Option WAARP =
      Option.builder("w").longOpt("waarp").hasArg(true).required(true)
            .desc("(*) Waarp configuration file").build();
  private static final Option WAARP_MODEL =
      Option.builder("m").longOpt("model").hasArg(true).required(false).desc(
          "Waarp model between R66 or a specific script file " +
          "that do send to the partner").build();
  private static final Option WAARP_NR =
      Option.builder("w").longOpt("waarp").hasArg(true).required(false)
            .desc("(*) Waarp configuration file").build();
  private static final Option HELP =
      Option.builder("h").longOpt("help").hasArg(false)
            .desc("Get the corresponding help").build();
  private static final Options HELP_ONLY = new Options().addOption(HELP);

  private WaarpCommon() {
    // Nothing
  }

  /**
   * Check if help is required
   *
   * @param args
   *
   * @return True if help required
   *
   * @throws ParseException
   */
  public static boolean checkHelp(String[] args) {
    CommandLineParser parser = new DefaultParser();
    CommandLine first = null;
    try {
      first = parser.parse(HELP_ONLY, args, true);
      return first.hasOption('h');
    } catch (ParseException ignore) {//NOSONAR
      SysErrLogger.FAKE_LOGGER.ignoreLog(ignore);
    }
    return false;
  }

  /**
   * TaskOption class
   */
  public static class TaskOption {
    private static final Option FILE =
        Option.builder("f").longOpt("file").required(true).hasArg(true)
              .desc("(*) Path of the local file").build();
    private static final Option TENANT =
        Option.builder("t").longOpt("tenant").required(true).hasArg(true)
              .type(Number.class).desc("(+) Tenant Id").build();
    private static final Option ACCESS =
        Option.builder("a").longOpt("access").required(true).hasArg(true)
              .desc("(+) Access Contract").build();
    private static final Option PARTNER =
        Option.builder("p").longOpt("partner").required(true).hasArg(true)
              .desc("(+) Waarp Partner").build();
    private static final Option RULE =
        Option.builder("r").longOpt("rule").required(true).hasArg(true)
              .desc("(+) Waarp Rule").build();
    private static final Option SESSION =
        Option.builder("s").longOpt("session").hasArg(true)
              .desc("Application Session Id").build();
    private static final Option CERTIFICATE =
        Option.builder("c").longOpt("certificate").hasArg(true)
              .desc("Personal Certificate").build();
    private static final Option CONF =
        Option.builder("o").longOpt("conf").hasArg(true).desc(
            "(+) Configuration file containing tenant, access, partner, rule," +
            " waarp, certificate options. " +
            "Any specific options set independently will " +
            "replace the value contained in this file").build();
    private static final Option TENANT_NR =
        Option.builder(TENANT.getOpt()).longOpt(TENANT.getLongOpt())
              .required(false).hasArg(true).type(Number.class)
              .desc(TENANT.getDescription()).build();
    private static final Option ACCESS_NR =
        Option.builder(ACCESS.getOpt()).longOpt(ACCESS.getLongOpt())
              .required(false).hasArg(true).desc(ACCESS.getDescription())
              .build();
    private static final Option PARTNER_NR =
        Option.builder(PARTNER.getOpt()).longOpt(PARTNER.getLongOpt())
              .required(false).hasArg(true).desc(PARTNER.getDescription())
              .build();
    private static final Option RULE_NR =
        Option.builder(RULE.getOpt()).longOpt(RULE.getLongOpt()).required(false)
              .hasArg(true).desc(RULE.getDescription()).build();
    private static final Options STANDARD =
        new Options().addOption(FILE).addOption(SESSION).addOption(CONF)
                     .addOption(CERTIFICATE).addOption(TENANT_NR)
                     .addOption(ACCESS_NR).addOption(PARTNER_NR)
                     .addOption(RULE_NR).addOption(WAARP_MODEL)
                     .addOption(WAARP_NR).addOption(HELP);
    private static final Options MANDATORY_NO_CONF =
        new Options().addOption(FILE).addOption(SESSION).addOption(CONF)
                     .addOption(TENANT).addOption(ACCESS).addOption(CERTIFICATE)
                     .addOption(PARTNER).addOption(RULE).addOption(WAARP_MODEL)
                     .addOption(WAARP);

    private final String waarpConfigurationFile;
    private final String path;
    private final int tenantId;
    private final String applicationSessionId;
    private final String personalCertificate;
    private final String accessContract;
    private final String waarpPartner;
    private final String waarpRule;
    private final String waarpModel;

    public TaskOption(final String waarpConfigurationFile, final String path,
                      final int tenantId, final String applicationSessionId,
                      final String personalCertificate,
                      final String accessContract, final String waarpPartner,
                      final String waarpRule, final String waarpModel)
        throws ParseException {
      try {
        ParametersChecker
            .checkParameter("Arguments should be clean and not null",
                            waarpConfigurationFile, path, accessContract,
                            waarpPartner, waarpRule);
        ParametersChecker
            .checkSanityString(waarpConfigurationFile, path, accessContract,
                               waarpPartner, waarpRule, waarpModel,
                               applicationSessionId, personalCertificate);
        if (tenantId < 0) {
          throw new ParseException("Illegal value");
        }
      } catch (IllegalArgumentException | InvalidArgumentException e) {
        throw new ParseException("Incorrect arguments");
      }
      this.waarpConfigurationFile = waarpConfigurationFile;
      this.path = path;
      this.tenantId = tenantId;
      this.applicationSessionId = applicationSessionId;
      this.personalCertificate = personalCertificate;
      this.accessContract = accessContract;
      this.waarpPartner = waarpPartner;
      this.waarpRule = waarpRule;
      this.waarpModel = waarpModel;
    }

    /**
     * Standard options for Task
     *
     * @param options
     */
    public static void setStandardTaskOptions(Options options) {
      for (Option option : STANDARD.getOptions()) {
        options.addOption(option);
      }
    }

    /**
     * @param cmd
     *
     * @return the TaskOption from CommandLine
     *
     * @throws ParseException
     */
    public static TaskOption getTaskOption(final CommandLine cmd,
                                           final String[] args)
        throws ParseException {
      CommandLineParser parser = new DefaultParser();
      Properties properties = new Properties();
      if (cmd.hasOption('o')) {
        // Use Configuration file
        File conf = new File(cmd.getOptionValue('o'));
        if (!conf.canRead()) {
          throw new ParseException(
              CONFIGURATION_FILE_CANNOT_BE_READ + conf.getAbsolutePath());
        }
        try (InputStream inputStream = new FileInputStream(conf)) {
          properties.load(inputStream);
        } catch (IOException e) {
          throw new ParseException(
              CONFIGURATION_FILE_CANNOT_BE_READ + conf.getAbsolutePath() +
              " since " + e.getMessage());
        }
      } else {
        // Check mandatory parameters if not using Configuration file
        parser.parse(MANDATORY_NO_CONF, args, true);
      }
      final String stenant = properties.getProperty(TENANT.getLongOpt(),
                                                    cmd.getOptionValue(
                                                        TENANT.getOpt()));
      final String accessContract = properties.getProperty(ACCESS.getLongOpt(),
                                                           cmd.getOptionValue(
                                                               ACCESS
                                                                   .getOpt()));
      final String personalCertificate = properties
          .getProperty(CERTIFICATE.getLongOpt(),
                       cmd.getOptionValue(CERTIFICATE.getOpt(), null));
      final String waarpPartner = properties.getProperty(PARTNER.getLongOpt(),
                                                         cmd.getOptionValue(
                                                             PARTNER.getOpt()));
      final String waarpRule = properties
          .getProperty(RULE.getLongOpt(), cmd.getOptionValue(RULE.getOpt()));
      final String waarpConfiguration = properties
          .getProperty(WAARP.getLongOpt(), cmd.getOptionValue(WAARP.getOpt()));
      final String path = cmd.getOptionValue('f');
      final String applicationSessionId;
      final String waarpModel;

      if (cmd.hasOption('m')) {
        String temp = cmd.getOptionValue('m');
        if (temp.isEmpty()) {
          temp = null;
        }
        waarpModel = temp;
      } else {
        waarpModel = null;
      }
      if (cmd.hasOption('s')) {
        String temp = cmd.getOptionValue('s');
        if (temp.isEmpty()) {
          temp = null;
        }
        applicationSessionId = temp;
      } else {
        applicationSessionId = null;
      }
      final int tenantId;
      try {
        tenantId = Integer.parseInt(stenant);
        if (tenantId < 0) {
          throw new NumberFormatException("Tenant Id must be positive");
        }
      } catch (NumberFormatException e) {
        throw new ParseException("Tenant Id must be a positive integer");
      }
      return new TaskOption(waarpConfiguration, path, tenantId,
                            applicationSessionId, personalCertificate,
                            accessContract, waarpPartner, waarpRule,
                            waarpModel);
    }

    public String getWaarpConfigurationFile() {
      return waarpConfigurationFile;
    }

    public String getPath() {
      return path;
    }

    public int getTenantId() {
      return tenantId;
    }

    public String getApplicationSessionId() {
      return applicationSessionId;
    }

    public String getPersonalCertificate() {
      return personalCertificate;
    }

    public String getAccessContract() {
      return accessContract;
    }

    public String getWaarpPartner() {
      return waarpPartner;
    }

    public String getWaarpRule() {
      return waarpRule;
    }

    public String getWaarpModel() {
      return waarpModel;
    }
  }

  /**
   * MonitorOption class
   */
  public static class MonitorOption {
    private final String stopFilePath;
    private final String waarpConfiguration;
    private final int elapseInSecond;

    public MonitorOption(final String stopFilePath,
                         final String waarpConfiguration,
                         final int elapseInSecond) throws ParseException {
      try {
        ParametersChecker
            .checkParameter("Arguments should be clean and not null",
                            stopFilePath, waarpConfiguration);
        ParametersChecker.checkSanityString(stopFilePath, waarpConfiguration);
        if (elapseInSecond < 0) {
          throw new ParseException("Illegal value");
        }
      } catch (IllegalArgumentException | InvalidArgumentException e) {
        throw new ParseException("Incorrect arguments");
      }
      this.stopFilePath = stopFilePath;
      this.waarpConfiguration = waarpConfiguration;
      this.elapseInSecond = elapseInSecond;
    }

    /**
     * Standard options for Monitor
     *
     * @param options
     */
    public static void setStandardMonitorOptions(Options options) {
      options
          .addRequiredOption("s", "stopfile", true, "(*) Path of the stop file")
          .addOption(Option.builder("e").longOpt("elapse").hasArg(true)
                           .type(Number.class).desc("Elapse time in seconds")
                           .build()).addOption(WAARP).addOption(HELP);
    }

    /**
     * Standard options for Monitor
     *
     * @param options
     */
    public static void addRetryMonitorOptions(Options options) {
      options.addOption(
          Option.builder("r").longOpt("retry").hasArg(true).type(Number.class)
                .desc("Retry for pooling operation (default 3)").build())
             .addOption(Option.builder("d").longOpt("delay").hasArg(true)
                              .type(Number.class).desc(
                     "Delay between 2 retries for pooling in ms greater than " +
                     "50 (default 100)").build());
    }

    /**
     * @param cmd
     *
     * @return the MonitorOption from CommandLine
     *
     * @throws ParseException
     */
    public static MonitorOption gestMonitorOption(CommandLine cmd,
                                                  String[] args)
        throws ParseException {
      final String stopFilePath = cmd.getOptionValue('s');
      final String waarpConfiguration = cmd.getOptionValue('w');
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
      int retry = OperationCheck.getRetry();
      int delay = OperationCheck.getDelay();
      if (cmd.hasOption('r')) {
        String sretry = cmd.getOptionValue('r');
        try {
          retry = Integer.parseInt(sretry);
          if (retry < 1) {
            throw new NumberFormatException("Retry must be positive");
          }
        } catch (NumberFormatException e) {
          throw new ParseException("Retry must be a positive integer");
        }
      }
      if (cmd.hasOption('d')) {
        String sdelay = cmd.getOptionValue('d');
        try {
          delay = Integer.parseInt(sdelay);
          if (delay < 50) {
            throw new NumberFormatException("Delay must be greater than 50");
          }
        } catch (NumberFormatException e) {
          throw new ParseException("Delay must be greater than 50");
        }
      }
      OperationCheck.setRetry(retry, delay);
      return new MonitorOption(stopFilePath, waarpConfiguration,
                               elapseInSecond);
    }

    public String getStopFilePath() {
      return stopFilePath;
    }

    public String getWaarpConfiguration() {
      return waarpConfiguration;
    }

    public int getElapseInSecond() {
      return elapseInSecond;
    }
  }

}
