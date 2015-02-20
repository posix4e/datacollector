/**
 * (c) 2014 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.stage.destination.hdfs;

import com.streamsets.pipeline.api.base.BaseEnumChooserValues;

public class LateRecordsActionChooserValues extends BaseEnumChooserValues {

  public LateRecordsActionChooserValues() {
    super(HdfsLateRecordsAction.class);
  }

}