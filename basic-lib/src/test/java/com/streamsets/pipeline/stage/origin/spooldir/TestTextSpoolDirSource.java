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
package com.streamsets.pipeline.stage.origin.spooldir;

import com.streamsets.pipeline.api.BatchMaker;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.config.Compression;
import com.streamsets.pipeline.config.CsvRecordType;
import com.streamsets.pipeline.config.DataFormat;
import com.streamsets.pipeline.config.OnParseError;
import com.streamsets.pipeline.config.PostProcessingOptions;
import com.streamsets.pipeline.sdk.SourceRunner;
import com.streamsets.pipeline.sdk.StageRunner;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;
import java.util.UUID;

public class TestTextSpoolDirSource {

  private String createTestDir() {
    File f = new File("target", UUID.randomUUID().toString());
    Assert.assertTrue(f.mkdirs());
    return f.getAbsolutePath();
  }

  private final static String LINE1 = "1234567890";
  private final static String LINE2 = "A1234567890";

  private final static String GBK_STRING = "ÓÃ»§Ãû:ôâÈ»12";
  private final static String UTF_STRING = "脫脙禄搂脙没:么芒脠禄12";

  private File createLogFile(String charset) throws Exception {
    File f = new File(createTestDir(), "test.log");
    Writer writer = new OutputStreamWriter(new FileOutputStream(f), charset);
    IOUtils.write(LINE1 + "\n", writer);
    IOUtils.write(LINE2, writer);
    writer.close();
    return f;
  }

  private SpoolDirSource createSource(String charset) {
    return new SpoolDirSource(DataFormat.TEXT, charset, false, 100, createTestDir(), 10, 1, "file-[0-9].log", 10, null,
      Compression.NONE, "*",  null,
      PostProcessingOptions.ARCHIVE, createTestDir(), 10, null, null, -1, '^', '^', '^', null, 0, 10,
      null, 0, null, 0, false, null, null, null, null, null, false, null, OnParseError.ERROR,
      -1, null, CsvRecordType.LIST);
  }

  public void testProduceFullFile(String charset) throws Exception {
    SpoolDirSource source = createSource(charset);
    SourceRunner runner = new SourceRunner.Builder(SpoolDirDSource.class, source).addOutputLane("lane").build();
    runner.runInit();
    try {
      BatchMaker batchMaker = SourceRunner.createTestBatchMaker("lane");
      Assert.assertEquals("-1", source.produce(createLogFile(charset), "0", 10, batchMaker));
      StageRunner.Output output = SourceRunner.getOutput(batchMaker);
      List<Record> records = output.getRecords().get("lane");
      Assert.assertNotNull(records);
      Assert.assertEquals(2, records.size());
      Assert.assertEquals(LINE1, records.get(0).get().getValueAsMap().get("text").getValueAsString());
      Assert.assertFalse(records.get(0).has("/truncated"));
      Assert.assertEquals(LINE2.substring(0, 10), records.get(1).get().getValueAsMap().get("text").getValueAsString());
      Assert.assertTrue(records.get(1).has("/truncated"));
    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testProduceFullFileUTF8() throws Exception {
    testProduceFullFile("UTF-8");
  }

  @Test
  public void testProduceFullFileIBM500() throws Exception {
    testProduceFullFile("IBM500");
  }

  @Test
  public void testProduceLessThanFile() throws Exception {
    SpoolDirSource source = createSource("UTF-8");
    SourceRunner runner = new SourceRunner.Builder(SpoolDirDSource.class, source).addOutputLane("lane").build();
    runner.runInit();
    try {
      BatchMaker batchMaker = SourceRunner.createTestBatchMaker("lane");
      String offset = source.produce(createLogFile("UTF-8"), "0", 1, batchMaker);
      Assert.assertEquals("11", offset);
      StageRunner.Output output = SourceRunner.getOutput(batchMaker);
      List<Record> records = output.getRecords().get("lane");
      Assert.assertNotNull(records);
      Assert.assertEquals(1, records.size());
      Assert.assertEquals(LINE1, records.get(0).get().getValueAsMap().get("text").getValueAsString());
      Assert.assertFalse(records.get(0).has("/truncated"));


      batchMaker = SourceRunner.createTestBatchMaker("lane");
      offset = source.produce(createLogFile("UTF-8"), offset, 1, batchMaker);
      Assert.assertEquals("22", offset);
      output = SourceRunner.getOutput(batchMaker);
      records = output.getRecords().get("lane");
      Assert.assertNotNull(records);
      Assert.assertEquals(1, records.size());
      Assert.assertEquals(LINE2.substring(0, 10), records.get(0).get().getValueAsMap().get("text").getValueAsString());
      Assert.assertEquals(true, records.get(0).get().getValueAsMap().get("truncated").getValueAsBoolean());

      batchMaker = SourceRunner.createTestBatchMaker("lane");
      offset = source.produce(createLogFile("UTF-8"), offset, 1, batchMaker);
      Assert.assertEquals("-1", offset);
      output = SourceRunner.getOutput(batchMaker);
      records = output.getRecords().get("lane");
      Assert.assertNotNull(records);
      Assert.assertEquals(0, records.size());
    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testGbkEncodedFile() throws Exception {
    SpoolDirSource source = createSource("GBK");
    SourceRunner runner = new SourceRunner.Builder(SpoolDirDSource.class, source).addOutputLane("lane").build();
    runner.runInit();
    try {
      // Write out a gbk-encoded file.
      File f = new File(createTestDir(), "test_gbk.log");
      Writer writer = new OutputStreamWriter(new FileOutputStream(f), "GBK");
      IOUtils.write(UTF_STRING, writer);
      writer.close();

      // Read back the file to verify its content is gbk-encoded.
      BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(f)));
      Assert.assertEquals(GBK_STRING, reader.readLine());
      reader.close();

      BatchMaker batchMaker = SourceRunner.createTestBatchMaker("lane");
      Assert.assertEquals("-1", source.produce(f, "0", 10, batchMaker));
      StageRunner.Output output = SourceRunner.getOutput(batchMaker);
      List<Record> records = output.getRecords().get("lane");
      Assert.assertNotNull(records);
      Assert.assertEquals(1, records.size());
      Assert.assertEquals(
          UTF_STRING.substring(0, 10),
          records.get(0).get().getValueAsMap().get("text").getValueAsString()
      );
      Assert.assertTrue(records.get(0).has("/truncated"));
    } finally {
      runner.runDestroy();
    }
  }
}
