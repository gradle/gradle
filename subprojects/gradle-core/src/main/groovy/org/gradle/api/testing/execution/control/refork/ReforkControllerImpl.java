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

import java.util.*;

/**
 * @author Tom Eyckmans
 */
public class ReforkControllerImpl implements ReforkController {

    private final List<DecisionContextItemKey> itemProcessorsOrdered;
    private final Map<DecisionContextItemKey, DecisionContextItemDataProcessor> itemProcessors;

    public ReforkControllerImpl() {
        itemProcessorsOrdered = new ArrayList<DecisionContextItemKey>();
        itemProcessors = new HashMap<DecisionContextItemKey, DecisionContextItemDataProcessor>();
    }

    public void initialize(NativeTest testTask, PipelineConfig pipelineConfig) {
        final ReforkItemConfigs reforkItemConfigs = pipelineConfig.getReforkItemConfigs();

        for (DecisionContextItemKey itemKey : reforkItemConfigs.getItemKeys()) {
            final DecisionContextItem item = DecisionContextItems.getDecisionContextItem(itemKey);
            final DecisionContextItemDataProcessor itemDataProcessor = item.getDataProcessor();

            itemDataProcessor.configure(testTask.getProject(), testTask);

            itemProcessorsOrdered.add(itemKey);
            itemProcessors.put(itemKey, itemDataProcessor);
        }
    }

    public boolean reforkNeeded(ReforkDecisionContext reforkDecisionContext) {
        boolean reforkNeeded = false;

        final Iterator<DecisionContextItemKey> itemsIterator = itemProcessorsOrdered.iterator();

        while (!reforkNeeded && itemsIterator.hasNext()) {
            final DecisionContextItemKey currentItemKey = itemsIterator.next();
            final Object currentItemData = reforkDecisionContext.getData(currentItemKey);

            if (currentItemData != null) {
                final DecisionContextItemDataProcessor currentItemDataProcessor = itemProcessors.get(currentItemKey);

                reforkNeeded = currentItemDataProcessor.determineReforkNeeded(currentItemData);
            }
        }

        return reforkNeeded;
    }
}
