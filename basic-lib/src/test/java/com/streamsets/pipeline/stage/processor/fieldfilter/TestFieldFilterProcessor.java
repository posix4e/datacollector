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
package com.streamsets.pipeline.stage.processor.fieldfilter;

import com.google.common.collect.ImmutableList;
import com.streamsets.pipeline.api.Field;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.sdk.ProcessorRunner;
import com.streamsets.pipeline.sdk.RecordCreator;
import com.streamsets.pipeline.sdk.StageRunner;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class TestFieldFilterProcessor {

  /********************************************************/
  /********************   KEEP RELATED   ******************/
  /********************************************************/

  @Test
  public void testKeepSimple() throws StageException {
    ProcessorRunner runner = new ProcessorRunner.Builder(FieldFilterDProcessor.class)
      .addConfiguration("fields", ImmutableList.of("/name", "/age"))
      .addConfiguration("filterOperation", FilterOperation.KEEP)
      .addOutputLane("a").build();
    runner.runInit();

    try {
      Map<String, Field> map = new LinkedHashMap<>();
      map.put("name", Field.create("a"));
      map.put("age", Field.create("b"));
      map.put("streetAddress", Field.create("c"));
      map.put("city", Field.create("d"));
      map.put("state", Field.create("e"));
      map.put("zip", Field.create(94040));
      Record record = RecordCreator.create("s", "s:1");
      record.set(Field.create(map));

      StageRunner.Output output = runner.runProcess(ImmutableList.of(record));
      Assert.assertEquals(1, output.getRecords().get("a").size());
      Field field = output.getRecords().get("a").get(0).get();
      Assert.assertTrue(field.getValue() instanceof Map);
      Map<String, Field> result = field.getValueAsMap();
      Assert.assertTrue(result.size() == 2);
      Assert.assertTrue(result.containsKey("name"));
      Assert.assertTrue(result.containsKey("age"));
    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testKeepNonExistingFiled() throws StageException {
    ProcessorRunner runner = new ProcessorRunner.Builder(FieldFilterDProcessor.class)
      .addConfiguration("fields", ImmutableList.of("/city"))
      .addConfiguration("filterOperation", FilterOperation.KEEP)
      .addOutputLane("a").build();
    runner.runInit();

    try {
      Map<String, Field> map = new LinkedHashMap<>();
      map.put("name", Field.create("a"));
      map.put("age", Field.create("b"));
      map.put("streetAddress", Field.create("c"));
      Record record = RecordCreator.create("s", "s:1");
      record.set(Field.create(map));

      StageRunner.Output output = runner.runProcess(ImmutableList.of(record));
      Assert.assertEquals(1, output.getRecords().get("a").size());
      Field field = output.getRecords().get("a").get(0).get();
      Assert.assertTrue(field.getValue() instanceof Map);
      Map<String, Field> result = field.getValueAsMap();
      Assert.assertTrue(result.size() == 0);
    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testNestedKeep1() throws StageException {
    /*
     * In a deep nested field path try to retain the second elements of an array within an array
     */
    Record record = createNestedRecord();

    //Keep only second elements of array within array "/USA[0]/SanFrancisco/noe/streets"
    ProcessorRunner runner = new ProcessorRunner.Builder(FieldFilterDProcessor.class)
      .addConfiguration("fields", ImmutableList.of(
        "/USA[0]/SanFrancisco/noe/streets[0][1]/name",
        "/USA[0]/SanFrancisco/noe/streets[1][1]/name"))
      .addConfiguration("filterOperation", FilterOperation.KEEP)
      .addOutputLane("a").build();
    runner.runInit();

    try {
      StageRunner.Output output = runner.runProcess(ImmutableList.of(record));
      Assert.assertEquals(1, output.getRecords().get("a").size());

      Record resultRecord = output.getRecords().get("a").get(0);
      //Note that since the first element was removed, the second element is the new first element
      Assert.assertTrue(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[0][0]/name"));
      Assert.assertEquals("b", resultRecord.get("/USA[0]/SanFrancisco/noe/streets[0][0]/name").getValueAsString());

      //Note that since the first element was removed, the second element is the new first element
      Assert.assertTrue(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[1][0]/name"));
      Assert.assertEquals("d", resultRecord.get("/USA[0]/SanFrancisco/noe/streets[1][0]/name").getValueAsString());

      Assert.assertFalse(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[0][1]/name"));
      Assert.assertFalse(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[1][1]/name"));
      Assert.assertFalse(resultRecord.has("/USA[1]/SantaMonica/cole/streets[0][0]/name"));
      Assert.assertFalse(resultRecord.has("/USA[1]/SantaMonica/cole/streets[0][1]/name"));
      Assert.assertFalse(resultRecord.has("/USA[0]/SanFrancisco/folsom/streets[0][0]/name"));
      Assert.assertFalse(resultRecord.has("/USA[0]/SanFrancisco/folsom/streets[0][1]/name"));
    } finally {
      runner.runDestroy();
    }

    /*
     * In a deep nested field path try to retain the first elements of an array within an array
     */
    record = createNestedRecord();
    //Keep only first elements of array within array "/USA[0]/SanFrancisco/noe/streets"
    runner = new ProcessorRunner.Builder(FieldFilterDProcessor.class)
      .addConfiguration("fields", ImmutableList.of(
        "/USA[0]/SanFrancisco/noe/streets[0][0]/name",
        "/USA[0]/SanFrancisco/noe/streets[1][0]/name"))
      .addConfiguration("filterOperation", FilterOperation.KEEP)
      .addOutputLane("a").build();
    runner.runInit();

    try {
      StageRunner.Output output = runner.runProcess(ImmutableList.of(record));
      Assert.assertEquals(1, output.getRecords().get("a").size());

      Record resultRecord = output.getRecords().get("a").get(0);

      Assert.assertTrue(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[0][0]/name"));
      Assert.assertEquals("a", resultRecord.get("/USA[0]/SanFrancisco/noe/streets[0][0]/name").getValueAsString());

      Assert.assertTrue(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[1][0]/name"));
      Assert.assertEquals("c", resultRecord.get("/USA[0]/SanFrancisco/noe/streets[1][0]/name").getValueAsString());

      Assert.assertFalse(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[0][1]/name"));
      Assert.assertFalse(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[1][1]/name"));
      Assert.assertFalse(resultRecord.has("/USA[1]/SantaMonica/cole/streets[0][0]/name"));
      Assert.assertFalse(resultRecord.has("/USA[1]/SantaMonica/cole/streets[0][1]/name"));
      Assert.assertFalse(resultRecord.has("/USA[0]/SanFrancisco/folsom/streets[0][0]/name"));
      Assert.assertFalse(resultRecord.has("/USA[0]/SanFrancisco/folsom/streets[0][1]/name"));
    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testNestedKeep2() throws StageException {
    /*
     * In a deep nested record try to retain arbitrary paths
     */
    Record record = createNestedRecord();
    ProcessorRunner runner = new ProcessorRunner.Builder(FieldFilterDProcessor.class)
      .addConfiguration("fields", ImmutableList.of(
        "/USA[0]/SanFrancisco/noe/streets[1][1]/name",
        "/USA[1]/SantaMonica/cole/streets[0][1]/name"))
      .addConfiguration("filterOperation", FilterOperation.KEEP)
      .addOutputLane("a").build();
    runner.runInit();

    try {
      StageRunner.Output output = runner.runProcess(ImmutableList.of(record));
      Assert.assertEquals(1, output.getRecords().get("a").size());

      Record resultRecord = output.getRecords().get("a").get(0);

      Assert.assertTrue(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[0][0]/name"));
      Assert.assertEquals("d", resultRecord.get("/USA[0]/SanFrancisco/noe/streets[0][0]/name").getValueAsString());

      Assert.assertTrue(resultRecord.has("/USA[1]/SantaMonica/cole/streets[0][0]/name"));
      Assert.assertEquals("f", resultRecord.get("/USA[1]/SantaMonica/cole/streets[0][0]/name").getValueAsString());

      Assert.assertFalse(resultRecord.has("/USA[1]/SantaMonica/cole/streets[0][1]/name"));
      Assert.assertFalse(resultRecord.has("/USA[0]/SanFrancisco/folsom/streets[0][0]/name"));
      Assert.assertFalse(resultRecord.has("/USA[0]/SanFrancisco/folsom/streets[0][1]/name"));
      Assert.assertFalse(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[0][1]/name"));
      Assert.assertFalse(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[1][0]/name"));
      Assert.assertFalse(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[1][1]/name"));

    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testNestedKeep3() throws StageException {
    /*
     * try to retain the second element from the root array list.
     * Make sure that this turns out to be the first element in the resulting record
     */
    Record record = createNestedRecord();
    ProcessorRunner runner = new ProcessorRunner.Builder(FieldFilterDProcessor.class)
      .addConfiguration("fields", ImmutableList.of(
        "/USA[1]/SantaMonica/cole/streets[0][1]/name"))
      .addConfiguration("filterOperation", FilterOperation.KEEP)
      .addOutputLane("a").build();
    runner.runInit();

    try {
      StageRunner.Output output = runner.runProcess(ImmutableList.of(record));
      Assert.assertEquals(1, output.getRecords().get("a").size());

      Record resultRecord = output.getRecords().get("a").get(0);

      Assert.assertTrue(resultRecord.has("/USA[0]/SantaMonica/cole/streets[0][0]/name"));
      Assert.assertEquals("f", resultRecord.get("/USA[0]/SantaMonica/cole/streets[0][0]/name").getValueAsString());

      Assert.assertFalse(resultRecord.has("/USA[0]/SantaMonica/cole/streets[0][1]/name"));
      Assert.assertFalse(resultRecord.has("/USA[0]/SanFrancisco"));
      Assert.assertFalse(resultRecord.has("/USA[1]"));
    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testNestedKeep4() throws StageException {
    Record record = createNestedRecord();
    /*
     * Keep non existing nested path "/USA[0]/SanFrancisco/cole".
     * Only objects upto /USA[0]/SanFrancisco should exist
     */
    ProcessorRunner runner = new ProcessorRunner.Builder(FieldFilterDProcessor.class)
      .addConfiguration("fields", ImmutableList.of(
        "/USA[0]/SanFrancisco/cole/streets[0][0]/name",
        "/USA[0]/SanFrancisco/cole/streets[1][0]/name"))
      .addConfiguration("filterOperation", FilterOperation.KEEP)
      .addOutputLane("a").build();
    runner.runInit();

    try {
      StageRunner.Output output = runner.runProcess(ImmutableList.of(record));
      Assert.assertEquals(1, output.getRecords().get("a").size());

      Record resultRecord = output.getRecords().get("a").get(0);
      Assert.assertTrue(resultRecord.has("/USA[0]/SanFrancisco"));
      Assert.assertEquals(0, resultRecord.get("/USA[0]/SanFrancisco").getValueAsMap().size());

      Assert.assertFalse(resultRecord.has("/USA[1]/SantaMonica/cole/streets[0][0]/name"));
      Assert.assertFalse(resultRecord.has("/USA[1]/SantaMonica/cole/streets[0][1]/name"));
      Assert.assertFalse(resultRecord.has("/USA[0]/SanFrancisco/folsom/streets[0][0]/name"));
      Assert.assertFalse(resultRecord.has("/USA[0]/SanFrancisco/folsom/streets[0][1]/name"));

    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testNestedKeep5() throws StageException {
    Record record = createNestedRecord();
    /*
     * keep all entries of a list by specifying path just upto the list
     */
    ProcessorRunner runner = new ProcessorRunner.Builder(FieldFilterDProcessor.class)
      .addConfiguration("fields", ImmutableList.of(
        "/USA[0]/SanFrancisco/folsom/streets[0]"))
      .addConfiguration("filterOperation", FilterOperation.KEEP)
      .addOutputLane("a").build();
    runner.runInit();

    try {
      StageRunner.Output output = runner.runProcess(ImmutableList.of(record));
      Assert.assertEquals(1, output.getRecords().get("a").size());

      Record resultRecord = output.getRecords().get("a").get(0);
      Assert.assertTrue(resultRecord.has("/USA[0]/SanFrancisco/folsom/streets[0]"));
      //Its a list and it is empty. use wild card [*] to preserve the contents of the list
      Assert.assertEquals(2, resultRecord.get("/USA[0]/SanFrancisco/folsom/streets[0]").getValueAsList().size());
    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testNestedKeep6() throws StageException {
    Record record = createNestedRecord();
    /*
     * keep all entries of a map by specifying path just upto the map
     */
    ProcessorRunner runner = new ProcessorRunner.Builder(FieldFilterDProcessor.class)
      .addConfiguration("fields", ImmutableList.of(
        "/USA[0]/SanFrancisco/noe"))
      .addConfiguration("filterOperation", FilterOperation.KEEP)
      .addOutputLane("a").build();
    runner.runInit();

    try {
      StageRunner.Output output = runner.runProcess(ImmutableList.of(record));
      Assert.assertEquals(1, output.getRecords().get("a").size());

      Record resultRecord = output.getRecords().get("a").get(0);
      Assert.assertTrue(resultRecord.has("/USA[0]/SanFrancisco/noe"));
      Assert.assertEquals(record.get("/USA[0]/SanFrancisco/noe/streets[0][0]/name").getValueAsString(), "a");
      Assert.assertEquals(record.get("/USA[0]/SanFrancisco/noe/streets[0][1]/name").getValueAsString(), "b");
      Assert.assertEquals(record.get("/USA[0]/SanFrancisco/noe/streets[1][0]/name").getValueAsString(), "c");
      Assert.assertEquals(record.get("/USA[0]/SanFrancisco/noe/streets[1][1]/name").getValueAsString(), "d");
    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testWildCardKeep1() throws StageException {

    /*
     * Use wil card in deep nested paths
     */
    Record record = createNestedRecord();
    ProcessorRunner runner = new ProcessorRunner.Builder(FieldFilterDProcessor.class)
      .addConfiguration("fields", ImmutableList.of("/USA[*]/SanFrancisco/*/streets[*][*]/name"))
      .addConfiguration("filterOperation", FilterOperation.KEEP)
      .addOutputLane("a").build();
    runner.runInit();

    try {
      StageRunner.Output output = runner.runProcess(ImmutableList.of(record));
      Assert.assertEquals(1, output.getRecords().get("a").size());

      Record resultRecord = output.getRecords().get("a").get(0);
      Assert.assertTrue(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[0][0]/name"));
      Assert.assertTrue(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[0][1]/name"));
      Assert.assertTrue(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[1][0]/name"));
      Assert.assertTrue(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[1][1]/name"));
      Assert.assertTrue(resultRecord.has("/USA[0]/SanFrancisco/folsom/streets[0][0]/name"));
      Assert.assertTrue(resultRecord.has("/USA[0]/SanFrancisco/folsom/streets[0][1]/name"));

      Assert.assertFalse(resultRecord.has("/USA[1]/SantaMonica/cole/streets[0][0]/name"));
      Assert.assertFalse(resultRecord.has("/USA[1]/SantaMonica/cole/streets[0][1]/name"));
    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testWildCardKeep2() throws StageException {
    /*
     * Use wil card in array within array
     */
    Record record = createNestedRecord();
    ProcessorRunner runner = new ProcessorRunner.Builder(FieldFilterDProcessor.class)
      .addConfiguration("fields", ImmutableList.of(
        "/USA[0]/SanFrancisco/noe/streets[0][*]"))
      .addConfiguration("filterOperation", FilterOperation.KEEP)
      .addOutputLane("a").build();
    runner.runInit();

    try {
      StageRunner.Output output = runner.runProcess(ImmutableList.of(record));
      Assert.assertEquals(1, output.getRecords().get("a").size());

      Record resultRecord = output.getRecords().get("a").get(0);
      Assert.assertTrue(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[0]"));
      Assert.assertEquals(record.get("/USA[0]/SanFrancisco/noe/streets[0][0]/name").getValueAsString(), "a");
      Assert.assertEquals(record.get("/USA[0]/SanFrancisco/noe/streets[0][1]/name").getValueAsString(), "b");

    } finally {
      runner.runDestroy();
    }

    /*
     * Use wil card in array
     */
    record = createNestedRecord();
    runner = new ProcessorRunner.Builder(FieldFilterDProcessor.class)
      .addConfiguration("fields", ImmutableList.of(
        "/USA[0]/SanFrancisco/noe/streets[*]"))
      .addConfiguration("filterOperation", FilterOperation.KEEP)
      .addOutputLane("a").build();
    runner.runInit();

    try {
      StageRunner.Output output = runner.runProcess(ImmutableList.of(record));
      Assert.assertEquals(1, output.getRecords().get("a").size());

      Record resultRecord = output.getRecords().get("a").get(0);
      Assert.assertTrue(resultRecord.has("/USA[0]/SanFrancisco/noe/streets"));
      Assert.assertEquals(record.get("/USA[0]/SanFrancisco/noe/streets[0][0]/name").getValueAsString(), "a");
      Assert.assertEquals(record.get("/USA[0]/SanFrancisco/noe/streets[0][1]/name").getValueAsString(), "b");
      Assert.assertEquals(record.get("/USA[0]/SanFrancisco/noe/streets[1][0]/name").getValueAsString(), "c");
      Assert.assertEquals(record.get("/USA[0]/SanFrancisco/noe/streets[1][1]/name").getValueAsString(), "d");

    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testWildCardKeep3() throws StageException {

    /*
     * Use wil card in map and array
     */
    Record record = createNestedRecord();
    ProcessorRunner runner = new ProcessorRunner.Builder(FieldFilterDProcessor.class)
      .addConfiguration("fields", ImmutableList.of("/USA[0]/SanFrancisco/*/streets[*][1]/name"))
      .addConfiguration("filterOperation", FilterOperation.KEEP)
      .addOutputLane("a").build();
    runner.runInit();

    try {
      StageRunner.Output output = runner.runProcess(ImmutableList.of(record));
      Assert.assertEquals(1, output.getRecords().get("a").size());

      Record resultRecord = output.getRecords().get("a").get(0);
      Assert.assertTrue(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[0][0]/name"));
      Assert.assertEquals("b", resultRecord.get("/USA[0]/SanFrancisco/noe/streets[0][0]/name").getValueAsString());
      Assert.assertFalse(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[0][1]/name"));
      Assert.assertTrue(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[1][0]/name"));
      Assert.assertEquals("d", resultRecord.get("/USA[0]/SanFrancisco/noe/streets[1][0]/name").getValueAsString());
      Assert.assertFalse(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[1][1]/name"));
      Assert.assertTrue(resultRecord.has("/USA[0]/SanFrancisco/folsom/streets[0][0]/name"));
      Assert.assertFalse(resultRecord.has("/USA[0]/SanFrancisco/folsom/streets[0][1]/name"));

      Assert.assertFalse(resultRecord.has("/USA[1]/SantaMonica/cole/streets[0][0]/name"));
      Assert.assertFalse(resultRecord.has("/USA[1]/SantaMonica/cole/streets[0][1]/name"));
    } finally {
      runner.runDestroy();
    }

    /*
     * Use wil card in map. Make sure the entire tree of elements is preserved
     */
    record = createNestedRecord();
    runner = new ProcessorRunner.Builder(FieldFilterDProcessor.class)
      .addConfiguration("fields", ImmutableList.of("/USA[0]/SanFrancisco/*"))
      .addConfiguration("filterOperation", FilterOperation.KEEP)
      .addOutputLane("a").build();
    runner.runInit();

    try {
      StageRunner.Output output = runner.runProcess(ImmutableList.of(record));
      Assert.assertEquals(1, output.getRecords().get("a").size());

      Record resultRecord = output.getRecords().get("a").get(0);
      Assert.assertTrue(resultRecord.has("/USA[0]/SanFrancisco/noe"));
      Assert.assertTrue(resultRecord.has("/USA[0]/SanFrancisco/folsom"));

      Assert.assertEquals(record.get("/USA[0]/SanFrancisco/noe/streets[0][0]/name").getValueAsString(), "a");
      Assert.assertEquals(record.get("/USA[0]/SanFrancisco/noe/streets[0][1]/name").getValueAsString(), "b");
      Assert.assertEquals(record.get("/USA[0]/SanFrancisco/noe/streets[1][0]/name").getValueAsString(), "c");
      Assert.assertEquals(record.get("/USA[0]/SanFrancisco/noe/streets[1][1]/name").getValueAsString(), "d");
      Assert.assertEquals(record.get("/USA[0]/SanFrancisco/folsom/streets[0][0]/name").getValueAsString(), "g");
      Assert.assertEquals(record.get("/USA[0]/SanFrancisco/folsom/streets[0][1]/name").getValueAsString(), "h");

      Assert.assertFalse(resultRecord.has("/USA[1]/SantaMonica/cole/streets[0][0]/name"));
      Assert.assertFalse(resultRecord.has("/USA[1]/SantaMonica/cole/streets[0][1]/name"));
    } finally {
      runner.runDestroy();
    }
  }

  /********************************************************/
  /******************   REMOVE RELATED   ******************/
  /********************************************************/

  @Test
  public void testRemoveSimple() throws StageException {
    ProcessorRunner runner = new ProcessorRunner.Builder(FieldFilterDProcessor.class)
      .addConfiguration("fields", ImmutableList.of("/name", "/age"))
      .addConfiguration("filterOperation", FilterOperation.REMOVE)
      .addOutputLane("a").build();
    runner.runInit();

    try {
      Map<String, Field> map = new LinkedHashMap<>();
      map.put("name", Field.create("a"));
      map.put("age", Field.create("b"));
      map.put("streetAddress", Field.create("c"));
      map.put("city", Field.create("d"));
      map.put("state", Field.create("e"));
      map.put("zip", Field.create(94040));
      Record record = RecordCreator.create("s", "s:1");
      record.set(Field.create(map));

      StageRunner.Output output = runner.runProcess(ImmutableList.of(record));
      Assert.assertEquals(1, output.getRecords().get("a").size());
      Field field = output.getRecords().get("a").get(0).get();
      Assert.assertTrue(field.getValue() instanceof Map);
      Map<String, Field> result = field.getValueAsMap();
      Assert.assertTrue(result.size() == 4);
      Assert.assertTrue(result.containsKey("streetAddress"));
      Assert.assertTrue(result.containsKey("city"));
      Assert.assertTrue(result.containsKey("state"));
      Assert.assertTrue(result.containsKey("zip"));
    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testRemoveNonExistingFiled() throws StageException {
    ProcessorRunner runner = new ProcessorRunner.Builder(FieldFilterDProcessor.class)
      .addConfiguration("fields", ImmutableList.of("/city"))
      .addConfiguration("filterOperation", FilterOperation.REMOVE)
      .addOutputLane("a").build();
    runner.runInit();

    try {
      Map<String, Field> map = new LinkedHashMap<>();
      map.put("name", Field.create("a"));
      map.put("age", Field.create("b"));
      map.put("streetAddress", Field.create("c"));
      Record record = RecordCreator.create("s", "s:1");
      record.set(Field.create(map));

      StageRunner.Output output = runner.runProcess(ImmutableList.of(record));
      Assert.assertEquals(1, output.getRecords().get("a").size());
      Field field = output.getRecords().get("a").get(0).get();
      Assert.assertTrue(field.getValue() instanceof Map);
      Map<String, Field> result = field.getValueAsMap();
      Assert.assertTrue(result.size() == 3);
      Assert.assertTrue(result.containsKey("name"));
      Assert.assertTrue(result.containsKey("age"));
      Assert.assertTrue(result.containsKey("streetAddress"));
    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testWildCardRemove1() throws StageException {

    Record record = createNestedRecord();

    ProcessorRunner runner = new ProcessorRunner.Builder(FieldFilterDProcessor.class)
      .addConfiguration("fields", ImmutableList.of("/USA[*]/SanFrancisco/*/streets[*][*]/name"))
      .addConfiguration("filterOperation", FilterOperation.REMOVE)
      .addOutputLane("a").build();
    runner.runInit();

    try {
      StageRunner.Output output = runner.runProcess(ImmutableList.of(record));
      Assert.assertEquals(1, output.getRecords().get("a").size());

      Record resultRecord = output.getRecords().get("a").get(0);
      Assert.assertFalse(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[0][0]/name"));
      Assert.assertFalse(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[0][1]/name"));
      Assert.assertFalse(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[1][0]/name"));
      Assert.assertFalse(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[1][1]/name"));

      Assert.assertEquals(resultRecord.get("/USA[1]/SantaMonica/cole/streets[0][0]/name").getValueAsString(), "e");
      Assert.assertEquals(resultRecord.get("/USA[1]/SantaMonica/cole/streets[0][1]/name").getValueAsString(), "f");
    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testWildCardRemove2() throws StageException {
    Record record = createNestedRecord();
    ProcessorRunner runner = new ProcessorRunner.Builder(FieldFilterDProcessor.class)
      .addConfiguration("fields", ImmutableList.of("/USA[0]/SanFrancisco/*/streets[*][1]/name"))
      .addConfiguration("filterOperation", FilterOperation.REMOVE)
      .addOutputLane("a").build();
    runner.runInit();

    try {
      StageRunner.Output output = runner.runProcess(ImmutableList.of(record));
      Assert.assertEquals(1, output.getRecords().get("a").size());

      Record resultRecord = output.getRecords().get("a").get(0);
      Assert.assertTrue(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[0][0]/name"));
      Assert.assertFalse(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[0][1]/name"));
      Assert.assertTrue(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[1][0]/name"));
      Assert.assertFalse(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[1][1]/name"));
      Assert.assertTrue(resultRecord.has("/USA[0]/SanFrancisco/folsom/streets[0][0]/name"));
      Assert.assertFalse(resultRecord.has("/USA[0]/SanFrancisco/folsom/streets[0][1]/name"));

      Assert.assertEquals(resultRecord.get("/USA[1]/SantaMonica/cole/streets[0][0]/name").getValueAsString(), "e");
      Assert.assertEquals(resultRecord.get("/USA[1]/SantaMonica/cole/streets[0][1]/name").getValueAsString(), "f");
    } finally {
      runner.runDestroy();
    }

    record = createNestedRecord();
    runner = new ProcessorRunner.Builder(FieldFilterDProcessor.class)
      .addConfiguration("fields", ImmutableList.of("/USA[0]/SanFrancisco/noe/streets[0][*]"))
      .addConfiguration("filterOperation", FilterOperation.REMOVE)
      .addOutputLane("a").build();
    runner.runInit();

    try {
      StageRunner.Output output = runner.runProcess(ImmutableList.of(record));
      Assert.assertEquals(1, output.getRecords().get("a").size());

      Record resultRecord = output.getRecords().get("a").get(0);
      Assert.assertTrue(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[0]"));
      Assert.assertEquals(0, resultRecord.get("/USA[0]/SanFrancisco/noe/streets[0]").getValueAsList().size());

      Assert.assertTrue(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[1][0]/name"));
      Assert.assertTrue(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[1][1]/name"));

      Assert.assertEquals(record.get("/USA[0]/SanFrancisco/noe/streets[1][0]/name").getValueAsString(), "c");
      Assert.assertEquals(record.get("/USA[0]/SanFrancisco/noe/streets[1][1]/name").getValueAsString(), "d");

      Assert.assertTrue(resultRecord.has("/USA[1]/SantaMonica/cole/streets[0][0]/name"));
      Assert.assertTrue(resultRecord.has("/USA[1]/SantaMonica/cole/streets[0][1]/name"));
      Assert.assertTrue(resultRecord.has("/USA[0]/SanFrancisco/folsom/streets[0][0]/name"));
      Assert.assertTrue(resultRecord.has("/USA[0]/SanFrancisco/folsom/streets[0][1]/name"));

    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testWildCardRemove3() throws StageException {

    Record record = createNestedRecord();
    ProcessorRunner runner = new ProcessorRunner.Builder(FieldFilterDProcessor.class)
      .addConfiguration("fields", ImmutableList.of("/USA[*]/SanFrancisco/noe"))
      .addConfiguration("filterOperation", FilterOperation.REMOVE)
      .addOutputLane("a").build();
    runner.runInit();

    try {
      StageRunner.Output output = runner.runProcess(ImmutableList.of(record));
      Assert.assertEquals(1, output.getRecords().get("a").size());

      Record resultRecord = output.getRecords().get("a").get(0);
      Assert.assertFalse(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[0][0]/name"));
      Assert.assertFalse(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[0][1]/name"));
      Assert.assertFalse(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[1][0]/name"));
      Assert.assertFalse(resultRecord.has("/USA[0]/SanFrancisco/noe/streets[1][1]/name"));

      Assert.assertTrue(resultRecord.has("/USA[1]/SantaMonica/cole/streets[0][0]/name"));
      Assert.assertTrue(resultRecord.has("/USA[1]/SantaMonica/cole/streets[0][1]/name"));
      Assert.assertTrue(resultRecord.has("/USA[0]/SanFrancisco/folsom/streets[0][0]/name"));
      Assert.assertTrue(resultRecord.has("/USA[0]/SanFrancisco/folsom/streets[0][1]/name"));
    } finally {
      runner.runDestroy();
    }

    record = createNestedRecord();
    runner = new ProcessorRunner.Builder(FieldFilterDProcessor.class)
      .addConfiguration("fields", ImmutableList.of("/USA[*]"))
      .addConfiguration("filterOperation", FilterOperation.REMOVE)
      .addOutputLane("a").build();
    runner.runInit();

    try {
      StageRunner.Output output = runner.runProcess(ImmutableList.of(record));
      Assert.assertEquals(1, output.getRecords().get("a").size());

      Record resultRecord = output.getRecords().get("a").get(0);
      Assert.assertFalse(resultRecord.has("/USA[0]"));
      Assert.assertFalse(resultRecord.has("/USA[1]"));
    } finally {
      runner.runDestroy();
    }
  }

  private Record createNestedRecord() {
    Field name1 = Field.create("a");
    Field name2 = Field.create("b");
    Map<String, Field> nameMap1 = new HashMap<>();
    nameMap1.put("name", name1);
    Map<String, Field> nameMap2 = new HashMap<>();
    nameMap2.put("name", name2);

    Field name3 = Field.create("c");
    Field name4 = Field.create("d");
    Map<String, Field> nameMap3 = new HashMap<>();
    nameMap3.put("name", name3);
    Map<String, Field> nameMap4 = new HashMap<>();
    nameMap4.put("name", name4);

    Field name5 = Field.create("e");
    Field name6 = Field.create("f");
    Map<String, Field> nameMap5 = new HashMap<>();
    nameMap5.put("name", name5);
    Map<String, Field> nameMap6 = new HashMap<>();
    nameMap6.put("name", name6);

    Field name7 = Field.create("g");
    Field name8 = Field.create("h");

    Map<String, Field> nameMap7 = new HashMap<>();
    nameMap7.put("name", name7);
    Map<String, Field> nameMap8 = new HashMap<>();
    nameMap8.put("name", name8);

    Field first = Field.create(Field.Type.LIST, ImmutableList.of(Field.create(nameMap1), Field.create(nameMap2)));
    Field second = Field.create(Field.Type.LIST, ImmutableList.of(Field.create(nameMap3), Field.create(nameMap4)));
    Field third = Field.create(Field.Type.LIST, ImmutableList.of(Field.create(nameMap5), Field.create(nameMap6)));
    Field fourth = Field.create(Field.Type.LIST, ImmutableList.of(Field.create(nameMap7), Field.create(nameMap8)));

    Map<String, Field> noe = new HashMap<>();
    noe.put("streets", Field.create(ImmutableList.of(first, second)));

    Map<String, Field> folsom = new HashMap<>();
    folsom.put("streets", Field.create(ImmutableList.of(fourth)));


    Map<String, Field> cole = new HashMap<>();
    cole.put("streets", Field.create(ImmutableList.of(third)));


    Map<String, Field> sfArea = new HashMap<>();
    sfArea.put("noe", Field.create(noe));
    sfArea.put("folsom", Field.create(folsom));

    Map<String, Field> utahArea = new HashMap<>();
    utahArea.put("cole", Field.create(cole));


    Map<String, Field> california = new HashMap<>();
    california.put("SanFrancisco", Field.create(sfArea));

    Map<String, Field> utah = new HashMap<>();
    utah.put("SantaMonica", Field.create(utahArea));

    Map<String, Field> map = new LinkedHashMap<>();
    map.put("USA", Field.create(Field.Type.LIST,
      ImmutableList.of(Field.create(california), Field.create(utah))));

    Record record = RecordCreator.create("s", "s:1");
    record.set(Field.create(map));

    //Nested record looks like this:
    Assert.assertEquals(record.get("/USA[0]/SanFrancisco/noe/streets[0][0]/name").getValueAsString(), "a");
    Assert.assertEquals(record.get("/USA[0]/SanFrancisco/noe/streets[0][1]/name").getValueAsString(), "b");
    Assert.assertEquals(record.get("/USA[0]/SanFrancisco/noe/streets[1][0]/name").getValueAsString(), "c");
    Assert.assertEquals(record.get("/USA[0]/SanFrancisco/noe/streets[1][1]/name").getValueAsString(), "d");
    Assert.assertEquals(record.get("/USA[1]/SantaMonica/cole/streets[0][0]/name").getValueAsString(), "e");
    Assert.assertEquals(record.get("/USA[1]/SantaMonica/cole/streets[0][1]/name").getValueAsString(), "f");
    Assert.assertEquals(record.get("/USA[0]/SanFrancisco/folsom/streets[0][0]/name").getValueAsString(), "g");
    Assert.assertEquals(record.get("/USA[0]/SanFrancisco/folsom/streets[0][1]/name").getValueAsString(), "h");

    return record;
  }
}
