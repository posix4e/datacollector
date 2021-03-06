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
package com.streamsets.datacollector.io;


import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

import com.streamsets.datacollector.io.DataStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class TestDataStore {

  private File createTestDir() {
    File dir = new File("target", UUID.randomUUID().toString()).getAbsoluteFile();
    Assert.assertTrue(dir.mkdirs());
    return dir;
  }

  @Test(expected = IllegalStateException.class)
  public void testNotReentrantLock() throws IOException {
    DataStore ds = new DataStore(new File(createTestDir(), "x"));
    try {
      ds.acquireLock();
      ds.acquireLock();
    } finally {
      ds.close();
    }
  }

  @Test
  public void testReleaseNoNotLockedLock() throws IOException {
    DataStore ds = new DataStore(new File(createTestDir(), "x"));
    try {
      ds.releaseLock();
    } finally {
      ds.close();
    }
  }

  @Test
  public void testCloseMultipeTimes() throws IOException {
    DataStore ds = new DataStore(new File(createTestDir(), "x"));
    try {
      OutputStream os = ds.getOutputStream();
      os.close();
      os.close();
    } finally {
      ds.close();
    }
  }

  @Test(timeout = 5000)
  public void testMultipleThreadAcquireReleaselock() throws Exception {
    final DataStore ds = new DataStore(new File(createTestDir(), "x"));
    Thread th1 = createThreadAcquireReleaseLock(ds);
    Thread th2 = createThreadAcquireReleaseLock(ds);
    th1.start();
    th2.start();
    th1.join();
    th2.join();
  }

  private Thread createThreadAcquireReleaseLock(final DataStore ds) {
    Thread thread = new Thread() {
      @Override
      public void run() {
        ds.acquireLock();
        try {
          Thread.sleep(500);
        } catch (InterruptedException e) {
        }
        ds.releaseLock();
      }
    };
    return thread;
  }

  @Test
  public void testLock() throws IOException {
    DataStore ds = new DataStore(new File(createTestDir(), "x"));
    try {
      ds.acquireLock();
      ds.releaseLock();
      ds.acquireLock();
      ds.releaseLock();
    } finally {
      ds.close();
    }
  }

  @Test(expected = IllegalStateException.class)
  public void testDoubleStore() throws IOException {
    File file = new File(createTestDir(), "x");
    DataStore ds1 = new DataStore(file);
    DataStore ds2 = new DataStore(file);
    try {
      ds1.acquireLock();
      ds2.acquireLock();
    } finally {
      ds1.close();
      ds2.close();
    }
  }

  @Test
  public void testWriteRead() throws IOException {
    DataStore ds = new DataStore(new File(createTestDir(), "x"));
    try {
      ds.getOutputStream().close();
      ds.getInputStream().close();
      ds.getOutputStream().close();
    } finally {
      ds.close();
    }
  }

  @Test(expected = IOException.class)
  public void testDataStoreCloseWithOpenWrite() throws IOException {
    DataStore ds = new DataStore(new File(createTestDir(), "x"));
    try {
      ds.getOutputStream();
    } finally {
      ds.close();
    }
  }

  @Test(expected = IOException.class)
  public void testDataStoreCloseWithOpenRead() throws IOException {
    DataStore ds = new DataStore(new File(createTestDir(), "x"));
    try {
      ds.getOutputStream().close();
      ds.getInputStream();
    } finally {
      ds.close();
    }
  }

  private void createFile(File file, byte[] data) throws IOException {
    OutputStream os = new FileOutputStream(file);
    os.write(data);
    os.close();
  }

  private byte[] readViaStore(DataStore dataStore) throws IOException {
    InputStream is = dataStore.getInputStream();
    byte[] arr = new byte[(dataStore.getFile().exists()) ? (int) dataStore.getFile().length() : 0];
    IOUtils.readFully(is, arr);
    is.close();
    return arr;
  }

  @Test
  public void testVerifyAndRecoverFromNew() throws IOException {
    byte[] neu = new byte[]{1, 2, 3};
    File file = new File(createTestDir(), "x");
    createFile(new File(file.getAbsolutePath() + "-new"), neu);
    DataStore ds = new DataStore(file);
    try {
      byte[] res = readViaStore(ds);
      Assert.assertArrayEquals(neu, res);
      ds.getInputStream().close();
    } finally {
      ds.close();
    }
  }

  @Test
  public void testVerifyAndRecoverFromOld() throws IOException {
    byte[] old = new byte[]{1, 2, 3};
    File file = new File(createTestDir(), "x");
    createFile(new File(file.getAbsolutePath() + "-old"), old);
    DataStore ds = new DataStore(file);
    try {
      byte[] arr = readViaStore(ds);
      Assert.assertArrayEquals(old, arr);
      ds.getInputStream().close();
    } finally {
      ds.close();
    }
  }

  @Test
  public void testVerifyAndRecoverFromOldAndTemp() throws IOException {
    byte[] old = new byte[]{1, 2, 3};
    byte[] neu = new byte[]{1, 2, 3, 4};
    File file = new File(createTestDir(), "x");
    createFile(new File(file.getAbsolutePath() + "-old"), old);
    createFile(new File(file.getAbsolutePath() + "-tmp"), neu);
    DataStore ds = new DataStore(file);
    try {
      byte[] res = readViaStore(ds);
      Assert.assertArrayEquals(old, res);
      ds.getInputStream().close();
    } finally {
      ds.close();
    }
  }

  @Test
  public void testVerifyAndRecoverFromOldAndNew() throws IOException {
    byte[] old = new byte[]{1, 2, 3};
    byte[] neu = new byte[]{1, 2, 3, 4};
    File file = new File(createTestDir(), "x");
    createFile(new File(file.getAbsolutePath() + "-old"), old);
    createFile(new File(file.getAbsolutePath() + "-new"), neu);
    DataStore ds = new DataStore(file);
    try {
      byte[] res = readViaStore(ds);
      Assert.assertArrayEquals(neu, res);
      ds.getInputStream().close();
    } finally {
      ds.close();
    }
  }

  @Test(expected = IOException.class)
  public void testVerifyAndRecoverInvalidNewAndTmp() throws IOException {
    byte[] arr = new byte[]{1, 2, 3};
    File file = new File(createTestDir(), "x");
    createFile(new File(file.getAbsolutePath() + "-new"), arr);
    createFile(new File(file.getAbsolutePath() + "-tmp"), arr);
    DataStore ds = new DataStore(file);
    try {
      readViaStore(ds);
    } finally {
      ds.close();
    }
  }

  @Test(expected = IOException.class)
  public void testVerifyAndRecoverInvalidNewAndFile() throws IOException {
    byte[] arr = new byte[]{1, 2, 3};
    File file = new File(createTestDir(), "x");
    createFile(new File(file.getAbsolutePath() + "-new"), arr);
    createFile(new File(file.getAbsolutePath()), arr);
    DataStore ds = new DataStore(file);
    try {
      readViaStore(ds);
    } finally {
      ds.close();
    }
  }

  @Test(expected = IOException.class)
  public void testVerifyAndRecoverInvalidTmpAndNoOld() throws IOException {
    byte[] arr = new byte[]{1, 2, 3};
    File file = new File(createTestDir(), "x");
    createFile(new File(file.getAbsolutePath() + "-tmp"), arr);
    DataStore ds = new DataStore(file);
    try {
      readViaStore(ds);
    } finally {
      ds.close();
    }
  }

  @Test(expected = IOException.class)
  public void testVerifyAndRecoverInvalidTmpOldFile() throws IOException {
    byte[] arr = new byte[]{1, 2, 3};
    File file = new File(createTestDir(), "x");
    createFile(new File(file.getAbsolutePath() + "-tmp"), arr);
    createFile(new File(file.getAbsolutePath() + "-old"), arr);
    createFile(new File(file.getAbsolutePath()), arr);
    DataStore ds = new DataStore(file);
    try {
      readViaStore(ds);
    } finally {
      ds.close();
    }
  }

  @Test(expected = IOException.class)
  public void testVerifyAndRecoverInvalidOldFile() throws IOException {
    byte[] arr = new byte[]{1, 2, 3};
    File file = new File(createTestDir(), "x");
    createFile(new File(file.getAbsolutePath() + "-old"), arr);
    createFile(new File(file.getAbsolutePath()), arr);
    DataStore ds = new DataStore(file);
    try {
      readViaStore(ds);
    } finally {
      ds.close();
    }
  }

}
