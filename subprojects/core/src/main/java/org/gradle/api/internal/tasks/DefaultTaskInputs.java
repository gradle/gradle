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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import groovy.lang.Closure;
import groovy.lang.GString;
import org.gradle.api.Action;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInputsInternal;
import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.tasks.TaskInputFilePropertySpec;
import org.gradle.api.tasks.TaskInputs;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.DeprecationLogger;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Callable;

import static org.gradle.util.GUtil.uncheckedCall;

public class DefaultTaskInputs implements TaskInputsInternal {
    private final FileCollection allInputFiles;
    private final FileCollection allSourceFiles;
    private final FileResolver resolver;
    private final String taskName;
    private final TaskMutator taskMutator;
    private final Map<String, Object> properties = new HashMap<String, Object>();
    private final List<PropertySpec> fileProperties = Lists.newArrayList();
    private int legacyFilePropertyCounter;
    private Queue<Action<? super TaskInputs>> configureActions;

    public DefaultTaskInputs(FileResolver resolver, String taskName, TaskMutator taskMutator) {
        this.resolver = resolver;
        this.taskName = taskName;
        this.taskMutator = taskMutator;
        this.allInputFiles = new TaskInputUnionFileCollection("task '" + taskName + "' input files", false);
        this.allSourceFiles = new TaskInputUnionFileCollection("task '" + taskName + "' source files", true);
    }

    @Override
    public boolean getHasInputs() {
        return !fileProperties.isEmpty() || !properties.isEmpty();
    }

    @Override
    public FileCollection getFiles() {
        return allInputFiles;
    }

    @Override
    public Collection<TaskInputFilePropertySpecInternal> getFileProperties() {
        ImmutableList.Builder<TaskInputFilePropertySpecInternal> builder = ImmutableList.builder();
        for (PropertySpec propertySpec : fileProperties) {
            if (propertySpec.getPropertyName() == null) {
                propertySpec.withPropertyName(nextLegacyPropertyName());
            }
            builder.add(propertySpec);
        }
        return builder.build();
    }

    @Override
    public TaskInputFilePropertySpec files(final Object... paths) {
        return taskMutator.mutate("TaskInputs.files(Object...)", new Callable<TaskInputFilePropertySpec>() {
            @Override
            public TaskInputFilePropertySpec call() {
                return addSpec(paths);
            }
        });
    }

    @Override
    public TaskInputFilePropertySpec file(final Object path) {
        return taskMutator.mutate("TaskInputs.file(Object)", new Callable<TaskInputFilePropertySpec>() {
            @Override
            public TaskInputFilePropertySpec call() {
                return addSpec(path);
            }
        });
    }

    @Override
    public TaskInputFilePropertySpec dir(final Object dirPath) {
        return taskMutator.mutate("TaskInputs.dir(Object)", new Callable<TaskInputFilePropertySpec>() {
            @Override
            public TaskInputFilePropertySpec call() {
                return addSpec(resolver.resolveFilesAsTree(dirPath));
            }
        });
    }

