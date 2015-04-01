/**
 * (c) 2014 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.configurablestage;

import com.streamsets.pipeline.api.Stage;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.api.el.ELEval;

import java.util.List;

public abstract class DStage<C extends Stage.Context> implements Stage<C> {
  private Stage<C> stage;

  Stage<C> getStage() {
    return stage;
  }

  abstract Stage<C> createStage();

  @Override
  public final List<ConfigIssue> validateConfigs(Info info, C context) throws StageException {
    if(stage == null) {
      stage = createStage();
    }
    return stage.validateConfigs(info, context);
  }

  @Override
  public List<ELEval> getELEvals(ELContext elContext) {
    if(stage == null) {
      stage = createStage();
    }
    return stage.getELEvals(elContext);
  }

  @Override
  public final void init(Info info, C context) throws StageException {
    stage.init(info, context);
  }

  @Override
  public final void destroy() {
    stage.destroy();
  }

}