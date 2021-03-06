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
package com.streamsets.pipeline.stage.origin.kafka;

import com.streamsets.pipeline.api.ExecutionMode;
import com.streamsets.pipeline.api.ListBeanModel;
import com.streamsets.pipeline.api.ConfigDef;
import com.streamsets.pipeline.api.ConfigGroups;
import com.streamsets.pipeline.api.ErrorListener;
import com.streamsets.pipeline.api.GenerateResourceBundle;
import com.streamsets.pipeline.api.RawSource;
import com.streamsets.pipeline.api.Source;
import com.streamsets.pipeline.api.StageDef;
import com.streamsets.pipeline.api.ValueChooserModel;
import com.streamsets.pipeline.api.impl.ClusterSource;
import com.streamsets.pipeline.config.CharsetChooserValues;
import com.streamsets.pipeline.config.CsvHeader;
import com.streamsets.pipeline.config.CsvHeaderChooserValues;
import com.streamsets.pipeline.config.CsvMode;
import com.streamsets.pipeline.config.CsvModeChooserValues;
import com.streamsets.pipeline.config.CsvRecordType;
import com.streamsets.pipeline.config.CsvRecordTypeChooserValues;
import com.streamsets.pipeline.config.DataFormat;
import com.streamsets.pipeline.config.DataFormatChooserValues;
import com.streamsets.pipeline.config.JsonMode;
import com.streamsets.pipeline.config.JsonModeChooserValues;
import com.streamsets.pipeline.config.LogMode;
import com.streamsets.pipeline.config.LogModeChooserValues;
import com.streamsets.pipeline.config.OnParseError;
import com.streamsets.pipeline.config.OnParseErrorChooserValues;
import com.streamsets.pipeline.configurablestage.DClusterSourceOffsetCommitter;
import com.streamsets.pipeline.lib.parser.log.RegExConfig;

import java.util.List;
import java.util.Map;

@StageDef(
  version = 2,
  label = "Kafka Consumer",
  description = "Reads data from Kafka",
  execution = {ExecutionMode.CLUSTER_YARN_STREAMING, ExecutionMode.CLUSTER_MESOS_STREAMING, ExecutionMode.STANDALONE},
  libJarsRegex = {"spark-streaming-kafka.*"},
  icon = "kafka.png",
  recordsByRef = true,
  upgrader = KafkaSourceUpgrader.class
)
@RawSource(rawSourcePreviewer = KafkaRawSourcePreviewer.class, mimeType = "*/*")
@ConfigGroups(value = Groups.class)
@GenerateResourceBundle
public class KafkaDSource extends DClusterSourceOffsetCommitter implements ErrorListener {

  //2 info required for spark streaming to create direct stream
  public static final String METADATA_BROKER_LIST= "metadataBrokerList";
  //For now assuming only one topic will be there
  public static final String TOPIC = "topic";
  private DelegatingKafkaSource delegatingKafkaSource;

