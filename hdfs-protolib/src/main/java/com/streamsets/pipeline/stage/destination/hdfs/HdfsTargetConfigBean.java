/**
 * Copyright 2015 StreamSets Inc.
 *
 * Licensed under the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.pipeline.stage.destination.hdfs;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.google.common.annotations.VisibleForTesting;
import com.streamsets.pipeline.api.ConfigDef;
import com.streamsets.pipeline.api.ConfigDefBean;
import com.streamsets.pipeline.api.ExecutionMode;
import com.streamsets.pipeline.api.Stage;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.api.Target;
import com.streamsets.pipeline.api.ValueChooserModel;
import com.streamsets.pipeline.api.el.ELEval;
import com.streamsets.pipeline.api.el.ELEvalException;
import com.streamsets.pipeline.api.el.ELVars;
import com.streamsets.pipeline.api.el.SdcEL;
import com.streamsets.pipeline.config.DataFormat;
import com.streamsets.pipeline.config.TimeZoneChooserValues;
import com.streamsets.pipeline.lib.el.DataUtilEL;
import com.streamsets.pipeline.lib.el.RecordEL;
import com.streamsets.pipeline.lib.el.TimeEL;
import com.streamsets.pipeline.lib.el.TimeNowEL;
import com.streamsets.pipeline.stage.destination.hdfs.writer.ActiveRecordWriters;
import com.streamsets.pipeline.stage.destination.hdfs.writer.RecordWriterManager;
import com.streamsets.pipeline.stage.destination.lib.DataGeneratorFormatConfig;
import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.CommonConfigurationKeys;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RawLocalFileSystem;
import org.apache.hadoop.hdfs.DFSConfigKeys;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.authentication.util.KerberosUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.Subject;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

public class HdfsTargetConfigBean {

  private static final Logger LOG = LoggerFactory.getLogger(HdfsTargetConfigBean.class);
  private final static int MEGA_BYTE = 1024 * 1024;

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.STRING,
    label = "Hadoop FS URI",
    description = "",
    displayPosition = 10,
    group = "HADOOP_FS"
  )
  public String hdfsUri;

  @ConfigDef(
    required = false,
    type = ConfigDef.Type.STRING,
    label = "HDFS User",
    description = "If set, the data collector will write to HDFS as this user. " +
      "The data collector user must be configured as a proxy user in HDFS.",
    displayPosition = 20,
    group = "HADOOP_FS"
  )
  public String hdfsUser;

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.BOOLEAN,
    label = "Kerberos Authentication",
    defaultValue = "false",
    description = "",
    displayPosition = 30,
    group = "HADOOP_FS"
  )
  public boolean hdfsKerberos;

  @ConfigDef(
    required = false,
    type = ConfigDef.Type.STRING,
    defaultValue = "",
    label = "Hadoop FS Configuration Directory",
    description = "An absolute path or a directory under SDC resources directory to load core-site.xml and hdfs-site.xml files " +
      "to configure the Hadoop FileSystem.",
    displayPosition = 50,
    group = "HADOOP_FS"
  )
  public String hdfsConfDir;

  @ConfigDef(
    required = false,
    type = ConfigDef.Type.MAP,
    label = "Hadoop FS Configuration",
    description = "Additional Hadoop properties to pass to the underlying Hadoop FileSystem. These properties " +
      "have precedence over properties loaded via the 'Hadoop FS Configuration Directory' property.",
    displayPosition = 60,
    group = "HADOOP_FS"
  )
  public Map<String, String> hdfsConfigs;

  @ConfigDef(
    required = false,
    type = ConfigDef.Type.STRING,
    defaultValue = "sdc-${sdc:id()}",
    label = "Files Prefix",
    description = "File name prefix",
    displayPosition = 105,
    group = "OUTPUT_FILES",
    elDefs = SdcEL.class
  )
  public String uniquePrefix;

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.STRING,
    defaultValue = "/tmp/out/${YYYY()}-${MM()}-${DD()}-${hh()}",
    label = "Directory Template",
    description = "Template for the creation of output directories. Valid variables are ${YYYY()}, ${MM()}, ${DD()}, " +
      "${hh()}, ${mm()}, ${ss()} and {record:value(“/field”)} for values in a field. Directories are " +
      "created based on the smallest time unit variable used.",
    displayPosition = 110,
    group = "OUTPUT_FILES",
    elDefs = {RecordEL.class, TimeEL.class, ExtraTimeEL.class},
    evaluation = ConfigDef.Evaluation.EXPLICIT
  )
  public String dirPathTemplate;

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.MODEL,
    defaultValue = "UTC",
    label = "Data Time Zone",
    description = "Time zone to use to resolve directory paths",
    displayPosition = 120,
    group = "OUTPUT_FILES"
  )
  @ValueChooserModel(TimeZoneChooserValues.class)
  public String timeZoneID;

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.STRING,
    defaultValue = "${time:now()}",
    label = "Time Basis",
    description = "Time basis to use for a record. Enter an expression that evaluates to a datetime. To use the " +
      "processing time, enter ${time:now()}. To use field values, use '${record:value(\"<filepath>\")}'.",
    displayPosition = 130,
    group = "OUTPUT_FILES",
    elDefs = {RecordEL.class, TimeEL.class, TimeNowEL.class},
    evaluation = ConfigDef.Evaluation.EXPLICIT
  )
  public String timeDriver;

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.NUMBER,
    defaultValue = "0",
    label = "Max Records in File",
    description = "Number of records that triggers the creation of a new file. Use 0 to opt out.",
    displayPosition = 140,
    group = "OUTPUT_FILES",
    min = 0
  )
  public long maxRecordsPerFile;

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.NUMBER,
    defaultValue = "0",
    label = "Max File Size (MB)",
    description = "Exceeding this size triggers the creation of a new file. Use 0 to opt out.",
    displayPosition = 150,
    group = "OUTPUT_FILES",
    min = 0
  )
  public long maxFileSize;

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.MODEL,
    defaultValue = "NONE",
    label = "Compression Codec",
    description = "",
    displayPosition = 160,
    group = "OUTPUT_FILES"
  )
  @ValueChooserModel(CompressionChooserValues.class)
  public CompressionMode compression;

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.STRING,
    defaultValue = "",
    label = "Compression Codec Class",
    description = "Use the full class name",
    displayPosition = 170,
    group = "OUTPUT_FILES",
    dependsOn = "compression",
    triggeredByValue = "OTHER"
  )

  public String otherCompression;

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.MODEL,
    defaultValue = "TEXT",
    label = "File Type",
    description = "",
    displayPosition = 100,
    group = "OUTPUT_FILES"
  )
  @ValueChooserModel(FileTypeChooserValues.class)
  public HdfsFileType fileType;

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.STRING,
    defaultValue = "${uuid()}",
    label = "Sequence File Key",
    description = "Record key for creating Hadoop sequence files. Valid options are " +
      "'${record:value(\"<field-path>\")}' or '${uuid()}'",
    displayPosition = 180,
    group = "OUTPUT_FILES",
    dependsOn = "fileType",
    triggeredByValue = "SEQUENCE_FILE",
    elDefs = {RecordEL.class, DataUtilEL.class}
  )
  public String keyEl;

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.MODEL,
    defaultValue = "BLOCK",
    label = "Compression Type",
    description = "Compression type if using a CompressionCodec",
    displayPosition = 190,
    group = "OUTPUT_FILES",
    dependsOn = "fileType",
    triggeredByValue = "SEQUENCE_FILE"
  )
  @ValueChooserModel(HdfsSequenceFileCompressionTypeChooserValues.class)
  public HdfsSequenceFileCompressionType seqFileCompressionType;

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.STRING,
    defaultValue = "${1 * HOURS}",
    label = "Late Record Time Limit (secs)",
    description = "Time limit (in seconds) for a record to be written to the corresponding HDFS directory, if the " +
      "limit is exceeded the record will be written to the current late records file. 0 means no limit. " +
      "If a number is used it is considered seconds, it can be multiplied by 'MINUTES' or 'HOURS', ie: " +
      "'${30 * MINUTES}'",
    displayPosition = 200,
    group = "LATE_RECORDS",
    elDefs = {TimeEL.class},
    evaluation = ConfigDef.Evaluation.EXPLICIT
  )
  public String lateRecordsLimit;

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.MODEL,
    defaultValue = "SEND_TO_ERROR",
    label = "Late Record Handling",
    description = "Action for records considered late.",
    displayPosition = 210,
    group = "LATE_RECORDS"
  )
  @ValueChooserModel(LateRecordsActionChooserValues.class)
  public LateRecordsAction lateRecordsAction;

  @ConfigDef(
    required = false,
    type = ConfigDef.Type.STRING,
    defaultValue = "/tmp/late/${YYYY()}-${MM()}-${DD()}",
    label = "Late Record Directory Template",
    description = "Template for the creation of late record directories. Valid variables are ${YYYY()}, ${MM()}, " +
      "${DD()}, ${hh()}, ${mm()}, ${ss()}.",
    displayPosition = 220,
    group = "LATE_RECORDS",
    dependsOn = "lateRecordsAction",
    triggeredByValue = "SEND_TO_LATE_RECORDS_FILE",
    elDefs = {RecordEL.class, TimeEL.class},
    evaluation = ConfigDef.Evaluation.EXPLICIT
  )
  public String lateRecordsDirPathTemplate;

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.MODEL,
    defaultValue = "JSON",
    label = "Data Format",
    description = "Data Format",
    displayPosition = 100,
    group = "OUTPUT_FILES",
    dependsOn = "fileType",
    triggeredByValue = { "TEXT", "SEQUENCE_FILE"}
  )
  @ValueChooserModel(DataFormatChooserValues.class)
  public DataFormat dataFormat;

  @ConfigDefBean()
  public DataGeneratorFormatConfig dataGeneratorFormatConfig;

  //private members

  private Configuration hdfsConfiguration;
  private UserGroupInformation loginUgi;
  private long lateRecordsLimitSecs;
  private ActiveRecordWriters currentWriters;
  private ActiveRecordWriters lateWriters;
  private ELEval timeDriverElEval;
  private ELEval lateRecordsLimitEvaluator;
  private CompressionCodec compressionCodec;
  private Counter toHdfsRecordsCounter;
  private Meter toHdfsRecordsMeter;
  private Counter lateRecordsCounter;
  private Meter lateRecordsMeter;

  //public API

  public void init(Stage.Context context, List<Stage.ConfigIssue> issues) {

    boolean validHadoopDir = false;
    if (validateHadoopFS(context, issues)) {
      validHadoopDir = validateHadoopDir(context, "dirPathTemplate", dirPathTemplate, issues);
      if (lateRecordsAction == LateRecordsAction.SEND_TO_LATE_RECORDS_FILE &&
          lateRecordsDirPathTemplate != null && !lateRecordsDirPathTemplate.isEmpty()) {
        validHadoopDir &= validateHadoopDir(context, "lateRecordsDirPathTemplate", lateRecordsDirPathTemplate, issues);
      }
    }
    try {
      lateRecordsLimitEvaluator = context.createELEval("lateRecordsLimit");
      context.parseEL(lateRecordsLimit);
      lateRecordsLimitSecs = lateRecordsLimitEvaluator.eval(context.createELVars(),
        lateRecordsLimit, Long.class);
      if (lateRecordsLimitSecs <= 0) {
        issues.add(context.createConfigIssue(Groups.LATE_RECORDS.name(), "lateRecordsLimit", Errors.HADOOPFS_10));
      }
    } catch (Exception ex) {
      issues.add(context.createConfigIssue(Groups.LATE_RECORDS.name(), "lateRecordsLimit", Errors.HADOOPFS_06,
        lateRecordsLimit, ex.toString(), ex));
    }
    if (maxFileSize < 0) {
      issues.add(context.createConfigIssue(Groups.LATE_RECORDS.name(), "maxFileSize", Errors.HADOOPFS_08));
    }

    if (maxRecordsPerFile < 0) {
      issues.add(context.createConfigIssue(Groups.LATE_RECORDS.name(), "maxRecordsPerFile", Errors.HADOOPFS_09));
    }

    if (uniquePrefix == null) {
      uniquePrefix = "";
    }

    dataGeneratorFormatConfig.init(
        context,
        dataFormat,
        Groups.OUTPUT_FILES.name(),
        "dataGeneratorFormatConfig",
        issues
    );

    SequenceFile.CompressionType compressionType = (seqFileCompressionType != null)
      ? seqFileCompressionType.getType() : null;
    try {
      switch (compression) {
        case OTHER:
          try {
            Class klass = Thread.currentThread().getContextClassLoader().loadClass(otherCompression);
            if (CompressionCodec.class.isAssignableFrom(klass)) {
              compressionCodec = ((Class<? extends CompressionCodec> ) klass).newInstance();
            } else {
              throw new StageException(Errors.HADOOPFS_04, otherCompression);
            }
          } catch (Exception ex1) {
            throw new StageException(Errors.HADOOPFS_05, otherCompression, ex1.toString(), ex1);
          }
          break;
        case NONE:
          break;
        default:
          compressionCodec = compression.getCodec().newInstance();
          break;
      }
      if (compressionCodec != null) {
        if (compressionCodec instanceof Configurable) {
          ((Configurable) compressionCodec).setConf(hdfsConfiguration);
        }
      }
      if(validHadoopDir) {
        RecordWriterManager mgr = new RecordWriterManager(new URI(hdfsUri), hdfsConfiguration, uniquePrefix,
          dirPathTemplate, TimeZone.getTimeZone(timeZoneID), lateRecordsLimitSecs, maxFileSize * MEGA_BYTE,
          maxRecordsPerFile, fileType, compressionCodec, compressionType, keyEl,
          dataGeneratorFormatConfig.getDataGeneratorFactory(), (Target.Context) context, "dirPathTemplate");

        if (mgr.validateDirTemplate(Groups.OUTPUT_FILES.name(), "dirPathTemplate", issues)) {
          currentWriters = new ActiveRecordWriters(mgr);
        }
      }
    } catch (Exception ex) {
      LOG.info("Validation Error: " + Errors.HADOOPFS_11.getMessage(), ex.toString(), ex);
      issues.add(context.createConfigIssue(Groups.OUTPUT_FILES.name(), null, Errors.HADOOPFS_11, ex.toString(),
        ex));
    }

    if (lateRecordsDirPathTemplate != null && !lateRecordsDirPathTemplate.isEmpty()) {
      if(validHadoopDir) {
        try {
          RecordWriterManager mgr = new RecordWriterManager(
              new URI(hdfsUri),
              hdfsConfiguration,
              uniquePrefix,
              lateRecordsDirPathTemplate,
              TimeZone.getTimeZone(timeZoneID),
              lateRecordsLimitSecs,
              maxFileSize * MEGA_BYTE,
              maxRecordsPerFile,
              fileType,
              compressionCodec,
              compressionType,
              keyEl,
              dataGeneratorFormatConfig.getDataGeneratorFactory(),
              (Target.Context)context, "lateRecordsDirPathTemplate"
          );

          if (mgr.validateDirTemplate(Groups.OUTPUT_FILES.name(), "lateRecordsDirPathTemplate", issues)) {
            lateWriters = new ActiveRecordWriters(mgr);
          }
        } catch (Exception ex) {
          issues.add(context.createConfigIssue(Groups.LATE_RECORDS.name(), null, Errors.HADOOPFS_17,
            ex.toString(), ex));
        }
      }
    }

    timeDriverElEval = context.createELEval("timeDriver");
    try {
      ELVars variables = context.createELVars();
      RecordEL.setRecordInContext(variables, context.createRecord("validationConfigs"));
      TimeNowEL.setTimeNowInContext(variables, new Date());
      context.parseEL(timeDriver);
      timeDriverElEval.eval(variables, timeDriver, Date.class);
    } catch (ELEvalException ex) {
      issues.add(context.createConfigIssue(Groups.OUTPUT_FILES.name(), "timeDriver", Errors.HADOOPFS_19,
        ex.toString(), ex));
    }

    if (issues.isEmpty()) {
      try {
        FileSystem fs = getFileSystemForInitDestroy();
        getCurrentWriters().commitOldFiles(fs);
        if (getLateWriters() != null) {
          getLateWriters().commitOldFiles(fs);
        }
      } catch (Exception ex) {
        issues.add(context.createConfigIssue(null, null, Errors.HADOOPFS_23, ex.toString(), ex));
      }
      toHdfsRecordsCounter = context.createCounter("toHdfsRecords");
      toHdfsRecordsMeter = context.createMeter("toHdfsRecords");
      lateRecordsCounter = context.createCounter("lateRecords");
      lateRecordsMeter = context.createMeter("lateRecords");
    }
  }

  public void destroy() {
    LOG.info("Destroy");
    try {
      if (currentWriters != null) {
        currentWriters.closeAll();
      }
      if (lateWriters != null) {
        lateWriters.closeAll();
      }
      if (loginUgi != null) {
        getFileSystemForInitDestroy().close();
      }
    } catch (Exception ex) {
      LOG.warn("Error while closing HDFS FileSystem URI='{}': {}", hdfsUri, ex.toString(), ex);
    }
  }

  Counter getToHdfsRecordsCounter() {
    return toHdfsRecordsCounter;
  }

  Meter getToHdfsRecordsMeter() {
    return toHdfsRecordsMeter;
  }

  Counter getLateRecordsCounter() {
    return lateRecordsCounter;
  }

  Meter getLateRecordsMeter() {
    return lateRecordsMeter;
  }

  String getTimeDriver() {
    return timeDriver;
  }

  ELEval getTimeDriverElEval() {
    return timeDriverElEval;
  }

  UserGroupInformation getUGI() {
    return (hdfsUser.isEmpty()) ? loginUgi : UserGroupInformation.createProxyUser(hdfsUser, loginUgi);
  }

  protected ActiveRecordWriters getCurrentWriters() {
    return currentWriters;
  }

  protected ActiveRecordWriters getLateWriters() {
    return lateWriters;
  }

  @VisibleForTesting
  Configuration getHdfsConfiguration() {
    return hdfsConfiguration;
  }

  @VisibleForTesting
  CompressionCodec getCompressionCodec() throws StageException {
    return compressionCodec;
  }

  @VisibleForTesting
  long getLateRecordLimitSecs() {
    return lateRecordsLimitSecs;
  }

  //private implementation

  private Configuration getHadoopConfiguration(Stage.Context context, List<Stage.ConfigIssue> issues) {
    Configuration conf = new Configuration();
    conf.setClass("fs.file.impl", RawLocalFileSystem.class, FileSystem.class);
    if (hdfsKerberos) {
      conf.set(CommonConfigurationKeys.HADOOP_SECURITY_AUTHENTICATION,
        UserGroupInformation.AuthenticationMethod.KERBEROS.name());
      try {
        conf.set(DFSConfigKeys.DFS_NAMENODE_USER_NAME_KEY, "hdfs/_HOST@" + KerberosUtil.getDefaultRealm());
      } catch (Exception ex) {
        if (!hdfsConfigs.containsKey(DFSConfigKeys.DFS_NAMENODE_USER_NAME_KEY)) {
          issues.add(context.createConfigIssue(Groups.HADOOP_FS.name(), null, Errors.HADOOPFS_28,
            ex.toString()));
        }
      }
    }
    if (hdfsConfDir != null && !hdfsConfDir.isEmpty()) {
      File hadoopConfigDir = new File(hdfsConfDir);
      if ((context.getExecutionMode() == ExecutionMode.CLUSTER_BATCH ||
        context.getExecutionMode() == ExecutionMode.CLUSTER_YARN_STREAMING ||
        context.getExecutionMode() == ExecutionMode.CLUSTER_MESOS_STREAMING) &&
        hadoopConfigDir.isAbsolute()
        ) {
        //Do not allow absolute hadoop config directory in cluster mode
        issues.add(context.createConfigIssue(Groups.HADOOP_FS.name(), "hdfsConfDir", Errors.HADOOPFS_45,
          hdfsConfDir));
      } else {
        if (!hadoopConfigDir.isAbsolute()) {
          hadoopConfigDir = new File(context.getResourcesDirectory(), hdfsConfDir).getAbsoluteFile();
        }
        if (!hadoopConfigDir.exists()) {
          issues.add(context.createConfigIssue(Groups.HADOOP_FS.name(), "hdfsConfDir", Errors.HADOOPFS_25,
            hadoopConfigDir.getPath()));
        } else if (!hadoopConfigDir.isDirectory()) {
          issues.add(context.createConfigIssue(Groups.HADOOP_FS.name(), "hdfsConfDir", Errors.HADOOPFS_26,
            hadoopConfigDir.getPath()));
        } else {
          File coreSite = new File(hadoopConfigDir, "core-site.xml");
          if (coreSite.exists()) {
            if (!coreSite.isFile()) {
              issues.add(context.createConfigIssue(Groups.HADOOP_FS.name(), "hdfsConfDir", Errors.HADOOPFS_27,
                coreSite.getPath()));
            }
            conf.addResource(new Path(coreSite.getAbsolutePath()));
          }
          File hdfsSite = new File(hadoopConfigDir, "hdfs-site.xml");
          if (hdfsSite.exists()) {
            if (!hdfsSite.isFile()) {
              issues.add(context.createConfigIssue(Groups.HADOOP_FS.name(), "hdfsConfDir", Errors.HADOOPFS_27,
                hdfsSite.getPath()));
            }
            conf.addResource(new Path(hdfsSite.getAbsolutePath()));
          }
        }
      }
    }
    for (Map.Entry<String, String> config : hdfsConfigs.entrySet()) {
      conf.set(config.getKey(), config.getValue());
    }
    return conf;
  }

  private boolean validateHadoopFS(Stage.Context context, List<Stage.ConfigIssue> issues) {
    boolean validHapoopFsUri = true;
    if (hdfsUri.contains("://")) {
      try {
        new URI(hdfsUri);
      } catch (Exception ex) {
        issues.add(context.createConfigIssue(Groups.HADOOP_FS.name(), null, Errors.HADOOPFS_22, hdfsUri,
          ex.toString(), ex));
        validHapoopFsUri = false;
      }
    } else {
      issues.add(context.createConfigIssue(Groups.HADOOP_FS.name(), "hdfsUri", Errors.HADOOPFS_18, hdfsUri));
      validHapoopFsUri = false;
    }

    StringBuilder logMessage = new StringBuilder();
    try {
      hdfsConfiguration = getHadoopConfiguration(context, issues);

      hdfsConfiguration.set(CommonConfigurationKeys.FS_DEFAULT_NAME_KEY, hdfsUri);

      // forcing UGI to initialize with the security settings from the stage
      UserGroupInformation.setConfiguration(hdfsConfiguration);
      Subject subject = Subject.getSubject(AccessController.getContext());
      if (UserGroupInformation.isSecurityEnabled()) {
        loginUgi = UserGroupInformation.getUGIFromSubject(subject);
      } else {
        UserGroupInformation.loginUserFromSubject(subject);
        loginUgi = UserGroupInformation.getLoginUser();
      }
      LOG.info("Subject = {}, Principals = {}, Login UGI = {}", subject,
        subject == null ? "null" : subject.getPrincipals(), loginUgi);
      if (hdfsKerberos) {
        logMessage.append("Using Kerberos");
        if (loginUgi.getAuthenticationMethod() != UserGroupInformation.AuthenticationMethod.KERBEROS) {
          issues.add(context.createConfigIssue(Groups.HADOOP_FS.name(), "hdfsKerberos", Errors.HADOOPFS_00,
            loginUgi.getAuthenticationMethod(),
            UserGroupInformation.AuthenticationMethod.KERBEROS));
        }
      } else {
        logMessage.append("Using Simple");
        hdfsConfiguration.set(CommonConfigurationKeys.HADOOP_SECURITY_AUTHENTICATION,
          UserGroupInformation.AuthenticationMethod.SIMPLE.name());
      }
      if (validHapoopFsUri) {
        getUGI().doAs(new PrivilegedExceptionAction<Void>() {
          @Override
          public Void run() throws Exception {
            try (FileSystem fs = getFileSystemForInitDestroy()) { //to trigger the close
            }
            return null;
          }
        });
      }
    } catch (Exception ex) {
      LOG.info("Validation Error: " + Errors.HADOOPFS_01.getMessage(), hdfsUri, ex.toString(), ex);
      issues.add(context.createConfigIssue(Groups.HADOOP_FS.name(), null, Errors.HADOOPFS_01, hdfsUri,
        String.valueOf(ex), ex));
    }
    LOG.info("Authentication Config: " + logMessage);
    return validHapoopFsUri;
  }

  private boolean validateHadoopDir(Stage.Context context, String configName, String dirPathTemplate,
                            List<Stage.ConfigIssue> issues) {
    boolean ok;
    if (!dirPathTemplate.startsWith("/")) {
      issues.add(context.createConfigIssue(Groups.HADOOP_FS.name(), configName, Errors.HADOOPFS_40));
      ok = false;
    } else {
      int firstEL = dirPathTemplate.indexOf("$");
      if (firstEL > -1) {
        int lastDir = dirPathTemplate.lastIndexOf("/", firstEL);
        dirPathTemplate = dirPathTemplate.substring(0, lastDir);
      }
      dirPathTemplate = (dirPathTemplate.isEmpty()) ? "/" : dirPathTemplate;
      try {
        Path dir = new Path(dirPathTemplate);
        FileSystem fs = getFileSystemForInitDestroy();
        if (!fs.exists(dir)) {
          try {
            if (fs.mkdirs(dir)) {
              ok = true;
            } else {
              issues.add(context.createConfigIssue(Groups.HADOOP_FS.name(), configName, Errors.HADOOPFS_41));
              ok = false;
            }
          } catch (IOException ex) {
            issues.add(context.createConfigIssue(Groups.HADOOP_FS.name(), configName, Errors.HADOOPFS_42,
              ex.toString()));
            ok = false;
          }
        } else {
          try {
            Path dummy = new Path(dir, "_sdc-dummy-" + UUID.randomUUID().toString());
            fs.create(dummy).close();
            fs.delete(dummy, false);
            ok = true;
          } catch (IOException ex) {
            issues.add(context.createConfigIssue(Groups.HADOOP_FS.name(), configName, Errors.HADOOPFS_43,
              ex.toString()));
            ok = false;
          }
        }
      } catch (Exception ex) {
        LOG.info("Validation Error: " + Errors.HADOOPFS_44.getMessage(), ex.toString(), ex);
        issues.add(context.createConfigIssue(Groups.HADOOP_FS.name(), configName, Errors.HADOOPFS_44,
          ex.toString()));
        ok = false;
      }
    }
    return ok;
  }

  private FileSystem getFileSystemForInitDestroy() throws Exception {
    try {
      return getUGI().doAs(new PrivilegedExceptionAction<FileSystem>() {
        @Override
        public FileSystem run() throws Exception {
          return FileSystem.get(new URI(hdfsUri), hdfsConfiguration);
        }
      });
    } catch (IOException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      Throwable cause = ex.getCause();
      if (cause instanceof Exception) {
        throw (Exception)cause;
      }
      throw ex;
    }
  }

}
