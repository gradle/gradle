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

package org.gradle.internal.dependencylock.reader;

import org.gradle.api.UncheckedIOException;
import org.gradle.internal.dependencylock.model.DependencyLock;
import org.gradle.internal.dependencylock.model.DependencyVersion;
import org.gradle.internal.dependencylock.model.GroupAndName;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedHashMap;

public class JsonDependencyLockReader implements DependencyLockReader {

    private final File lockFile;

    public JsonDependencyLockReader(File lockFile) {
        this.lockFile = lockFile;
    }

    @Override
    public DependencyLock read() {
        DependencyLock dependencyLock = new DependencyLock();

        if (lockFile.isFile()) {
            JSONParser parser = new JSONParser();

            try {
                Object json = parser.parse(new FileReader(lockFile));
                JSONArray allLocks = (JSONArray)json;

                for (Object lock : allLocks) {
                    JSONObject configurationBasedLock = (JSONObject)lock;
                    String configurationName = (String)configurationBasedLock.get("configuration");
                    JSONArray dependencies = (JSONArray)configurationBasedLock.get("dependencies");
                    LinkedHashMap<GroupAndName, DependencyVersion> dependencyMapping = new LinkedHashMap<GroupAndName, DependencyVersion>();
                    dependencyLock.getMapping().put(configurationName, dependencyMapping);

                    for (Object dependency : dependencies) {
                        JSONObject dependencyObject = (JSONObject) dependency;
                        String coordinates = (String)dependencyObject.get("coordinates");
                        String[] groupAndNameString = coordinates.split(":");
                        String requestedVersion = (String)dependencyObject.get("requestedVersion");
                        String lockedVersion = (String)dependencyObject.get("lockedVersion");
                        GroupAndName groupAndName = new GroupAndName(groupAndNameString[0], groupAndNameString[1]);
                        DependencyVersion dependencyVersion = new DependencyVersion();
                        dependencyVersion.setDeclaredVersion(requestedVersion);
                        dependencyVersion.setResolvedVersion(lockedVersion);
                        dependencyMapping.put(groupAndName, dependencyVersion);
                    }
                }
            } catch (ParseException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        return dependencyLock;
    }
}
