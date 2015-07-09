/**
 * (c) 2015 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */

package com.streamsets.dataCollector.datacollector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamsets.dataCollector.execution.Manager;
import com.streamsets.dataCollector.main.MainStandalonePipelineManagerModule;
import com.streamsets.dataCollector.main.PipelineTask;
import com.streamsets.pipeline.DataCollector;
import com.streamsets.pipeline.api.impl.Utils;
import com.streamsets.pipeline.config.PipelineConfiguration;
import com.streamsets.pipeline.http.ServerNotYetRunningException;
import com.streamsets.pipeline.json.ObjectMapperFactory;
import com.streamsets.pipeline.main.BuildInfo;
import com.streamsets.pipeline.main.LogConfigurator;
import com.streamsets.pipeline.main.RuntimeInfo;
import com.streamsets.pipeline.prodmanager.StandalonePipelineManagerTask;
import com.streamsets.pipeline.restapi.bean.BeanHelper;
import com.streamsets.pipeline.restapi.bean.PipelineConfigurationJson;
import com.streamsets.pipeline.runner.Pipeline;
import com.streamsets.pipeline.stagelibrary.StageLibraryTask;
import com.streamsets.pipeline.store.PipelineStoreException;
import com.streamsets.pipeline.store.PipelineStoreTask;
import com.streamsets.pipeline.task.Task;
import com.streamsets.pipeline.task.TaskWrapper;
import com.streamsets.pipeline.validation.PipelineConfigurationValidator;
import dagger.ObjectGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class EmbeddedDataCollector implements DataCollector {
  private static final Logger LOG = LoggerFactory.getLogger(EmbeddedDataCollector.class);
  private String pipelineName;
  private Manager pipelineManager;
  private ObjectGraph dagger;
  private Thread waitingThread;
  private PipelineConfiguration realPipelineConfig;
  private PipelineTask pipelineTask;
  private Task task;

  private void createAndSave(String pipelineName) throws PipelineStoreException {
    String user = realPipelineConfig.getInfo().getCreator();
    String tag = realPipelineConfig.getInfo().getLastRev();
    String desc = realPipelineConfig.getDescription();
    StageLibraryTask stageLibrary = pipelineTask.getStageLibraryTask();
    PipelineStoreTask store = pipelineTask.getPipelineStoreTask();
    PipelineConfiguration tmpPipelineConfig =
      store.create(user, pipelineName, desc);
    // we might want to add an import API as now to import have to create one then update it
    realPipelineConfig.setUuid(tmpPipelineConfig.getUuid());
    PipelineConfigurationValidator validator =
      new PipelineConfigurationValidator(stageLibrary, pipelineName, realPipelineConfig);
    validator.validate();
    realPipelineConfig.setValidation(validator);
    realPipelineConfig =
      store.save(user, pipelineName, tag, desc, realPipelineConfig);
  }

  @Override
  public void startPipeline(String pipelineJson) throws Exception {
    Utils.checkNotNull(pipelineJson, "Pipeline Json string");
    ObjectMapper json = ObjectMapperFactory.getOneLine();
    PipelineConfigurationJson pipelineConfigBean = json.readValue(pipelineJson, PipelineConfigurationJson.class);
    realPipelineConfig = BeanHelper.unwrapPipelineConfiguration(pipelineConfigBean);
    if (task == null) {
      throw new IllegalStateException("Data collector has not been started");
    }
    pipelineTask = (PipelineTask) ((TaskWrapper)task).getTask();
    this.pipelineName = Utils.checkNotNull(realPipelineConfig.getInfo(), "Pipeline Info").getName();
    createAndSave(pipelineName);
    pipelineManager.getRunner(realPipelineConfig.getInfo().getCreator(), pipelineName, "1").start();
  }

  @Override
  public void createPipeline(String pipelineJson) throws Exception {
    throw new UnsupportedOperationException("This method is not supported. Use \"startPipeline\" method");

  }

  @Override
  public void startPipeline() throws Exception {
    throw new UnsupportedOperationException("This method is not supported. Use \"startPipeline\" method");
  }

  @Override
  public void stopPipeline() throws Exception {
    throw new UnsupportedOperationException("This method is not supported. Use \"startPipeline\" method");
  }

  @Override
  public void init() {
    final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    LOG.info("Entering Embedded SDC with ClassLoader: " + classLoader);
    LOG.info("Java classpath is " + System.getProperty("java.class.path"));
    dagger = ObjectGraph.create(MainStandalonePipelineManagerModule.class);
    task = dagger.get(TaskWrapper.class);
    pipelineTask = (PipelineTask) ((TaskWrapper)task).getTask();
    pipelineManager = pipelineTask.getManager();
    dagger.get(LogConfigurator.class).configure();
    LOG.info("-----------------------------------------------------------------");
    dagger.get(BuildInfo.class).log(LOG);
    LOG.info("-----------------------------------------------------------------");
    dagger.get(RuntimeInfo.class).log(LOG);
    LOG.info("-----------------------------------------------------------------");
    if (System.getSecurityManager() != null) {
      LOG.info("  Security Manager : ENABLED, policy file: {}", System.getProperty("java.security.policy"));
    } else {
      LOG.warn("  Security Manager : DISABLED");
    }
    LOG.info("-----------------------------------------------------------------");
    LOG.info("Starting ...");

    task.init();
    final Thread shutdownHookThread = new Thread("Main.shutdownHook") {
      @Override
      public void run() {
        LOG.debug("Stopping, reason: SIGTERM (kill)");
        task.stop();
      }
    };
    shutdownHookThread.setContextClassLoader(classLoader);
    Runtime.getRuntime().addShutdownHook(shutdownHookThread);
    dagger.get(RuntimeInfo.class).setShutdownHandler(new Runnable() {
      @Override
      public void run() {
        LOG.debug("Stopping, reason: requested");
        task.stop();
      }
    });
    task.run();

    // this thread waits until the pipeline is shutdown
    waitingThread = new Thread() {
      @Override
      public void run() {
        try {
          task.waitWhileRunning();
          try {
            Runtime.getRuntime().removeShutdownHook(shutdownHookThread);
          } catch (IllegalStateException ignored) {
          }
          LOG.debug("Stopping, reason: programmatic stop()");
        } catch(Throwable throwable) {
          String msg = "Error running pipeline: " + throwable;
          LOG.error(msg, throwable);
        }
      }
    };
    waitingThread.setContextClassLoader(classLoader);
    waitingThread.setName("Pipeline-" + pipelineName);
    waitingThread.setDaemon(true);
    waitingThread.start();
  }

  @Override
  public URI getServerURI() {
    URI serverURI;
    try {
      serverURI =  pipelineTask.getWebServerTask().getServerURI();
    } catch (ServerNotYetRunningException ex) {
      throw new RuntimeException("Cannot retrieve URI of server" + ex.getMessage(), ex);
    }
    return serverURI;
  }

  @Override
  public void destroy() {
    task.stop();
  }

  public Pipeline getPipeline() {
    return ((StandalonePipelineManagerTask)pipelineManager).getProductionPipeline().getPipeline();
  }

  @Override
  public List<URI> getWorkerList() throws URISyntaxException {
    List<URI> sdcURLList = new ArrayList<>();
/*
    for (CallbackInfo callBackInfo : pipelineManager.getSlaveCallbackList()) {
      sdcURLList.add(new URI(callBackInfo.getSdcURL()));
    }
*/
    return sdcURLList;
  }

}