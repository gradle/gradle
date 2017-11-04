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

    private final SortedMap<String, SortedMap<String, LinkedHashMap<ModuleIdentifier, String>>> locksMapping = new TreeMap<String,  SortedMap<String, LinkedHashMap<ModuleIdentifier, String>>>();

    public void addDependency(String projectPath, String configurationName, ModuleIdentifier moduleIdentifier, String resolvedVersion) {
        if (locksMapping.containsKey(projectPath)) {
            SortedMap<String, LinkedHashMap<ModuleIdentifier, String>> project = locksMapping.get(projectPath);

            if (!project.containsKey(configurationName)) {
                project.put(configurationName, new LinkedHashMap<ModuleIdentifier, String>());
            }

            project.get(configurationName).put(moduleIdentifier, resolvedVersion);
        } else {
            SortedMap<String, LinkedHashMap<ModuleIdentifier, String>> configurationMapping = createNewConfigurationMapping(configurationName);
            LinkedHashMap<ModuleIdentifier, String> modules = new LinkedHashMap<ModuleIdentifier, String>();
            modules.put(moduleIdentifier, resolvedVersion);
            configurationMapping.put(configurationName, modules);
            locksMapping.put(projectPath, configurationMapping);
        }
    }

    private SortedMap<String, LinkedHashMap<ModuleIdentifier, String>> createNewConfigurationMapping(String configurationName) {
        SortedMap<String, LinkedHashMap<ModuleIdentifier, String>> configurationMapping = new TreeMap<String, LinkedHashMap<ModuleIdentifier, String>>();
        LinkedHashMap<ModuleIdentifier, String> modules = new LinkedHashMap<ModuleIdentifier, String>();
        configurationMapping.put(configurationName, modules);
        return configurationMapping;
    }

    public Map<String, SortedMap<String, LinkedHashMap<ModuleIdentifier, String>>> getLocksMapping() {
        return locksMapping;
    }
}
