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

import org.gradle.api.artifacts.ModuleVersionIdentifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class DependencyLock {

    private final SortedMap<String, LinkedHashMap<GroupAndName, DependencyVersion>> mapping = new TreeMap<String, LinkedHashMap<GroupAndName, DependencyVersion>>();

    public void addDependency(String configurationName, ModuleVersionIdentifier moduleVersionIdentifier) {
        if (mapping.containsKey(configurationName)) {
            DependencyVersion dependencyVersion = new DependencyVersion();
            dependencyVersion.setDeclaredVersion(moduleVersionIdentifier.getVersion());
            mapping.get(configurationName).put(new GroupAndName(moduleVersionIdentifier.getGroup(), moduleVersionIdentifier.getName()), dependencyVersion);
        } else {
            LinkedHashMap<GroupAndName, DependencyVersion> modules = new LinkedHashMap<GroupAndName, DependencyVersion>();
            DependencyVersion dependencyVersion = new DependencyVersion();
            dependencyVersion.setDeclaredVersion(moduleVersionIdentifier.getVersion());
            modules.put(new GroupAndName(moduleVersionIdentifier.getGroup(), moduleVersionIdentifier.getName()), dependencyVersion);
            mapping.put(configurationName, modules);
        }
    }

    public Map<String, LinkedHashMap<GroupAndName, DependencyVersion>> getMapping() {
        return mapping;
    }
}
