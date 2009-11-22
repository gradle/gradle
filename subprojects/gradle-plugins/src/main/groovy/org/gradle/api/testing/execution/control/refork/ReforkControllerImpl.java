/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.testing.execution.control.refork;

import org.gradle.api.tasks.testing.NativeTest;
import org.gradle.api.testing.execution.PipelineConfig;
import org.gradle.api.testing.execution.Pipeline;

import java.util.*;

/**
 * @author Tom Eyckmans
 */
public class ReforkControllerImpl implements ReforkController {

    private final List<ReforkReasonKey> itemProcessorsOrdered;
    private final Map<ReforkReasonKey, ReforkReasonDataProcessor> itemProcessors;

    public ReforkControllerImpl() {
        itemProcessorsOrdered = new ArrayList<ReforkReasonKey>();
        itemProcessors = new HashMap<ReforkReasonKey, ReforkReasonDataProcessor>();
    }

    public void initialize(NativeTest testTask, PipelineConfig pipelineConfig) {
        final ReforkItemConfigs reforkItemConfigs = pipelineConfig.getReforkItemConfigs();

        for (ReforkReasonKey itemKey : reforkItemConfigs.getItemKeys()) {
            final ReforkReason item = ReforkReasonRegister.getDecisionContextItem(itemKey);
            final ReforkReasonDataProcessor itemDataProcessor = item.getDataProcessor();

            itemDataProcessor.configure(testTask.getProject(), testTask);

            itemProcessorsOrdered.add(itemKey);
            itemProcessors.put(itemKey, itemDataProcessor);
        }
    }

    public boolean reforkNeeded(Pipeline pipeline, int forkId, ReforkDecisionContext reforkDecisionContext) {
        boolean reforkNeeded = false;

        final Iterator<ReforkReasonKey> itemsIterator = itemProcessorsOrdered.iterator();

        while (!reforkNeeded && itemsIterator.hasNext()) {
            final ReforkReasonKey currentItemKey = itemsIterator.next();
            final Object currentItemData = reforkDecisionContext.getData(currentItemKey);

            if (currentItemData != null) {
                final ReforkReasonDataProcessor currentItemDataProcessor = itemProcessors.get(currentItemKey);

                reforkNeeded = currentItemDataProcessor.determineReforkNeeded(pipeline, forkId, currentItemData);
            }
        }

        return reforkNeeded;
    }
}
