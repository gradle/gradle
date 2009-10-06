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