    @Override
    public boolean getHasSourceFiles() {
        for (PropertySpec propertySpec : fileProperties) {
            if (propertySpec.isSkipWhenEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public FileCollection getSourceFiles() {
        return allSourceFiles;
    }

    @Override
    public TaskInputs source(final Object... paths) {
        DeprecationLogger.nagUserOfDiscontinuedMethod("TaskInputs.source(Object...)", "Please use TaskInputs.files(Object...).skipWhenEmpty() instead.");
        taskMutator.mutate("TaskInputs.source(Object...)", new Runnable() {
            @Override
            public void run() {
                addSpec(paths, true);
            }
        });
        return this;
    }

    @Override
    public TaskInputs source(final Object path) {
        DeprecationLogger.nagUserOfDiscontinuedMethod("TaskInputs.source(Object)", "Please use TaskInputs.file(Object).skipWhenEmpty() instead.");
        taskMutator.mutate("TaskInputs.source(Object)", new Runnable() {
            @Override
            public void run() {
                addSpec(path, true);
            }
        });
        return this;
    }

    @Override
    public TaskInputs sourceDir(final Object path) {
        DeprecationLogger.nagUserOfDiscontinuedMethod("TaskInputs.sourceDir(Object)", "Please use TaskInputs.dir(Object).skipWhenEmpty() instead.");
        taskMutator.mutate("TaskInputs.sourceDir(Object)", new Runnable() {
            @Override
            public void run() {
                addSpec(resolver.resolveFilesAsTree(path), true);
            }
        });
        return this;
    }

    private TaskInputFilePropertySpecInternal addSpec(Object paths) {
        return addSpec(paths, false);
    }

    private TaskInputFilePropertySpecInternal addSpec(Object paths, boolean skipWhenEmpty) {
        PropertySpec spec = new PropertySpec(taskName, skipWhenEmpty, resolver, paths);
        fileProperties.add(spec);
        return spec;
    }

    private String nextLegacyPropertyName() {
        return "$" + (++legacyFilePropertyCounter);
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
                value = uncheckedCall(callable);
            } else if (value instanceof FileCollection) {
                FileCollection fileCollection = (FileCollection) value;
                return fileCollection.getFiles();
            } else {
                return avoidGString(value);
            }
        }
    }

    private static Object avoidGString(Object value) {
        return (value instanceof GString) ? value.toString() : value;
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

    @Override
    public TaskInputs configure(final Action<? super TaskInputs> action) {
        taskMutator.mutate("TaskInputs.configure(Action)", new Runnable() {
            public void run() {
                if (configureActions == null) {
                    configureActions = Lists.newLinkedList();
                }
                configureActions.add(action);
            }
        });
        return this;
    }

    @Override
    public TaskInputs configure(Closure action) {
        return configure(ConfigureUtil.configureUsing(action));
    }

    @Override
    public void ensureConfigured() {
        if (configureActions != null) {
            while (!configureActions.isEmpty()) {
                configureActions.remove().execute(this);
            }
            configureActions = null;
        }
    }

    private class PropertySpec extends AbstractTaskFilePropertySpec implements TaskInputFilePropertySpecInternal {

        private boolean skipWhenEmpty;
        private boolean optional;

        public PropertySpec(String taskName, boolean skipWhenEmpty, FileResolver resolver, Object paths) {
            super(taskName, "input", resolver, paths);
            this.skipWhenEmpty = skipWhenEmpty;
        }

        @Override
        public TaskInputFilePropertySpec withPropertyName(String propertyName) {
            setPropertyName(propertyName);
            return this;
        }

        @Override
        public boolean isSkipWhenEmpty() {
            return skipWhenEmpty;
        }

        @Override
        public TaskInputFilePropertySpecInternal skipWhenEmpty(boolean skipWhenEmpty) {
            this.skipWhenEmpty = skipWhenEmpty;
            return this;
        }

        @Override
        public TaskInputFilePropertySpec skipWhenEmpty() {
            return skipWhenEmpty(true);
        }

        @Override
        public boolean isOptional() {
            return optional;
        }

        @Override
        public TaskInputFilePropertySpecInternal optional(boolean optional) {
            this.optional = optional;
            return this;
        }

        @Override
        public TaskInputFilePropertySpec optional() {
            return optional(true);
        }

        // --- Deprecated delegate methods

        private TaskInputs getTaskInputs(String method) {
            DeprecationLogger.nagUserOfDiscontinuedMethod("chaining of the " + method, String.format("Please use the %s method on TaskInputs directly instead.", method));
            return DefaultTaskInputs.this;
        }

        @Override
        public boolean getHasInputs() {
            return getTaskInputs("getHasInputs()").getHasInputs();
        }

        @Override
        public FileCollection getFiles() {
            return getTaskInputs("getFiles()").getFiles();
        }

        @Override
        public TaskInputFilePropertySpec files(Object... paths) {
            return getTaskInputs("files(Object...)").files(paths);
        }

        @Override
        public TaskInputFilePropertySpec file(Object path) {
            return getTaskInputs("file(Object)").file(path);
        }

        @Override
        public TaskInputFilePropertySpec dir(Object dirPath) {
            return getTaskInputs("dir(Object)").dir(dirPath);
        }

        @Override
        public Map<String, Object> getProperties() {
            return getTaskInputs("getProperties()").getProperties();
        }

        @Override
        public TaskInputs property(String name, Object value) {
            return getTaskInputs("property(String, Object)").property(name, value);
        }

        @Override
        public TaskInputs properties(Map<String, ?> properties) {
            return getTaskInputs("properties(Map)").properties(properties);
        }

        @Override
        public boolean getHasSourceFiles() {
            return getTaskInputs("getHasSourceFiles()").getHasSourceFiles();
        }

        @Override
        public FileCollection getSourceFiles() {
            return getTaskInputs("getSourceFiles()").getSourceFiles();
        }

        @Override
        @Deprecated
        public TaskInputs source(Object... paths) {
            return getTaskInputs("source(Object...)").source(paths);
        }

        @Override
        @Deprecated
        public TaskInputs source(Object path) {
            return getTaskInputs("source(Object)").source(path);
        }

        @Override
        @Deprecated
        public TaskInputs sourceDir(Object path) {
            return getTaskInputs("sourceDir(Object)").sourceDir(path);
        }

        @Override
        public TaskInputs configure(Action<? super TaskInputs> action) {
            return getTaskInputs("configure(Action)").configure(action);
        }

        @Override
        public TaskInputs configure(Closure action) {
            return getTaskInputs("configure(Closure)").configure(action);
        }
    }

    private class TaskInputUnionFileCollection extends CompositeFileCollection {
        private final boolean skipWhenEmptyOnly;
        private final String displayName;

        public TaskInputUnionFileCollection(String displayName, boolean skipWhenEmptyOnly) {
            this.displayName = displayName;
            this.skipWhenEmptyOnly = skipWhenEmptyOnly;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public void visitContents(FileCollectionResolveContext context) {
            for (PropertySpec fileProperty : fileProperties) {
                if (!skipWhenEmptyOnly || fileProperty.isSkipWhenEmpty()) {
                    context.add(fileProperty.getPropertyFiles());
                }
            }
        }
    }
}
