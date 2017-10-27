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

package org.gradle.internal.dependencylock.converter;

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.internal.dependencylock.model.DependencyLock;
import org.gradle.internal.dependencylock.model.DependencyVersion;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;

public class JsonDependencyLockConverter implements DependencyLockConverter {

    private static final String USER_NOTICE = "This is an auto-generated file and is not meant to be edited manually!";
    private static final String LOCK_FILE_VERSION = "1.0";

    @SuppressWarnings("unchecked")
    @Override
    public String convert(DependencyLock dependencyLock) {
        JSONObject allLocks = new JSONObject();
        allLocks.put("_comment", USER_NOTICE);
        allLocks.put("lockFileVersion", LOCK_FILE_VERSION);
        JSONArray projects = new JSONArray();

        for (Map.Entry<String, SortedMap<String, LinkedHashMap<ModuleIdentifier, DependencyVersion>>> projectsMapping : dependencyLock.getProjectsMapping().entrySet()) {
            JSONObject project = new JSONObject();
            project.put("path", projectsMapping.getKey());
            projects.add(project);
            JSONArray configurations = new JSONArray();
            project.put("configurations", configurations);

            for (Map.Entry<String, LinkedHashMap<ModuleIdentifier, DependencyVersion>> configurationsMapping : projectsMapping.getValue().entrySet()) {
                JSONObject configuration = new JSONObject();
                JSONArray dependencies = new JSONArray();

                for (Map.Entry<ModuleIdentifier, DependencyVersion> lockedDependency : configurationsMapping.getValue().entrySet()) {
                    JSONObject dependency = new JSONObject();
                    dependency.put("moduleId", lockedDependency.getKey().toString());
                    dependency.put("requestedVersion", lockedDependency.getValue().getRequestedVersion());
                    dependency.put("lockedVersion", lockedDependency.getValue().getSelectedVersion());
                    dependencies.add(dependency);
                }

                configuration.put("name", configurationsMapping.getKey());
                configuration.put("dependencies", dependencies);
                configurations.add(configuration);
            }

            allLocks.put("projects", projects);
        }

        return allLocks.toJSONString();
    }
}
