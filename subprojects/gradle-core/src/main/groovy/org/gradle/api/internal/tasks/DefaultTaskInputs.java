/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.tasks;

import groovy.lang.Closure;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.PathResolvingFileCollection;
import org.gradle.api.tasks.TaskInputs;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

public class DefaultTaskInputs implements TaskInputs {
    private final PathResolvingFileCollection inputFiles;
    private final FileResolver resolver;
    private final Map<String, Object> properties = new HashMap<String, Object>();

    public DefaultTaskInputs(FileResolver resolver) {
        this.resolver = resolver;
        inputFiles = new PathResolvingFileCollection("task input files", resolver, null);
    }

    public boolean getHasInputs() {
        return !inputFiles.getSources().isEmpty() || !properties.isEmpty();
    }

    public FileCollection getFiles() {
        return inputFiles;
    }

    public TaskInputs files(Object... paths) {
        inputFiles.from(paths);
        return this;
    }

    public TaskInputs dir(Object dirPath) {
        inputFiles.from(resolver.resolveFilesAsTree(dirPath));
        return this;
    }

    public Map<String, Object> getProperties() {
        Map<String, Object> actualProperties = new HashMap<String, Object>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Callable) {
                Callable callable = (Callable) value;
                try {
                    value = callable.call();
                } catch (Exception e) {
                    throw new GradleException(e);
                }
            }
            if (value instanceof Closure) {
                Closure closure = (Closure) value;
                value = closure.call();
            }
            actualProperties.put(entry.getKey(), value);
        }
        return actualProperties;
    }

    public TaskInputs property(String name, Object value) {
        properties.put(name, value);
        return this;
    }

    public TaskInputs properties(Map<String, ?> properties) {
        this.properties.putAll(properties);
        return this;
    }
}
