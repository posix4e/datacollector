/**
 * (c) 2014 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.config;

import com.streamsets.pipeline.api.Label;

public enum BadRecordsOptions implements Label {
  DISCARD("Discard"),
  SAVE("Save")
  ;

  public final String label;
  BadRecordsOptions(String label) {
    this.label = label;
  }


  @Override
  public String getLabel() {
    return label;
  }

}