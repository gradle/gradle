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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class DependencyLock {

    private final SortedMap<String, SortedMap<String, LinkedHashMap<ModuleIdentifier, List<DependencyVersion>>>> projectsMapping = new TreeMap<String,  SortedMap<String, LinkedHashMap<ModuleIdentifier, List<DependencyVersion>>>>();

    public void addDependency(String projectPath, String configurationName, ModuleIdentifier moduleIdentifier, DependencyVersion dependencyVersion) {
        if (projectsMapping.containsKey(projectPath)) {
            SortedMap<String, LinkedHashMap<ModuleIdentifier, List<DependencyVersion>>> project = projectsMapping.get(projectPath);
            LinkedHashMap<ModuleIdentifier, List<DependencyVersion>> modulesMapping = createAndGetModulesMapping(project, configurationName);

            if (!modulesMapping.containsKey(moduleIdentifier)) {
                startCapturingDependenciesForModule(modulesMapping, moduleIdentifier, dependencyVersion);
            } else {
                modulesMapping.get(moduleIdentifier).add(dependencyVersion);
            }
        } else {
            SortedMap<String, LinkedHashMap<ModuleIdentifier, List<DependencyVersion>>> configurationMapping = createNewConfigurationMapping(configurationName);
            LinkedHashMap<ModuleIdentifier, List<DependencyVersion>> modulesMapping = new LinkedHashMap<ModuleIdentifier, List<DependencyVersion>>();
            startCapturingDependenciesForModule(modulesMapping, moduleIdentifier, dependencyVersion);
            configurationMapping.put(configurationName, modulesMapping);
            projectsMapping.put(projectPath, configurationMapping);
        }
    }

    private LinkedHashMap<ModuleIdentifier, List<DependencyVersion>> createAndGetModulesMapping(SortedMap<String, LinkedHashMap<ModuleIdentifier, List<DependencyVersion>>> project, String configurationName) {
        if (!project.containsKey(configurationName)) {
            project.put(configurationName, new LinkedHashMap<ModuleIdentifier, List<DependencyVersion>>());
        }

        return project.get(configurationName);
    }

    private void startCapturingDependenciesForModule(LinkedHashMap<ModuleIdentifier, List<DependencyVersion>> modulesMapping, ModuleIdentifier moduleIdentifier, DependencyVersion dependencyVersion) {
        List<DependencyVersion> dependencies = new ArrayList<DependencyVersion>();
        dependencies.add(dependencyVersion);
        modulesMapping.put(moduleIdentifier, dependencies);
    }

    private SortedMap<String, LinkedHashMap<ModuleIdentifier, List<DependencyVersion>>> createNewConfigurationMapping(String configurationName) {
        SortedMap<String, LinkedHashMap<ModuleIdentifier,  List<DependencyVersion>>> configurationMapping = new TreeMap<String, LinkedHashMap<ModuleIdentifier,  List<DependencyVersion>>>();
        configurationMapping.put(configurationName, new LinkedHashMap<ModuleIdentifier, List<DependencyVersion>>());
        return configurationMapping;
    }

    public Map<String, SortedMap<String, LinkedHashMap<ModuleIdentifier, List<DependencyVersion>>>> getProjectsMapping() {
        return projectsMapping;
    }
}
