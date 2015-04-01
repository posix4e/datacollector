/**
 * (c) 2014 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.snapshotstore.impl;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableList;
import com.streamsets.pipeline.main.RuntimeInfo;
import com.streamsets.pipeline.api.Field;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.record.RecordImpl;
import com.streamsets.pipeline.runner.ErrorSink;
import com.streamsets.pipeline.runner.StageOutput;
import com.streamsets.pipeline.snapshotstore.SnapshotInfo;
import com.streamsets.pipeline.snapshotstore.SnapshotStatus;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestFileSnapshotStore {

  private static final String TEST_STRING = "TestSnapshotPersister";
  private static final String MIME = "application/octet-stream";
  private static final String PIPELINE_NAME = "myPipeline";
  private static final String PIPELINE_REV = "0";
  private static final String SNAPSHOT_NAME1 = "snapshot1";
  private static final String SNAPSHOT_NAME2 = "snapshot2";

  private FileSnapshotStore snapshotStore = null;

  @BeforeClass
  public static void beforeClass() {
    System.setProperty(RuntimeInfo.DATA_DIR, "./target/var");
  }

  @AfterClass
  public static void afterClass() {
    System.getProperties().remove(RuntimeInfo.DATA_DIR);
  }

  @Before
  public void setUp() throws IOException {
    File f = new File(System.getProperty(RuntimeInfo.DATA_DIR));
    FileUtils.deleteDirectory(f);
    RuntimeInfo info = new RuntimeInfo(new MetricRegistry(), ImmutableList.of(getClass().getClassLoader()));
    snapshotStore = new FileSnapshotStore(info);
    snapshotStore.setInProgress(PIPELINE_NAME, PIPELINE_REV, SNAPSHOT_NAME1, true);
  }

  @After
  public void tearDown() {

  }

  @Test
  public void testStoreAndRetrieveSnapshot() {
    Assert.assertTrue(snapshotStore.retrieveSnapshot(PIPELINE_NAME, PIPELINE_REV, SNAPSHOT_NAME1).isEmpty());
    List<StageOutput> snapshot = createSnapshotData();
    snapshotStore.storeSnapshot(PIPELINE_NAME, PIPELINE_REV, SNAPSHOT_NAME1, snapshot);

    InputStream in = snapshotStore.getSnapshot(PIPELINE_NAME, PIPELINE_REV, SNAPSHOT_NAME1);
    Assert.assertNotNull(in);

    //TODO: Retrieve snapshot and compare contents once de-serializer is ready
    //Assert.assertEquals(2, snapshotStore.retrieveSnapshot(PIPELINE_NAME).size());
    //StageOutput actualS1 = snapshotStore.retrieveSnapshot().get(0);
    //Assert.assertEquals(2);
  }

  @Test
  public void testGetSnapshotStatus() {
    SnapshotStatus snapshotStatus = snapshotStore.getSnapshotStatus(PIPELINE_NAME, PIPELINE_REV, SNAPSHOT_NAME1);
    Assert.assertNotNull(snapshotStatus);
    Assert.assertEquals(false, snapshotStatus.isExists());
    Assert.assertEquals(true, snapshotStatus.isSnapshotInProgress());

    //create snapshot
    List<StageOutput> snapshot = createSnapshotData();
    snapshotStore.storeSnapshot(PIPELINE_NAME, PIPELINE_REV, SNAPSHOT_NAME1, snapshot);

    snapshotStatus = snapshotStore.getSnapshotStatus(PIPELINE_NAME, PIPELINE_REV, SNAPSHOT_NAME1);
    Assert.assertNotNull(snapshotStatus);
    Assert.assertEquals(true, snapshotStatus.isExists());
    Assert.assertEquals(false, snapshotStatus.isSnapshotInProgress());
  }

  @Test
  public void testGetSnapshotsInfo() {
    List<SnapshotInfo> snapshotsInfo = snapshotStore.getSnapshotsInfo(PIPELINE_NAME, PIPELINE_REV);
    Assert.assertEquals(1, snapshotsInfo.size());

    //First one is in progress snapshot info
    Assert.assertEquals(PIPELINE_NAME, snapshotsInfo.get(0).getPipelineName());
    Assert.assertEquals(SNAPSHOT_NAME1, snapshotsInfo.get(0).getSnapshotName());
    Assert.assertNull(snapshotsInfo.get(0).getCaptured()); //Captured is set to null when capturing sanpshot is in progress

    //Store the first snapshot
    List<StageOutput> snapshot = createSnapshotData();
    snapshotStore.storeSnapshot(PIPELINE_NAME, PIPELINE_REV, SNAPSHOT_NAME1, snapshot);

    snapshotsInfo = snapshotStore.getSnapshotsInfo(PIPELINE_NAME, PIPELINE_REV);
    Assert.assertEquals(1, snapshotsInfo.size());
    Assert.assertEquals(PIPELINE_NAME, snapshotsInfo.get(0).getPipelineName());
    Assert.assertEquals(SNAPSHOT_NAME1, snapshotsInfo.get(0).getSnapshotName());
    Assert.assertNotNull(snapshotsInfo.get(0).getCaptured());

    //Capture second snapshot
    snapshotStore.setInProgress(PIPELINE_NAME, PIPELINE_REV, SNAPSHOT_NAME2, true);
    snapshotsInfo = snapshotStore.getSnapshotsInfo(PIPELINE_NAME, PIPELINE_REV);
    Assert.assertEquals(2, snapshotsInfo.size());

    SnapshotInfo secondSnapshotInfo = null;
    for(SnapshotInfo snapshotInfo: snapshotsInfo) {
      if(snapshotInfo.getSnapshotName().equals(SNAPSHOT_NAME2)) {
        secondSnapshotInfo = snapshotInfo;
      }
    }
    Assert.assertNotNull(secondSnapshotInfo);
    Assert.assertEquals(PIPELINE_NAME, secondSnapshotInfo.getPipelineName());
    Assert.assertEquals(SNAPSHOT_NAME2, secondSnapshotInfo.getSnapshotName());
    Assert.assertNull(secondSnapshotInfo.getCaptured());


    snapshotStore.storeSnapshot(PIPELINE_NAME, PIPELINE_REV, SNAPSHOT_NAME2, snapshot);
    snapshotsInfo = snapshotStore.getSnapshotsInfo(PIPELINE_NAME, PIPELINE_REV);
    Assert.assertEquals(2, snapshotsInfo.size());

    secondSnapshotInfo = null;
    for(SnapshotInfo snapshotInfo: snapshotsInfo) {
      if(snapshotInfo.getSnapshotName().equals(SNAPSHOT_NAME2)) {
        secondSnapshotInfo = snapshotInfo;
      }
    }
    Assert.assertNotNull(secondSnapshotInfo);
    Assert.assertEquals(PIPELINE_NAME, secondSnapshotInfo.getPipelineName());
    Assert.assertEquals(SNAPSHOT_NAME2, secondSnapshotInfo.getSnapshotName());
    Assert.assertNotNull(secondSnapshotInfo.getCaptured());
  }

  @Test
  public void testDeleteSnapshot() {
    //create snapshot
    List<StageOutput> snapshot = createSnapshotData();
    snapshotStore.storeSnapshot(PIPELINE_NAME, PIPELINE_REV, SNAPSHOT_NAME1, snapshot);

    SnapshotStatus snapshotStatus = snapshotStore.getSnapshotStatus(PIPELINE_NAME, PIPELINE_REV, SNAPSHOT_NAME1);
    Assert.assertNotNull(snapshotStatus);
    Assert.assertEquals(true, snapshotStatus.isExists());
    Assert.assertEquals(false, snapshotStatus.isSnapshotInProgress());

    //delete
    snapshotStore.deleteSnapshot(PIPELINE_NAME, PIPELINE_REV, SNAPSHOT_NAME1);

    snapshotStatus = snapshotStore.getSnapshotStatus(PIPELINE_NAME, PIPELINE_REV, SNAPSHOT_NAME1);
    Assert.assertNotNull(snapshotStatus);
    Assert.assertEquals(false, snapshotStatus.isExists());
    Assert.assertEquals(false, snapshotStatus.isSnapshotInProgress());

  }

  @Test(expected = RuntimeException.class)
  public void testStoreInvalidDir() {
    RuntimeInfo info = Mockito.mock(RuntimeInfo.class);
    Mockito.when(info.getDataDir()).thenReturn("\0");
    snapshotStore = new FileSnapshotStore(info);

    //Runtime exception expected
    snapshotStore.storeSnapshot(PIPELINE_NAME, PIPELINE_REV, SNAPSHOT_NAME1, Collections.EMPTY_LIST);

  }

  @Test
  public void testGetSnapshotWhenItDoesNotExist() {
    Assert.assertNull(snapshotStore.getSnapshot("randomPipelineName", PIPELINE_REV, SNAPSHOT_NAME1));
  }

  private List<StageOutput> createSnapshotData() {

    List<StageOutput> snapshot = new ArrayList<>(2);

    List<Record> records1 = new ArrayList<>(2);

    Record r1 = new RecordImpl("s", "s:1", TEST_STRING.getBytes(), MIME);
    r1.set(Field.create(1));

    ((RecordImpl)r1).createTrackingId();
    ((RecordImpl)r1).createTrackingId();

    Record r2 = new RecordImpl("s", "s:2", TEST_STRING.getBytes(), MIME);
    r2.set(Field.create(2));

    ((RecordImpl)r2).createTrackingId();
    ((RecordImpl)r2).createTrackingId();

    records1.add(r1);
    records1.add(r2);

    Map<String, List<Record>> so1 = new HashMap<>(1);
    so1.put("lane", records1);

    StageOutput s1 = new StageOutput("source", so1, new ErrorSink());
    snapshot.add(s1);

    List<Record> records2 = new ArrayList<>(1);
    Record r3 = new RecordImpl("s", "s:3", TEST_STRING.getBytes(), MIME);
    r3.set(Field.create(1));

    ((RecordImpl)r3).createTrackingId();
    ((RecordImpl)r3).createTrackingId();

    Record r4 = new RecordImpl("s", "s:2", TEST_STRING.getBytes(), MIME);
    r4.set(Field.create(2));

    ((RecordImpl)r4).createTrackingId();
    ((RecordImpl)r4).createTrackingId();

    records2.add(r3);

    Map<String, List<Record>> so2 = new HashMap<>(1);
    so2.put("lane", records2);
    StageOutput s2 = new StageOutput("processor", so2, new ErrorSink());
    snapshot.add(s2);

    return snapshot;
  }
}