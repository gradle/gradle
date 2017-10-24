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

package org.gradle.internal.dependencylock.model;

import org.gradle.api.artifacts.ModuleIdentifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class DependencyLock {

    private final SortedMap<String, LinkedHashMap<ModuleIdentifier, DependencyVersion>> mapping = new TreeMap<String, LinkedHashMap<ModuleIdentifier, DependencyVersion>>();

    public void addDependency(String configurationName, ModuleIdentifier moduleIdentifier, DependencyVersion dependencyVersion) {
        if (mapping.containsKey(configurationName)) {
            mapping.get(configurationName).put(moduleIdentifier, dependencyVersion);
        } else {
            LinkedHashMap<ModuleIdentifier, DependencyVersion> modules = new LinkedHashMap<ModuleIdentifier, DependencyVersion>();
            modules.put(moduleIdentifier, dependencyVersion);
            mapping.put(configurationName, modules);
        }
    }

    public Map<String, LinkedHashMap<ModuleIdentifier, DependencyVersion>> getMapping() {
        return mapping;
    }
}
