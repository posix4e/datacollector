/**
 * (c) 2014 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.restapi.bean;

import com.streamsets.pipeline.record.HeaderImpl;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;

public class TestHeaderBean {

  @Test(expected = NullPointerException.class)
  public void testHeaderBeanNull() {
    HeaderJson h = new HeaderJson(null);
  }

  @Test
  public void testHeaderBean() {
    HeaderImpl header = new HeaderImpl("s1", "id1", "/s1", "t1", "t0", null, "byte", "e1", "ep1", "es1", "ec", "em",
      System.currentTimeMillis(), new HashMap<String, Object>());

    HeaderJson headerJsonBean = new HeaderJson(header);

    Assert.assertEquals(header.getErrorCode(), headerJsonBean.getErrorCode());
    Assert.assertEquals(header.getErrorMessage(), headerJsonBean.getErrorMessage());
    Assert.assertEquals(header.getErrorDataCollectorId(), headerJsonBean.getErrorDataCollectorId());
    Assert.assertEquals(header.getErrorPipelineName(), headerJsonBean.getErrorPipelineName());
    Assert.assertEquals(header.getErrorStage(), headerJsonBean.getErrorStage());
    Assert.assertEquals(header.getErrorTimestamp(), headerJsonBean.getErrorTimestamp());
    Assert.assertEquals(header.getPreviousTrackingId(), headerJsonBean.getPreviousTrackingId());
    Assert.assertEquals(header.getRaw(), headerJsonBean.getRaw());
    Assert.assertEquals(header.getRawMimeType(), headerJsonBean.getRawMimeType());
    Assert.assertEquals(header.getValues(), headerJsonBean.getValues());
    Assert.assertEquals(header.getSourceId(), headerJsonBean.getSourceId());
    Assert.assertEquals(header.getStageCreator(), headerJsonBean.getStageCreator());
    Assert.assertEquals(header.getStagesPath(), headerJsonBean.getStagesPath());
    Assert.assertEquals(header.getTrackingId(), headerJsonBean.getTrackingId());

  }

  @Test
  public void testHeaderBeanConstructorWithArgs() {
    long timestamp = System.currentTimeMillis();
    HeaderImpl header = new HeaderImpl("s1", "id1", "/s1", "t1", "t0", null, "byte", "e1", "ep1", "es1", "ec", "em",
      timestamp, new HashMap<String, Object>());

    HeaderJson headerJsonBean = new HeaderJson("s1", "id1", "/s1", "t1", "t0", null, "byte", "e1", "ep1", "es1", "ec", "em",
      timestamp, new HashMap<String, Object>());

    Assert.assertEquals(header.getErrorCode(), headerJsonBean.getErrorCode());
    Assert.assertEquals(header.getErrorMessage(), headerJsonBean.getErrorMessage());
    Assert.assertEquals(header.getErrorDataCollectorId(), headerJsonBean.getErrorDataCollectorId());
    Assert.assertEquals(header.getErrorPipelineName(), headerJsonBean.getErrorPipelineName());
    Assert.assertEquals(header.getErrorStage(), headerJsonBean.getErrorStage());
    Assert.assertEquals(header.getErrorTimestamp(), headerJsonBean.getErrorTimestamp());
    Assert.assertEquals(header.getPreviousTrackingId(), headerJsonBean.getPreviousTrackingId());
    Assert.assertEquals(header.getRaw(), headerJsonBean.getRaw());
    Assert.assertEquals(header.getRawMimeType(), headerJsonBean.getRawMimeType());
    Assert.assertEquals(header.getValues(), headerJsonBean.getValues());
    Assert.assertEquals(header.getSourceId(), headerJsonBean.getSourceId());
    Assert.assertEquals(header.getStageCreator(), headerJsonBean.getStageCreator());
    Assert.assertEquals(header.getStagesPath(), headerJsonBean.getStagesPath());
    Assert.assertEquals(header.getTrackingId(), headerJsonBean.getTrackingId());

    //underlying headerimpl
    Assert.assertEquals(header.getErrorCode(), headerJsonBean.getHeader().getErrorCode());
    Assert.assertEquals(header.getErrorMessage(), headerJsonBean.getHeader().getErrorMessage());
    Assert.assertEquals(header.getAttributeNames(), headerJsonBean.getHeader().getAttributeNames());
    Assert.assertEquals(header.getErrorDataCollectorId(), headerJsonBean.getHeader().getErrorDataCollectorId());
    Assert.assertEquals(header.getErrorPipelineName(), headerJsonBean.getHeader().getErrorPipelineName());
    Assert.assertEquals(header.getErrorStage(), headerJsonBean.getHeader().getErrorStage());
    Assert.assertEquals(header.getErrorTimestamp(), headerJsonBean.getHeader().getErrorTimestamp());
    Assert.assertEquals(header.getPreviousTrackingId(), headerJsonBean.getHeader().getPreviousTrackingId());
    Assert.assertEquals(header.getRaw(), headerJsonBean.getHeader().getRaw());
    Assert.assertEquals(header.getRawMimeType(), headerJsonBean.getHeader().getRawMimeType());
    Assert.assertEquals(header.getSourceRecord(), headerJsonBean.getHeader().getSourceRecord());
    Assert.assertEquals(header.getValues(), headerJsonBean.getHeader().getValues());
    Assert.assertEquals(header.getSourceId(), headerJsonBean.getHeader().getSourceId());
    Assert.assertEquals(header.getStageCreator(), headerJsonBean.getHeader().getStageCreator());
    Assert.assertEquals(header.getStagesPath(), headerJsonBean.getHeader().getStagesPath());
    Assert.assertEquals(header.getTrackingId(), headerJsonBean.getHeader().getTrackingId());

  }
}