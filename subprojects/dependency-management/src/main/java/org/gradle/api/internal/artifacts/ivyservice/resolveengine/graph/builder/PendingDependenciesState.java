/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder;

import com.google.common.collect.Maps;

import java.util.Map;

/**
 * Maintains the pending state of all modules seen during graph traversal.
 */
public class PendingDependenciesState {
    private static final PendingDependencies NOT_PENDING = PendingDependencies.notPending();

    private final Map<String, Map<String, PendingDependencies>> pendingDependencies = Maps.newHashMap();

    public PendingDependencies getPendingDependencies(String group, String module) {
        Map<String, PendingDependencies> pendingDependenciesForGroup = pendingDependenciesForGroup(group);
        PendingDependencies pendingDependencies = pendingDependenciesForGroup.get(module);
        if (pendingDependencies == null) {
            pendingDependencies = PendingDependencies.pending();
            pendingDependenciesForGroup.put(module, pendingDependencies);
        }
        return pendingDependencies;
    }

    public PendingDependencies notPending(String group, String module) {
        Map<String, PendingDependencies> pendingDependenciesForGroup = pendingDependenciesForGroup(group);
        return pendingDependenciesForGroup.put(module, NOT_PENDING);
    }

    private Map<String, PendingDependencies> pendingDependenciesForGroup(String group) {
        Map<String, PendingDependencies> map = pendingDependencies.get(group);
        if (map == null) {
            map = Maps.newHashMap();
            pendingDependencies.put(group, map);
        }
        return map;
    }
}
