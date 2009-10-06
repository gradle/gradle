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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Tom Eyckmans
 */
public class ReforkDataGatherControl {

    private final Map<DataGatherMoment, List<DecisionContextItemDataGatherer>> dataGatherers;

    public ReforkDataGatherControl() {
        dataGatherers = new HashMap<DataGatherMoment, List<DecisionContextItemDataGatherer>>();
    }

    public void initialize(final ReforkItemConfigs reforkItemConfigs) {
        final Map<DecisionContextItemKey, DecisionContextItemConfig> itemConfigs = reforkItemConfigs.getItemConfigs();
        for (final DecisionContextItemKey itemKey : reforkItemConfigs.getItemKeys()) {
            final DecisionContextItem item = DecisionContextItems.getDecisionContextItem(itemKey);

            final DecisionContextItemDataGatherer dataGatherer = item.getDataGatherer();

            final DecisionContextItemConfig itemConfig = itemConfigs.get(itemKey);
            if (itemConfig != null) {
                dataGatherer.configure(itemConfig);
            }

            final List<DataGatherMoment> dataGatherMoments = dataGatherer.getDataGatherMoments();
            if (dataGatherMoments != null && !dataGatherMoments.isEmpty()) {
                for (final DataGatherMoment dataGatherMoment : dataGatherMoments) {
                    List<DecisionContextItemDataGatherer> momentDataGatherers = dataGatherers.get(dataGatherMoment);
                    if (momentDataGatherers == null) {
                        momentDataGatherers = new ArrayList<DecisionContextItemDataGatherer>();
                    }
                    momentDataGatherers.add(dataGatherer);
                    dataGatherers.put(dataGatherMoment, momentDataGatherers);
                }
            }
        }
    }

    public ReforkDecisionContext gatherData(DataGatherMoment moment, Object... momentData) {
        ReforkDecisionContext reforkDecisionContext = new ReforkDecisionContextImpl();

        final List<DecisionContextItemDataGatherer> momentDataGatherers = dataGatherers.get(moment);

        if (momentDataGatherers != null && !momentDataGatherers.isEmpty()) {

            for (final DecisionContextItemDataGatherer dataGatherer : momentDataGatherers) {
                try {
                    if (dataGatherer.processDataGatherMoment(moment, momentData)) {
                        reforkDecisionContext.addItem(dataGatherer.getItemKey(), dataGatherer.getCurrentData());
                    }
                }
                catch (Throwable t) {
                    t.printStackTrace();
                }
            }

        } else {
            System.out.println("no data gatherers for " + moment);
        }

        if (reforkDecisionContext.isEmpty())
            return null;
        else
            return reforkDecisionContext;
    }

}
