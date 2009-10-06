package org.gradle.api.testing.execution.control.refork;

import java.io.Serializable;
import java.util.*;

/**
 * @author Tom Eyckmans
 */
public class ReforkItemConfigs implements Serializable {
    private final List<DecisionContextItemKey> itemKeys;
    private final Map<DecisionContextItemKey, DecisionContextItemConfig> itemConfigs;

    public ReforkItemConfigs() {
        this.itemKeys = new ArrayList<DecisionContextItemKey>();
        this.itemConfigs = new HashMap<DecisionContextItemKey, DecisionContextItemConfig>();
    }

    public List<DecisionContextItemKey> getItemKeys() {
        return Collections.unmodifiableList(itemKeys);
    }

    public Map<DecisionContextItemKey, DecisionContextItemConfig> getItemConfigs() {
        return Collections.unmodifiableMap(itemConfigs);
    }

    public void addItem(DecisionContextItemKey itemKey) {
        if (!itemKeys.contains(itemKey))
            itemKeys.add(itemKey);
    }

    public void addItemConfig(DecisionContextItemKey itemKey, DecisionContextItemConfig itemConfig) {
        addItem(itemKey);

        if (itemConfig != null) {
            itemConfigs.put(itemKey, itemConfig);
        }
    }
}
