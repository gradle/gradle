package org.gradle.api.testing.execution.control.refork;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.*;

/**
 * @author Tom Eyckmans
 */
public class ReforkDecisionContextImpl implements ReforkDecisionContext {
    private List<DecisionContextItemKey> itemKeys;
    private Map<DecisionContextItemKey, Object> itemData;

    public ReforkDecisionContextImpl() {
        itemKeys = new ArrayList<DecisionContextItemKey>();
        itemData = new HashMap<DecisionContextItemKey, Object>();
    }

    public List<DecisionContextItemKey> getItemKeys() {
        return Collections.unmodifiableList(itemKeys);
    }

    public Map<DecisionContextItemKey, Object> getItemData() {
        return Collections.unmodifiableMap(itemData);
    }

    public void addItem(DecisionContextItemKey itemKey, Object itemData) {
        if (!itemKeys.contains(itemKey)) {
            itemKeys.add(itemKey);
        }
        if (itemData != null) {
            this.itemData.put(itemKey, itemData);
        }
    }

    public Object getData(DecisionContextItemKey itemKey) {
        return itemData.get(itemKey);
    }

    public boolean isEmpty() {
        return itemData.isEmpty();
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeObject(itemKeys);
        out.writeObject(itemData);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        itemKeys = (List<DecisionContextItemKey>) in.readObject();
        itemData = (Map<DecisionContextItemKey, Object>) in.readObject();
    }
}
