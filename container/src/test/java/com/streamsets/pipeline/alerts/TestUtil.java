/**
 * (c) 2014 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.alerts;

import com.streamsets.pipeline.api.Field;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.record.RecordImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TestUtil {


  private static final String TEST_STRING = "TestAlertsChecker";
  private static final String MIME = "application/octet-stream";

  public static Map<String, List<Record>> createSnapshot(String lane) {
    Map<String, List<Record>> snapshot = new HashMap<>();
    List<Record> records = new ArrayList<>();

    Map<String, Field> map1 = new LinkedHashMap<>();
    map1.put("name", Field.create(Field.Type.STRING, "streamsets"));
    Record r1 = new RecordImpl("s", "s:2", TEST_STRING.getBytes(), MIME);
    r1.set(Field.create(map1));
    records.add(r1);

    Map<String, Field> map2 = new LinkedHashMap<>();
    map2.put("name", Field.create(Field.Type.STRING, null));
    Record r2 = new RecordImpl("s", "s:2", TEST_STRING.getBytes(), MIME);
    r2.set(Field.create(map2));
    records.add(r2);

    Map<String, Field> map3 = new LinkedHashMap<>();
    map3.put("name", Field.create(Field.Type.STRING, null));
    Record r3 = new RecordImpl("s", "s:2", TEST_STRING.getBytes(), MIME);
    r3.set(Field.create(map3));
    records.add(r3);

    Map<String, Field> map4 = new LinkedHashMap<>();
    map4.put("name", Field.create(Field.Type.STRING, "streamsets"));
    Record r4 = new RecordImpl("s", "s:2", TEST_STRING.getBytes(), MIME);
    r4.set(Field.create(map4));
    records.add(r4);

    Map<String, Field> map5 = new LinkedHashMap<>();
    map5.put("name", Field.create(Field.Type.STRING, null));
    Record r5 = new RecordImpl("s", "s:2", TEST_STRING.getBytes(), MIME);
    r5.set(Field.create(map5));
    records.add(r5);

    Map<String, Field> map6 = new LinkedHashMap<>();
    map6.put("name", Field.create(Field.Type.STRING, "streamsets"));
    Record r6 = new RecordImpl("s", "s:2", TEST_STRING.getBytes(), MIME);
    r6.set(Field.create(map6));
    records.add(r6);

    snapshot.put(lane + "::s", records);

    return snapshot;
  }
}