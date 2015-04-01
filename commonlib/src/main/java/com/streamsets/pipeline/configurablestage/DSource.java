/**
 * (c) 2014 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.configurablestage;

import com.streamsets.pipeline.api.BatchMaker;
import com.streamsets.pipeline.api.Source;
import com.streamsets.pipeline.api.Stage;
import com.streamsets.pipeline.api.StageException;

public abstract class DSource extends DStage<Source.Context> implements Source {

  protected abstract Source createSource();

  @Override
  Stage<Source.Context> createStage() {
    return createSource();
  }

  @Override
  public final String produce(String lastSourceOffset, int maxBatchSize, BatchMaker batchMaker) throws StageException {
    return ((Source)getStage()).produce(lastSourceOffset, maxBatchSize, batchMaker);
  }

}