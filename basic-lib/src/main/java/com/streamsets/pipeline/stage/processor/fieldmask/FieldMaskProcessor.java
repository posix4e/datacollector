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
package com.streamsets.pipeline.stage.processor.fieldmask;

import com.google.common.annotations.VisibleForTesting;
import com.streamsets.pipeline.api.Field;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.api.base.OnRecordErrorException;
import com.streamsets.pipeline.api.base.SingleLaneRecordProcessor;
import com.streamsets.pipeline.lib.util.FieldRegexUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FieldMaskProcessor extends SingleLaneRecordProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(FieldMaskProcessor.class);

  private static final String FIXED_LENGTH_MASK = "xxxxxxxxxx";
  private static final char NON_MASK_CHAR = '#';
  private static final char MASK_CHAR = 'x';

  private final List<FieldMaskConfig> fieldMaskConfigs;
  private Map<String, Set<Integer>> regexToGroupsToShowMap = new HashMap<>();
  private Map<String, Pattern> regExToPatternMap = new HashMap<>();

  public FieldMaskProcessor(List<FieldMaskConfig> fieldMaskConfigs) {
    this.fieldMaskConfigs = fieldMaskConfigs;
  }

  @Override
  protected List<ConfigIssue> init() {
    List<ConfigIssue> issues =  super.init();
    for(FieldMaskConfig fieldMaskConfig : fieldMaskConfigs) {
      if(fieldMaskConfig.maskType == MaskType.REGEX) {
        Pattern p = Pattern.compile(fieldMaskConfig.regex);
        int maxGroupCount = p.matcher("").groupCount();
        if(maxGroupCount == 0) {
          issues.add(getContext().createConfigIssue(Groups.MASKING.name(), "groupsToShow", Errors.MASK_03,
            fieldMaskConfig.regex));
        } else {
          regExToPatternMap.put(fieldMaskConfig.regex, p);
          if (fieldMaskConfig.groupsToShow == null || fieldMaskConfig.groupsToShow.trim().isEmpty()) {
            issues.add(getContext().createConfigIssue(Groups.MASKING.name(), "groupsToShow", Errors.MASK_02));
            return issues;
          } else {
            String[] groupsToShowString = fieldMaskConfig.groupsToShow.split(",");
            Set<Integer> groups = new HashSet<>();
            for (String groupString : groupsToShowString) {
              try {
                int groupToShow = Integer.parseInt(groupString.trim());
                if (groupToShow <= 0 || groupToShow > maxGroupCount) {
                  issues.add(getContext().createConfigIssue(Groups.MASKING.name(), "groupsToShow", Errors.MASK_01,
                    groupToShow, fieldMaskConfig.regex, maxGroupCount));
                }
                groups.add(groupToShow);
              } catch (NumberFormatException e) {
                issues.add(getContext().createConfigIssue(Groups.MASKING.name(), "groupsToShow", Errors.MASK_01, groupString
                  , fieldMaskConfig.regex, maxGroupCount, e.toString(), e));
              }
            }
            regexToGroupsToShowMap.put(fieldMaskConfig.regex, groups);
          }
        }
      }
    }
    return issues;
  }

  @Override
  protected void process(Record record, SingleLaneBatchMaker batchMaker) throws StageException {
    Set<String> fieldPaths = record.getFieldPaths();
    List<String> nonStringFields = new ArrayList<>();
    for(FieldMaskConfig fieldMaskConfig : fieldMaskConfigs) {
      for (String toMask : fieldMaskConfig.fields) {
        for(String matchingFieldPath : FieldRegexUtil.getMatchingFieldPaths(toMask, fieldPaths)) {
          if (record.has(matchingFieldPath)) {
            Field field = record.get(matchingFieldPath);
            if (field.getType() != Field.Type.STRING) {
              nonStringFields.add(matchingFieldPath);
            } else {
              if (field.getValue() != null) {
                Field newField = Field.create(maskField(field, fieldMaskConfig));
                record.set(matchingFieldPath, newField);
              }
            }
          }
        }
      }
    }
    if (nonStringFields.isEmpty()) {
      batchMaker.addRecord(record);
    } else {
      throw new OnRecordErrorException(Errors.MASK_00, record.getHeader().getSourceId(), nonStringFields);
    }
  }

  private String maskField(Field field, FieldMaskConfig fieldMaskConfig) {
    if(fieldMaskConfig.maskType == MaskType.FIXED_LENGTH) {
      return fixedLengthMask();
    } else if (fieldMaskConfig.maskType == MaskType.VARIABLE_LENGTH) {
      return variableLengthMask(field.getValueAsString());
    } else if (fieldMaskConfig.maskType == MaskType.CUSTOM) {
      return mask(field.getValueAsString(), fieldMaskConfig.mask);
    } else if (fieldMaskConfig.maskType == MaskType.REGEX) {
      return regExMask(field, fieldMaskConfig);
    }
    //Should not happen
    return null;
  }

  @VisibleForTesting
  String regExMask(Field field, FieldMaskConfig fieldMaskConfig) {
    String value = field.getValueAsString();
    Matcher matcher = regExToPatternMap.get(fieldMaskConfig.regex).matcher(value);
    if(matcher.matches()) {
      int groupCount = matcher.groupCount();
      //create a masked string of the same length as the original string
      StringBuilder resultString = new StringBuilder();
      for(int i = 0; i < value.length(); i++) {
        resultString.append(MASK_CHAR);
      }
      //for each group that needs to be shown, replace the masked string with the original string characters at those
      //positions
      Set<Integer> groupsToShow = regexToGroupsToShowMap.get(fieldMaskConfig.regex);
      if(groupsToShow != null && !groupsToShow.isEmpty()) {
        for (int i = 1; i <= groupCount; i++) {
          if (groupsToShow.contains(i)) {
            resultString.replace(matcher.start(i), matcher.end(i), matcher.group(i));
          }
        }
      }
      return resultString.toString();
    }
    return field.getValueAsString();
  }

  @VisibleForTesting
  String mask(String toMask, String mask) {
    int index = 0;
    StringBuilder masked = new StringBuilder();
    for (int i = 0; i < mask.length() && index < toMask.length(); i++) {
      char c = mask.charAt(i);
      if (c == NON_MASK_CHAR) {
        masked.append(toMask.charAt(index));
        index++;
      } else if (c == MASK_CHAR) {
        masked.append(c);
        index++;
      } else {
        masked.append(c);
        //The data can be either formatted or not
        //for example ssn data could be 123456789 or 123-45-6789
        if(toMask.charAt(index) == c) {
          //the data is already in the required format
          index++;
        }
      }
    }
    return masked.toString();
  }

  @VisibleForTesting
  String fixedLengthMask() {
    return FIXED_LENGTH_MASK;
  }

  @VisibleForTesting
  String variableLengthMask(String toMask) {
    StringBuilder masked = new StringBuilder();
    for (int i = 0; i < toMask.length(); i++) {
      masked.append(MASK_CHAR);
    }
    return masked.toString();
  }
}
