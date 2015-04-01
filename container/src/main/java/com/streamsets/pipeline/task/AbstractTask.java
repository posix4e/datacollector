/**
 * (c) 2014 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.task;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.streamsets.pipeline.api.impl.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public abstract class AbstractTask implements Task {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractTask.class);
  private static final Map<Status, Set<Status>> VALID_TRANSITIONS = ImmutableMap.of(
      Status.CREATED, (Set<Status>)ImmutableSet.of(Status.INITIALIZED),
      Status.INITIALIZED, ImmutableSet.of(Status.RUNNING, Status.STOPPED),
      Status.RUNNING, ImmutableSet.of(Status.STOPPED),
      Status.STOPPED, ImmutableSet.of(Status.STOPPED, Status.INITIALIZED),
      Status.ERROR, ImmutableSet.<Status>of()
  );

  private static final String STATE_ERROR_MSG = "Current status is '{}'";
  private final String name;
  private final CountDownLatch latch;
  private volatile Status status;

  public AbstractTask(String name) {
    this.name = Preconditions.checkNotNull(name, "name cannot be null");
    setStatus(Status.CREATED);
    latch = new CountDownLatch(1);
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public synchronized void init() {
    Preconditions.checkState(VALID_TRANSITIONS.get(getStatus()).contains(Status.INITIALIZED),
                             Utils.formatL(STATE_ERROR_MSG, getStatus()));
    try {
      LOG.debug("Task '{}' initializing", getName());
      setStatus(Status.INITIALIZED);
      initTask();
      LOG.debug("Task '{}' initialized", getName());
    } catch (RuntimeException ex) {
      LOG.warn("Task '{}' failed to initialize, {}, calling stopTask() and going into ERROR", getName(),
               ex.getMessage(), ex);
      safeStop(Status.ERROR);
      throw ex;
    }
  }

  @Override
  public synchronized void run() {
    Preconditions.checkState(VALID_TRANSITIONS.get(getStatus()).contains(Status.RUNNING),
                             Utils.formatL(STATE_ERROR_MSG, getStatus()));
    setStatus(Status.RUNNING);
    try {
      LOG.debug("Task '{}' starting", getName());
      runTask();
      LOG.debug("Task '{}' running", getName());
    } catch (RuntimeException ex) {
      LOG.warn("Task '{}' failed to start, {}, calling stopTask() and going into ERROR", getName(), ex.getMessage(),
               ex);
      safeStop(Status.ERROR);
      throw ex;
    }
  }

  @Override
  public synchronized void stop() {
    Preconditions.checkState(VALID_TRANSITIONS.get(getStatus()).contains(Status.STOPPED),
                             Utils.formatL(STATE_ERROR_MSG, getStatus()));
    if (getStatus() != Status.STOPPED) {
      LOG.debug("Task '{}' stopping", getName());
      safeStop(Status.STOPPED);
    }
  }

  private void safeStop(Status endStatus) {
    Status priorStatus = getStatus();
    try {
      setStatus(endStatus);
      stopTask();
      LOG.debug("Task '{}' stopped from status '{}'", getName(), priorStatus);
    } catch (RuntimeException ex) {
      LOG.warn("Task '{}' failed to stop properly, {}", getName(), ex.getMessage(), ex);
      setStatus(Status.ERROR);
    }
  }

  private void setStatus(Status status) {
    if (this.status == Status.RUNNING) {
      latch.countDown();
    }
    this.status = status;
  }

  @Override
  public Status getStatus() {
    return status;
  }

  @Override
  public void waitWhileRunning() throws InterruptedException {
    Preconditions.checkState(getStatus() == Status.RUNNING || getStatus() == Status.STOPPED,
                             Utils.formatL(STATE_ERROR_MSG, getStatus()));
    if (getStatus() == Status.RUNNING) {
      latch.await();
    }
  }

  @Override
  public String toString() {
    return Utils.format("{}[status='{}']", getName(), getStatus());
  }

  protected void initTask() {
  }

  protected void runTask() {
  }

  protected void stopTask() {
  }

}