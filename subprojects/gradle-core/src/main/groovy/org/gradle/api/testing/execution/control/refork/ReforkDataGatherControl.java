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
