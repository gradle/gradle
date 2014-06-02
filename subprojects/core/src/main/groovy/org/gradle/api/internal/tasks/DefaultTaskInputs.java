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
import groovy.lang.GString;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.UnionFileCollection;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.api.tasks.TaskInputs;
import org.gradle.internal.UncheckedException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

public class DefaultTaskInputs implements TaskInputs {
    private final DefaultConfigurableFileCollection inputFiles;
    private final DefaultConfigurableFileCollection sourceFiles;
    private final FileResolver resolver;
    private final TaskMutator taskMutator;
    private final Map<String, Object> properties = new HashMap<String, Object>();

    public DefaultTaskInputs(FileResolver resolver, TaskInternal task, TaskMutator taskMutator) {
        this.resolver = resolver;
        this.taskMutator = taskMutator;
        inputFiles = new DefaultConfigurableFileCollection(String.format("%s input files", task), resolver, null);
        sourceFiles = new DefaultConfigurableFileCollection(String.format("%s source files", task), resolver, null);
    }

    public boolean getHasInputs() {
        return !inputFiles.getFrom().isEmpty() || !properties.isEmpty() || !sourceFiles.getFrom().isEmpty();
    }

    public FileCollection getFiles() {
        return new UnionFileCollection(inputFiles, sourceFiles);
    }

    public TaskInputs files(final Object... paths) {
        taskMutator.mutate("TaskInputs.files(Object...)", new Runnable() {
            public void run() {
                inputFiles.from(paths);
            }
        });
        return this;
    }

    public TaskInputs file(final Object path) {
        taskMutator.mutate("TaskInputs.file(Object)", new Runnable() {
            public void run() {
                inputFiles.from(path);
            }
        });
        return this;
    }

    public TaskInputs dir(final Object dirPath) {
        taskMutator.mutate("TaskInputs.dir(Object)", new Runnable() {
            public void run() {
                inputFiles.from(resolver.resolveFilesAsTree(dirPath));
            }
        });
        return this;
    }

    public boolean getHasSourceFiles() {
        return !sourceFiles.getFrom().isEmpty();
    }

    public FileCollection getSourceFiles() {
        return sourceFiles;
    }

    public TaskInputs source(final Object... paths) {
        taskMutator.mutate("TaskInputs.source(Object...)", new Runnable() {
            public void run() {
                sourceFiles.from(paths);
            }
        });
        return this;
    }

    public TaskInputs source(final Object path) {
        taskMutator.mutate("TaskInputs.source(Object)", new Runnable() {
            public void run() {
                sourceFiles.from(path);
            }
        });
        return this;
    }

    public TaskInputs sourceDir(final Object path) {
        taskMutator.mutate("TaskInputs.sourceDir(Object)", new Runnable() {
            public void run() {
                sourceFiles.from(resolver.resolveFilesAsTree(path));
            }
        });
        return this;
    }

    public Map<String, Object> getProperties() {
        Map<String, Object> actualProperties = new HashMap<String, Object>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            Object value = prepareValue(entry.getValue());
            actualProperties.put(entry.getKey(), value);
        }
        return actualProperties;
    }

    private Object prepareValue(Object value) {
        while (true) {
            if (value instanceof Callable) {
                Callable callable = (Callable) value;
                try {
                    value = callable.call();
                } catch (Exception e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            } else if (value instanceof Closure) {
                Closure closure = (Closure) value;
                value = closure.call();
            } else if (value instanceof FileCollection) {
                FileCollection fileCollection = (FileCollection) value;
                return fileCollection.getFiles();
            } else {
                return avoidGString(value);
            }
        }
    }

    private static Object avoidGString(Object value) {
        return (value instanceof GString)? value.toString() : value;
    }

    public TaskInputs property(final String name, final Object value) {
        taskMutator.mutate("TaskInputs.property(String, Object)", new Runnable() {
            public void run() {
                properties.put(name, value);
            }
        });
        return this;
    }

    public TaskInputs properties(final Map<String, ?> newProps) {
        taskMutator.mutate("TaskInputs.properties(Map)", new Runnable() {
            public void run() {
                properties.putAll(newProps);
            }
        });
        return this;
    }
}
