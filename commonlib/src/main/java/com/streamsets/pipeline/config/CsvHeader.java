/**
 * (c) 2014 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.config;

import com.streamsets.pipeline.api.GenerateResourceBundle;
import com.streamsets.pipeline.api.Label;

@GenerateResourceBundle
public enum CsvHeader implements Label {
  WITH_HEADER("With Header Line"),
  IGNORE_HEADER("Ignore Header Line"),
  NO_HEADER("No Header Line"),
  ;

  private final String label;

  CsvHeader(String label) {
    this.label = label;
  }

  @Override
  public String getLabel() {
    return label;
  }

}