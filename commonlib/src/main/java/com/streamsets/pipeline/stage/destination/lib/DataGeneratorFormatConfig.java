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
package com.streamsets.pipeline.stage.destination.lib;

import com.streamsets.pipeline.api.ConfigDef;
import com.streamsets.pipeline.api.FieldSelectorModel;
import com.streamsets.pipeline.api.Stage;
import com.streamsets.pipeline.api.ValueChooserModel;
import com.streamsets.pipeline.config.CharsetChooserValues;
import com.streamsets.pipeline.config.CsvHeader;
import com.streamsets.pipeline.config.CsvHeaderChooserValues;
import com.streamsets.pipeline.config.CsvMode;
import com.streamsets.pipeline.config.CsvModeChooserValues;
import com.streamsets.pipeline.config.DataFormat;
import com.streamsets.pipeline.config.JsonMode;
import com.streamsets.pipeline.config.JsonModeChooserValues;
import com.streamsets.pipeline.lib.el.StringEL;
import com.streamsets.pipeline.lib.generator.DataGeneratorFactory;
import com.streamsets.pipeline.lib.generator.DataGeneratorFactoryBuilder;
import com.streamsets.pipeline.lib.generator.avro.AvroDataGeneratorFactory;
import com.streamsets.pipeline.lib.generator.binary.BinaryDataGeneratorFactory;
import com.streamsets.pipeline.lib.generator.delimited.DelimitedDataGeneratorFactory;
import com.streamsets.pipeline.lib.generator.text.TextDataGeneratorFactory;
import com.streamsets.pipeline.lib.util.AvroTypeUtil;
import com.streamsets.pipeline.lib.util.DelimitedDataConstants;
import com.streamsets.pipeline.stage.common.DataFormatErrors;
import com.streamsets.pipeline.stage.common.DataFormatGroups;
import org.apache.avro.Schema;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class DataGeneratorFormatConfig {

  private static final String CHARSET_UTF8 = "UTF-8";

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.MODEL,
    defaultValue = "UTF-8",
    label = "Charset",
    displayPosition = 3010,
    group = "#0",
    dependsOn = "dataFormat^",
    triggeredByValue = {"TEXT", "JSON", "DELIMITED", "XML", "LOG"}
  )
  @ValueChooserModel(CharsetChooserValues.class)
  public String charset;

  /********  For DELIMITED Content  ***********/

  @ConfigDef(
    required = false,
    type = ConfigDef.Type.MODEL,
    defaultValue = "CSV",
    label = "Delimiter Format",
    description = "",
    displayPosition = 150,
    group = "DELIMITED",
    dependsOn = "dataFormat^",
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
    displayPosition = 160,
    group = "DELIMITED",
    dependsOn = "dataFormat^",
    triggeredByValue = "DELIMITED"
  )
  @ValueChooserModel(CsvHeaderChooserValues.class)
  public CsvHeader csvHeader;

  @ConfigDef(
    required = false,
    type = ConfigDef.Type.BOOLEAN,
    defaultValue = "true",
    label = "Remove New Line Characters",
    description = "Replaces new lines characters with white spaces",
    displayPosition = 170,
    group = "DELIMITED",
    dependsOn = "dataFormat^",
    triggeredByValue = "DELIMITED"
  )
  public boolean csvReplaceNewLines;

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

  /********  For JSON *******/

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.MODEL,
    defaultValue = "MULTIPLE_OBJECTS",
    label = "JSON Content",
    description = "",
    displayPosition = 180,
    group = "JSON",
    dependsOn = "dataFormat^",
    triggeredByValue = "JSON"
  )
  @ValueChooserModel(JsonModeChooserValues.class)
  public JsonMode jsonMode;

  /********  For TEXT Content  ***********/

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.MODEL,
    defaultValue = "/",
    label = "Text Field Path",
    description = "Field to write data to Kafka",
    displayPosition = 190,
    group = "TEXT",
    dependsOn = "dataFormat^",
    triggeredByValue = "TEXT"
  )
  @FieldSelectorModel(singleValued = true)
  public String textFieldPath;

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.BOOLEAN,
    defaultValue = "false",
    label = "Empty Line If No Text",
    description = "",
    displayPosition = 200,
    group = "TEXT",
    dependsOn = "dataFormat^",
    triggeredByValue = "TEXT"
  )
  public boolean textEmptyLineIfNull;

  /********  For AVRO Content  ***********/

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.TEXT,
    defaultValue = "",
    label = "Avro Schema",
    description = "Optionally use the runtime:loadResource function to use a schema stored in a file",
    displayPosition = 320,
    group = "AVRO",
    dependsOn = "dataFormat^",
    triggeredByValue = {"AVRO"},
    mode = ConfigDef.Mode.JSON
  )
  public String avroSchema;

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.BOOLEAN,
    defaultValue = "true",
    label = "Include Schema",
    description = "Includes the Avro schema in the Flume event",
    displayPosition = 330,
    group = "AVRO",
    dependsOn = "dataFormat^",
    triggeredByValue = "AVRO"
  )
  public boolean includeSchema = true;

  /********  For Binary Content  ***********/

  @ConfigDef(
    required = true,
    type = ConfigDef.Type.MODEL,
    defaultValue = "/",
    label = "Binary Field Path",
    description = "Field to write data to Kafka",
    displayPosition = 120,
    group = "BINARY",
    dependsOn = "dataFormat^",
    triggeredByValue = "BINARY",
    elDefs = {StringEL.class}
  )
  @FieldSelectorModel(singleValued = true)
  public String binaryFieldPath;

  private boolean validateDataGenerator (
      Stage.Context context,
      DataFormat dataFormat,
      String groupName,
      List<Stage.ConfigIssue> issues
  ) {
    boolean valid = true;

    DataGeneratorFactoryBuilder builder = new DataGeneratorFactoryBuilder(context,
      dataFormat.getGeneratorFormat());
    if(charset == null || charset.trim().isEmpty()) {
      charset = CHARSET_UTF8;
    }

    Charset cSet;
    try {
      cSet = Charset.forName(charset);
    } catch (UnsupportedCharsetException ex) {
      // setting it to a valid one so the parser factory can be configured and tested for more errors
      cSet = StandardCharsets.UTF_8;
      issues.add(context.createConfigIssue(groupName, "charset", DataFormatErrors.DATA_FORMAT_05, charset));
      valid &= false;
    }

    builder.setCharset(cSet);

    switch (dataFormat) {
      case SDC_JSON:
        break;
      case DELIMITED:
        builder.setMode(csvFileFormat);
        builder.setMode(csvHeader);
        builder.setConfig(DelimitedDataGeneratorFactory.REPLACE_NEWLINES_KEY, csvReplaceNewLines);
        builder.setConfig(DelimitedDataConstants.DELIMITER_CONFIG, csvCustomDelimiter);
        builder.setConfig(DelimitedDataConstants.ESCAPE_CONFIG, csvCustomEscape);
        builder.setConfig(DelimitedDataConstants.QUOTE_CONFIG, csvCustomQuote);
        break;
      case TEXT:
        builder.setConfig(TextDataGeneratorFactory.FIELD_PATH_KEY, textFieldPath);
        builder.setConfig(TextDataGeneratorFactory.EMPTY_LINE_IF_NULL_KEY, textEmptyLineIfNull);
        break;
      case JSON:
        builder.setMode(jsonMode);
        break;
      case AVRO:
        Schema schema = null;
        Map<String, Object> defaultValues = new HashMap<>();
        try {
          schema = new Schema.Parser()
              .setValidate(true)
              .setValidateDefaults(true)
              .parse(avroSchema);
        } catch (Exception e)  {
          issues.add(
              context.createConfigIssue(
                  DataFormatGroups.AVRO.name(),
                  "avroSchema",
                  DataFormatErrors.DATA_FORMAT_300,
                  e.toString(),
                  e
              )
          );
          valid &= false;
        }
        if(schema != null) {
          try {
            defaultValues.putAll(AvroTypeUtil.getDefaultValuesFromSchema(schema, new HashSet<String>()));
          } catch (IOException e) {
            issues.add(
                context.createConfigIssue(
                    DataFormatGroups.AVRO.name(),
                    "avroSchema",
                    DataFormatErrors.DATA_FORMAT_301,
                    e.toString(),
                    e
                )
            );
            valid &= false;
          }
        }
        builder.setConfig(AvroDataGeneratorFactory.SCHEMA_KEY, avroSchema);
        builder.setConfig(AvroDataGeneratorFactory.INCLUDE_SCHEMA_KEY, includeSchema);
        builder.setConfig(AvroDataGeneratorFactory.DEFAULT_VALUES_KEY, defaultValues);
        break;
      case BINARY:
        builder.setConfig(BinaryDataGeneratorFactory.FIELD_PATH_KEY, binaryFieldPath);
        break;
    }
    if(valid) {
      try {
        dataGeneratorFactory = builder.build();
      } catch (Exception ex) {
        issues.add(context.createConfigIssue(null, null, DataFormatErrors.DATA_FORMAT_201, ex.toString(), ex));
        valid &= false;
      }
    }
    return valid;
  }

  public boolean init(
      Stage.Context context,
      DataFormat dataFormat,
      String groupName,
      String configName,
      List<Stage.ConfigIssue> issues
  ) {
    boolean valid = true;
    switch (dataFormat) {
      case TEXT:
        //required field configuration to be set and it is "/" by default
        if (textFieldPath == null || textFieldPath.isEmpty()) {
          issues.add(context.createConfigIssue(DataFormatGroups.TEXT.name(), "fieldPath", DataFormatErrors.DATA_FORMAT_200));
          valid = false;
        }
        break;
      case BINARY:
        //required field configuration to be set and it is "/" by default
        if (binaryFieldPath == null || binaryFieldPath.isEmpty()) {
          issues.add(context.createConfigIssue(DataFormatGroups.BINARY.name(), "fieldPath", DataFormatErrors.DATA_FORMAT_200));
          valid = false;
        }
        break;
      case JSON:
      case DELIMITED:
      case SDC_JSON:
      case AVRO:
        //no-op
        break;
      default:
        issues.add(context.createConfigIssue(groupName, configName, DataFormatErrors.DATA_FORMAT_04, dataFormat));
        valid = false;
    }

    valid &= validateDataGenerator(context, dataFormat, groupName, issues);

    return valid;
  }

  private DataGeneratorFactory dataGeneratorFactory;

  public DataGeneratorFactory getDataGeneratorFactory() {
    return dataGeneratorFactory;
  }
}
