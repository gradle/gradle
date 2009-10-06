package org.gradle.api.testing.execution.control.refork;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Tom Eyckmans
 */
public class DecisionContextItems {
    private static final DecisionContextItems INSTANCE = new DecisionContextItems();

    static {
        addDecisionContextItem(new AmountOfTestsExecutedByForkItem());
    }

    private final Map<DecisionContextItemKey, DecisionContextItem> decisionContextItems;

    private DecisionContextItems() {
        decisionContextItems = new ConcurrentHashMap<DecisionContextItemKey, DecisionContextItem>();
    }

    public static void addDecisionContextItem(DecisionContextItem decisionContextItem) {
        if (decisionContextItem == null) throw new IllegalArgumentException("decisionContextItem == null!");

        INSTANCE.decisionContextItems.put(decisionContextItem.getKey(), decisionContextItem);
    }

    public static DecisionContextItem getDecisionContextItem(DecisionContextItemKey decisionContextItemKey) {
        if (decisionContextItemKey == null) throw new IllegalArgumentException("decisionContextItemKey == null!");

        return INSTANCE.decisionContextItems.get(decisionContextItemKey);
    }
}
