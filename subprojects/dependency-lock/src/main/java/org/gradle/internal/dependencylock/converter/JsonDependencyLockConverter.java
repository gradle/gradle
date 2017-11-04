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

import com.google.gson.stream.JsonWriter;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.internal.dependencylock.model.DependencyLock;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;

public class JsonDependencyLockConverter implements DependencyLockConverter {

    private static final String INDENT = "  ";
    public static final String USER_NOTICE = "This is an auto-generated file and is not meant to be edited manually!";
    public static final String LOCK_FILE_VERSION = "1.0";

    @Override
    public String convert(DependencyLock dependencyLock) {
        Writer stringWriter = new StringWriter();
        JsonWriter writer = new JsonWriter(stringWriter);
        writer.setIndent(INDENT);

        try {
            writer.beginObject();
            writer.name("_comment").value(USER_NOTICE);
            writer.name("lockFileVersion").value(LOCK_FILE_VERSION);
            writer.name("locks");
            writer.beginArray();

            for (Map.Entry<String, SortedMap<String, LinkedHashMap<ModuleIdentifier, String>>> locksMapping : dependencyLock.getLocksMapping().entrySet()) {
                for (Map.Entry<String, LinkedHashMap<ModuleIdentifier, String>> configurationsMapping : locksMapping.getValue().entrySet()) {
                    writer.beginObject();
                    writer.name("id").value(buildLockId(locksMapping.getKey(), configurationsMapping.getKey()));
                    writer.name("dependencies");
                    writer.beginArray();

                    for (Map.Entry<ModuleIdentifier, String> lockedDependency : configurationsMapping.getValue().entrySet()) {
                            writer.beginObject();
                            writer.name("moduleId").value(lockedDependency.getKey().toString());
                            writer.name("lockedVersion").value(lockedDependency.getValue());
                            writer.endObject();
                    }

                    writer.endArray();
                    writer.endObject();
                }
            }

            writer.endArray();
            writer.endObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            try {
                writer.close();
            } catch (IOException e) {
                // ignore
            }
        }

        return stringWriter.toString();
    }

    private String buildLockId(String projectPath, String configurationName) {
        return projectPath + ":" + configurationName;
    }
}
