/**
 * (c) 2015 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.dataCollector.execution;

public enum PipelineStatus {
  EDITED,          // pipeline job has been create/modified, didn't run since the creation/modification

  STARTING,         // pipeline job starting (initialization)
  START_ERROR,      // pipeline job failed while start (during initialization) or failed while submission in cluster mode

  RUNNING,          // pipeline job running
  RUNNING_ERROR,    // pipeline job failed while running (calling destroy on pipeline) - only for standalone
  RUN_ERROR,        // pipeline job failed while running (done)

  FINISHING,        // pipeline job finishing (source reached end, returning NULL offset) (calling destroy on pipeline) - only for standalone
  FINISHED,         // pipeline job finished                                              (done)

  KILLED,           // only happens in cluster mode


  STOPPING,         // pipeline job has been manually stopped (calling destroy on pipeline)
  STOPPED,          // pipeline job has been manually stopped (done)

  DISCONNECTING,    // SDC going down gracefully (calling destroy on pipeline for LOCAL, doing nothing for CLUSTER)
  DISCONNECTED,     // SDC going down gracefully (done)

  CONNECTING,       // SDC starting back (transition to STARTING for LOCAL, for CLUSTER checks job still running)
                    //                   (and transitions to RUNNING or RUN_ERROR -streaming- or FINISHED -batch)
  CONNECT_ERROR     // failed to get to RUNNING, on SDC restart will retry again - only for cluster mode
  ;
}
