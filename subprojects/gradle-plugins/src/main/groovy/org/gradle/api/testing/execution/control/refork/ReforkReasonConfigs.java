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
public class ReforkReasonConfigs implements Serializable {
    private final List<ReforkReasonKey> keys;
    private final Map<ReforkReasonKey, ReforkReasonConfig> configs;

    public ReforkReasonConfigs() {
        this.keys = new ArrayList<ReforkReasonKey>();
        this.configs = new HashMap<ReforkReasonKey, ReforkReasonConfig>();
    }

    public List<ReforkReasonKey> getKeys() {
        return Collections.unmodifiableList(keys);
    }

    public Map<ReforkReasonKey, ReforkReasonConfig> getConfigs() {
        return Collections.unmodifiableMap(configs);
    }

    public void addOrUpdateReforkReasonConfig(ReforkReasonConfig config) {
        if ( config == null ) {
            throw new IllegalArgumentException("itemConfig can't be null!");
        }

        final ReforkReasonKey key = config.getKey();

        if ( key == null ) {
            throw new IllegalArgumentException("itemKey can't be null!");
        }

        if (!keys.contains(key)) {
            keys.add(key);
        }
        
        configs.put(key, config);
    }
}