  @ConfigDef(
    required = false,
    type = ConfigDef.Type.STRING,
    defaultValue = "localhost:9092",
    label = "Broker URI",
    description = "Comma-separated list of Kafka brokers. Use format <HOST>:<PORT>",
    displayPosition = 10,
    group = "KAFKA"
  )
  public String metadataBrokerList;

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.STRING,
    defaultValue = "localhost:2181",
    label = "ZooKeeper URI",
    description = "Comma-separated list of ZooKeepers followed by optional chroot path. Use format: <HOST1>:<PORT1>,<HOST2>:<PORT2>,<HOST3>:<PORT3>/<ital><CHROOT_PATH></ital>",
    displayPosition = 10,
    group = "KAFKA"
  )
  public String zookeeperConnect;

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.STRING,
    defaultValue = "streamsetsDataCollector",
    label = "Consumer Group",
    displayPosition = 20,
    group = "KAFKA"
  )
  public String consumerGroup;

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.STRING,
    defaultValue = "topicName",
    label = "Topic",
    displayPosition = 30,
    group = "KAFKA"
  )
  public String topic;

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.MODEL,
    label = "Data Format",
    displayPosition = 40,
    group = "KAFKA"
  )
  @ValueChooserModel(DataFormatChooserValues.class)
  public DataFormat dataFormat;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.MODEL,
      defaultValue = "UTF-8",
      label = "Messages Charset",
      displayPosition = 42,
      group = "KAFKA",
      dependsOn = "dataFormat",
      triggeredByValue = {"TEXT", "JSON", "DELIMITED", "XML", "LOG"}
  )
  @ValueChooserModel(CharsetChooserValues.class)
  public String charset;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.BOOLEAN,
      defaultValue = "false",
      label = "Ignore Control Characters",
      description = "Use only if required as it impacts reading performance",
      displayPosition = 43,
      group = "KAFKA"
  )
  public boolean removeCtrlChars;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.BOOLEAN,
      defaultValue = "false",
      label = "Produce Single Record",
      description = "Generates a single record for multiple objects within a message",
      displayPosition = 45,
      group = "KAFKA"
  )
  public boolean produceSingleRecordPerMessage;

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.NUMBER,
    defaultValue = "1000",
    label = "Max Batch Size (records)",
    description = "Max number of records per batch",
    displayPosition = 50,
    group = "KAFKA",
    min = 1,
    max = Integer.MAX_VALUE
  )
  public int maxBatchSize;

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.NUMBER,
    defaultValue = "2000",
    label = "Batch Wait Time (ms)",
    description = "Max time to wait for data before sending a partial or empty batch",
    displayPosition = 60,
    group = "KAFKA",
    min = 1,
    max = Integer.MAX_VALUE
  )
  public int maxWaitTime;

  @ConfigDef(
    required = false,
    type = ConfigDef.Type.MAP,
    defaultValue = "",
    label = "Kafka Configuration",
    description = "Additional Kafka properties to pass to the underlying Kafka consumer",
    displayPosition = 70,
    group = "KAFKA"
  )
  public Map<String, String> kafkaConsumerConfigs;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.NUMBER,
      defaultValue = "1024",
      label = "Max Line Length",
      description = "Longer lines are truncated",
      displayPosition = 100,
      group = "TEXT",
      dependsOn = "dataFormat",
      triggeredByValue = "TEXT",
      min = 1,
      max = Integer.MAX_VALUE
  )
  public int textMaxLineLen;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.MODEL,
      defaultValue = "MULTIPLE_OBJECTS",
      label = "JSON Content",
      description = "",
      displayPosition = 100,
      group = "JSON",
      dependsOn = "dataFormat",
      triggeredByValue = "JSON"
  )
  @ValueChooserModel(JsonModeChooserValues.class)
  public JsonMode jsonContent;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.NUMBER,
      defaultValue = "4096",
      label = "Max Object Length (chars)",
      description = "Larger objects are not processed",
      displayPosition = 110,
      group = "JSON",
      dependsOn = "dataFormat",
      triggeredByValue = "JSON",
      min = 1,
      max = Integer.MAX_VALUE
  )
  public int jsonMaxObjectLen;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.MODEL,
      defaultValue = "CSV",
      label = "Delimiter Format Type",
      description = "",
      displayPosition = 200,
      group = "DELIMITED",
      dependsOn = "dataFormat",
      triggeredByValue = "DELIMITED"
  )
  @ValueChooserModel(CsvModeChooserValues.class)
  public CsvMode csvFileFormat;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.MODEL,
      defaultValue = "NO_HEADER",
      label = "Header Line",
      description = "",
      displayPosition = 210,
      group = "DELIMITED",
      dependsOn = "dataFormat",
      triggeredByValue = "DELIMITED"
  )
  @ValueChooserModel(CsvHeaderChooserValues.class)
  public CsvHeader csvHeader;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.NUMBER,
      defaultValue = "1024",
      label = "Max Record Length (chars)",
      description = "Larger objects are not processed",
      displayPosition = 220,
      group = "DELIMITED",
      dependsOn = "dataFormat",
      triggeredByValue = "DELIMITED",
      min = 1,
      max = Integer.MAX_VALUE
  )
  public int csvMaxObjectLen;

  @ConfigDef(
    required = false,
    type = ConfigDef.Type.CHARACTER,
    defaultValue = "|",
    label = "Delimiter Character",
    displayPosition = 330,
    group = "DELIMITED",
    dependsOn = "csvFileFormat",
    triggeredByValue = "CUSTOM"
  )
  public char csvCustomDelimiter;

  @ConfigDef(
    required = false,
    type = ConfigDef.Type.CHARACTER,
    defaultValue = "\\",
    label = "Escape Character",
    displayPosition = 340,
    group = "DELIMITED",
    dependsOn = "csvFileFormat",
    triggeredByValue = "CUSTOM"
  )
  public char csvCustomEscape;

  @ConfigDef(
    required = false,
    type = ConfigDef.Type.CHARACTER,
    defaultValue = "\"",
    label = "Quote Character",
    displayPosition = 350,
    group = "DELIMITED",
    dependsOn = "csvFileFormat",
    triggeredByValue = "CUSTOM"
  )
  public char csvCustomQuote;

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.MODEL,
    defaultValue = "LIST_MAP",
    label = "Record Type",
    description = "",
    displayPosition = 310,
    group = "DELIMITED",
    dependsOn = "dataFormat",
    triggeredByValue = "DELIMITED"
  )
  @ValueChooserModel(CsvRecordTypeChooserValues.class)
  public CsvRecordType csvRecordType;

  @ConfigDef(
      required = false,
      type = ConfigDef.Type.STRING,
      label = "Delimiter Element",
      defaultValue = "",
      description = "XML element that acts as a record delimiter. No delimiter will treat the whole XML document as one record.",
      displayPosition = 300,
      group = "XML",
      dependsOn = "dataFormat",
      triggeredByValue = "XML"
  )
  public String xmlRecordElement;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.NUMBER,
      defaultValue = "4096",
      label = "Max Record Length (chars)",
      description = "Larger records are not processed",
      displayPosition = 310,
      group = "XML",
      dependsOn = "dataFormat",
      triggeredByValue = "XML",
      min = 1,
      max = Integer.MAX_VALUE
  )
  public int xmlMaxObjectLen;

  // LOG Configuration

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.MODEL,
    defaultValue = "COMMON_LOG_FORMAT",
    label = "Log Format",
    description = "",
    displayPosition = 700,
    group = "LOG",
    dependsOn = "dataFormat",
    triggeredByValue = "LOG"
  )
  @ValueChooserModel(LogModeChooserValues.class)
  public LogMode logMode;

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.NUMBER,
    defaultValue = "1024",
    label = "Max Line Length",
    description = "Longer lines are truncated",
    displayPosition = 710,
    group = "LOG",
    dependsOn = "dataFormat",
    triggeredByValue = "LOG",
    min = 1,
    max = Integer.MAX_VALUE
  )
  public int logMaxObjectLen;

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.BOOLEAN,
    defaultValue = "false",
    label = "Retain Original Line",
    description = "Indicates if the original line of log should be retained in the record",
    displayPosition = 720,
    group = "LOG",
    dependsOn = "dataFormat",
    triggeredByValue = "LOG"
  )
  public boolean retainOriginalLine;

  //APACHE_CUSTOM_LOG_FORMAT
  @ConfigDef(
    required = true,
    type = ConfigDef.Type.STRING,
    defaultValue = "%h %l %u %t \"%r\" %>s %b",
    label = "Custom Log Format",
    description = "",
    displayPosition = 730,
    group = "LOG",
    dependsOn = "logMode",
    triggeredByValue = "APACHE_CUSTOM_LOG_FORMAT"
  )
  public String customLogFormat;

  //REGEX

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.STRING,
    defaultValue = "^(\\S+) (\\S+) (\\S+) \\[([\\w:/]+\\s[+\\-]\\d{4})\\] \"(\\S+) (\\S+) (\\S+)\" (\\d{3}) (\\d+)",
    label = "Regular Expression",
    description = "The regular expression which is used to parse the log line.",
    displayPosition = 740,
    group = "LOG",
    dependsOn = "logMode",
    triggeredByValue = "REGEX"
  )
  public String regex;

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.MODEL,
    defaultValue = "",
    label = "Field Path To RegEx Group Mapping",
    description = "Map groups in the regular expression to field paths",
    displayPosition = 750,
    group = "LOG",
    dependsOn = "logMode",
    triggeredByValue = "REGEX"
  )
  @ListBeanModel
  public List<RegExConfig> fieldPathsToGroupName;

  //GROK

  @ConfigDef(
    required = false,
    type = ConfigDef.Type.TEXT,
    defaultValue = "",
    label = "Grok Pattern Definition",
    description = "Define your own grok patterns which will be used to parse the logs",
    displayPosition = 760,
    group = "LOG",
    dependsOn = "logMode",
    triggeredByValue = "GROK",
    mode = ConfigDef.Mode.PLAIN_TEXT
  )
  public String grokPatternDefinition;

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.STRING,
    defaultValue = "%{COMMONAPACHELOG}",
    label = "Grok Pattern",
    description = "The grok pattern which is used to parse the log line",
    displayPosition = 780,
    group = "LOG",
    dependsOn = "logMode",
    triggeredByValue = "GROK"
  )
  public String grokPattern;

  //LOG4J

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.MODEL,
    defaultValue = "ERROR",
    label = "On Parse Error",
    description = "",
    displayPosition = 790,
    group = "LOG",
    dependsOn = "logMode",
    triggeredByValue = "LOG4J"
  )
  @ValueChooserModel(OnParseErrorChooserValues.class)
  public OnParseError onParseError;

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.NUMBER,
    defaultValue = "50",
    label = "Trim Stack Trace to Length",
    description = "Any line that does not match the expected pattern will be treated as a Stack trace as long as it " +
      "is part of the same message. The stack trace will be trimmed to the specified number of lines.",
    displayPosition = 800,
    group = "LOG",
    dependsOn = "onParseError",
    triggeredByValue = "INCLUDE_AS_STACK_TRACE",
    min = 0,
    max = Integer.MAX_VALUE
  )
  public int maxStackTraceLines;

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.BOOLEAN,
    defaultValue = "false",
    label = "Use Custom Log Format",
    description = "",
    displayPosition = 810,
    group = "LOG",
    dependsOn = "logMode",
    triggeredByValue = "LOG4J"
  )
  public boolean enableLog4jCustomLogFormat;


  @ConfigDef(
    required = true,
    type = ConfigDef.Type.STRING,
    defaultValue = "%r [%t] %-5p %c %x - %m%n",
    label = "Custom Log4J Format",
    description = "Specify your own custom log4j format.",
    displayPosition = 820,
    group = "LOG",
    dependsOn = "enableLog4jCustomLogFormat",
    triggeredByValue = "true"
  )
  public String log4jCustomLogFormat;

  //AVRO

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.BOOLEAN,
    defaultValue = "true",
    label = "Message includes Schema",
    description = "The Kafka message includes the Avro schema",
    displayPosition = 830,
    group = "AVRO",
    dependsOn = "dataFormat",
    triggeredByValue = "AVRO"
  )
  public boolean schemaInMessage;

  @ConfigDef(
    required = false,
    type = ConfigDef.Type.TEXT,
    defaultValue = "",
    label = "Avro Schema",
    description = "Overrides the schema associated with the message. Optionally use the runtime:loadResource function to use a schema stored in a file",
    displayPosition = 840,
    group = "AVRO",
    dependsOn = "dataFormat",
    triggeredByValue = "AVRO",
    mode = ConfigDef.Mode.JSON
  )
  public String avroSchema;

  //BINARY

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.NUMBER,
    defaultValue = "1024",
    label = "Max Data Size (bytes)",
    description = "Larger objects are not processed",
    displayPosition = 850,
    group = "BINARY",
    dependsOn = "dataFormat",
    triggeredByValue = "BINARY",
    min = 1,
    max = Integer.MAX_VALUE
  )
  public int binaryMaxObjectLen;

  @Override
  protected Source createSource() {
    SourceArguments args = new SourceArguments(metadataBrokerList,
      zookeeperConnect, consumerGroup, topic, dataFormat, charset, removeCtrlChars,
      produceSingleRecordPerMessage, maxBatchSize, maxWaitTime,
      textMaxLineLen, jsonContent, jsonMaxObjectLen, csvFileFormat, csvHeader,
      csvMaxObjectLen, xmlRecordElement, xmlMaxObjectLen, logMode, logMaxObjectLen, retainOriginalLine,
      customLogFormat, regex, grokPatternDefinition, grokPattern, fieldPathsToGroupName,
      enableLog4jCustomLogFormat, log4jCustomLogFormat, maxStackTraceLines, onParseError, kafkaConsumerConfigs,
      schemaInMessage, avroSchema, binaryMaxObjectLen, csvCustomDelimiter, csvCustomEscape, csvCustomQuote,
      csvRecordType);
    delegatingKafkaSource = new DelegatingKafkaSource(new StandaloneKafkaSourceFactory(args),
      new ClusterKafkaSourceFactory(args));
    return delegatingKafkaSource;
  }

  @Override
  public Source getSource() {
    return source != null?  delegatingKafkaSource.getSource(): null;
  }

  @Override
  public void errorNotification(Throwable throwable) {
    DelegatingKafkaSource delegatingSource = delegatingKafkaSource;
    if (delegatingSource != null) {
      Source source = delegatingSource.getSource();
      if (source instanceof ErrorListener) {
        ((ErrorListener)source).errorNotification(throwable);
      }
    }
  }

  @Override
  public void shutdown() {
    DelegatingKafkaSource delegatingSource = delegatingKafkaSource;
    if (delegatingSource != null) {
      Source source = delegatingSource.getSource();
      if (source instanceof ClusterSource) {
        ((ClusterSource)source).shutdown();
      }
    }
  }
}
