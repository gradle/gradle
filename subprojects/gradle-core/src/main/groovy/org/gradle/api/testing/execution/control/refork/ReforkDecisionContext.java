package org.gradle.api.testing.execution.control.refork;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * @author Tom Eyckmans
 */
public interface ReforkDecisionContext extends Serializable {

    List<DecisionContextItemKey> getItemKeys();

    Map<DecisionContextItemKey, Object> getItemData();

    void addItem(DecisionContextItemKey itemKey, Object itemData);

    Object getData(DecisionContextItemKey itemKey);

    boolean isEmpty();
}
