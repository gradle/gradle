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

    private final SortedMap<String, SortedMap<String, LinkedHashMap<ModuleIdentifier, DependencyVersion>>> projectsMapping = new TreeMap<String,  SortedMap<String, LinkedHashMap<ModuleIdentifier, DependencyVersion>>>();

    public void addDependency(String projectPath, String configurationName, ModuleIdentifier moduleIdentifier, DependencyVersion dependencyVersion) {
        if (projectsMapping.containsKey(projectPath)) {
            SortedMap<String, LinkedHashMap<ModuleIdentifier, DependencyVersion>> project = projectsMapping.get(projectPath);

            if (!project.containsKey(configurationName)) {
                project.put(configurationName, new LinkedHashMap<ModuleIdentifier, DependencyVersion>());
            }

            project.get(configurationName).put(moduleIdentifier, dependencyVersion);
        } else {
            SortedMap<String, LinkedHashMap<ModuleIdentifier, DependencyVersion>> configurationMapping = createNewConfigurationMapping(configurationName);
            LinkedHashMap<ModuleIdentifier, DependencyVersion> modules = new LinkedHashMap<ModuleIdentifier, DependencyVersion>();
            modules.put(moduleIdentifier, dependencyVersion);
            configurationMapping.put(configurationName, modules);
            projectsMapping.put(projectPath, configurationMapping);
        }
    }

    private SortedMap<String, LinkedHashMap<ModuleIdentifier, DependencyVersion>> createNewConfigurationMapping(String configurationName) {
        SortedMap<String, LinkedHashMap<ModuleIdentifier, DependencyVersion>> configurationMapping = new TreeMap<String, LinkedHashMap<ModuleIdentifier, DependencyVersion>>();
        LinkedHashMap<ModuleIdentifier, DependencyVersion> modules = new LinkedHashMap<ModuleIdentifier, DependencyVersion>();
        configurationMapping.put(configurationName, modules);
        return configurationMapping;
    }

    public Map<String, SortedMap<String, LinkedHashMap<ModuleIdentifier, DependencyVersion>>> getProjectsMapping() {
        return projectsMapping;
    }
}
