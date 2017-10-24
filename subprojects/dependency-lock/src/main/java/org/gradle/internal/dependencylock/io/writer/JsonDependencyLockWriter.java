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

package org.gradle.internal.dependencylock.io.writer;

import org.gradle.api.UncheckedIOException;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.internal.dependencylock.model.DependencyLock;
import org.gradle.internal.dependencylock.model.DependencyVersion;
import org.gradle.util.GFileUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class JsonDependencyLockWriter implements DependencyLockWriter {

    private final File lockFile;

    public JsonDependencyLockWriter(File lockFile) {
        this.lockFile = lockFile;
    }

    @Override
    public void write(DependencyLock dependencyLock) {
        if (!dependencyLock.getMapping().isEmpty()) {
            JSONArray allLocks = createJson(dependencyLock);
            writeFile(lockFile, allLocks);
        }
    }

    private JSONArray createJson(DependencyLock dependencyLock) {
        JSONArray allLocks = new JSONArray();

        for (Map.Entry<String, LinkedHashMap<ModuleIdentifier, DependencyVersion>> mapping : dependencyLock.getMapping().entrySet()) {
            JSONObject configuration = new JSONObject();
            JSONArray dependencies = new JSONArray();

            for (Map.Entry<ModuleIdentifier, DependencyVersion> lockedDependency : mapping.getValue().entrySet()) {
                JSONObject dependency = new JSONObject();
                dependency.put("coordinates", lockedDependency.getKey().toString());
                dependency.put("requestedVersion", lockedDependency.getValue().getRequestedVersion());
                dependency.put("lockedVersion", lockedDependency.getValue().getSelectedVersion());
                dependencies.add(dependency);
            }

            configuration.put("configuration", mapping.getKey());
            configuration.put("dependencies", dependencies);
            allLocks.add(configuration);
        }

        return allLocks;
    }

    private void writeFile(File lockFile, JSONArray allLocks) {
        GFileUtils.parentMkdirs(lockFile);
        FileWriter fileWriter = null;

        try {
            fileWriter = new FileWriter(lockFile);
            fileWriter.write(allLocks.toJSONString());
            fileWriter.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            try {
                if (fileWriter != null) {
                    fileWriter.close();
                }
            } catch (IOException e) {
                // ignore
            }
        }
    }
}
