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
