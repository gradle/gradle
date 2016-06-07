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
import org.gradle.api.tasks.TaskFileInputPropertySpec;
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
    private final List<DefaultTaskFilePropertyInputSpec> fileProperties = Lists.newArrayList();
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
    public Collection<TaskFileInputPropertySpecInternal> getFileProperties() {
        ImmutableList.Builder<TaskFileInputPropertySpecInternal> builder = ImmutableList.builder();
        for (DefaultTaskFilePropertyInputSpec propertySpec : fileProperties) {
            if (propertySpec.getPropertyName() == null) {
                propertySpec.withPropertyName(nextLegacyPropertyName());
            }
            builder.add(propertySpec);
        }
        return builder.build();
    }

    @Override
    public TaskFileInputPropertySpec includeFile(final Object path) {
        return taskMutator.mutate("TaskInputs.includeFile(Object)", new Callable<TaskFileInputPropertySpec>() {
            @Override
            public TaskFileInputPropertySpec call() throws Exception {
                return addSpec(taskName, path);
            }
        });
    }

    @Override
    public TaskFileInputPropertySpec includeDir(final Object path) {
        return taskMutator.mutate("TaskInputs.includeDir(Object)", new Callable<TaskFileInputPropertySpec>() {
            @Override
            public TaskFileInputPropertySpec call() {
                return addSpec(taskName, resolver.resolveFilesAsTree(path));
            }
        });
    }

    @Override
    public TaskFileInputPropertySpec includeFiles(final Object... paths) {
        return taskMutator.mutate("TaskInputs.includeFiles(Object...)", new Callable<TaskFileInputPropertySpec>() {
            @Override
            public TaskFileInputPropertySpec call() {
                return addSpec(taskName, paths);
            }
        });
    }

    @Override
    public TaskInputs files(final Object... paths) {
        DeprecationLogger.nagUserOfReplacedMethod("TaskInputs.files(Object...)", "TaskInputs.includeFiles(Object...)");
        taskMutator.mutate("TaskInputs.files(Object...)", new Runnable() {
            @Override
            public void run() {
                addSpec(taskName, paths);
            }
        });
        return this;
    }

    @Override
    public TaskInputs file(final Object path) {
        DeprecationLogger.nagUserOfReplacedMethod("TaskInputs.file(Object)", "TaskInputs.includeFile(Object)");
        taskMutator.mutate("TaskInputs.file(Object)", new Runnable() {
            @Override
            public void run() {
                addSpec(taskName, path);
            }
        });
        return this;
    }

    @Override
    public TaskInputs dir(final Object dirPath) {
        DeprecationLogger.nagUserOfReplacedMethod("TaskInputs.dir(Object)", "TaskInputs.includeDir(Object)");
        taskMutator.mutate("TaskInputs.dir(Object)", new Runnable() {
            @Override
            public void run() {
                addSpec(taskName, resolver.resolveFilesAsTree(dirPath));
            }
        });
        return this;
    }

    @Override
    public boolean getHasSourceFiles() {
        for (DefaultTaskFilePropertyInputSpec propertySpec : fileProperties) {
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
        DeprecationLogger.nagUserOfReplacedMethod("TaskInputs.source(Object...)", "TaskInputs.includeFiles(Object...)");
        taskMutator.mutate("TaskInputs.source(Object...)", new Runnable() {
            @Override
            public void run() {
                addSpec(taskName, true, paths);
            }
        });
        return this;
    }

    @Override
    public TaskInputs source(final Object path) {
        DeprecationLogger.nagUserOfReplacedMethod("TaskInputs.source(Object)", "TaskInputs.includeFile(Object)");
        taskMutator.mutate("TaskInputs.source(Object)", new Runnable() {
            @Override
            public void run() {
                addSpec(taskName, true, path);
            }
        });
        return this;
    }

    @Override
    public TaskInputs sourceDir(final Object path) {
        DeprecationLogger.nagUserOfReplacedMethod("TaskInputs.sourceDir(Object)", "TaskInputs.includeDir(Object)");
        taskMutator.mutate("TaskInputs.sourceDir(Object)", new Runnable() {
            @Override
            public void run() {
                addSpec(taskName, true, resolver.resolveFilesAsTree(path));
            }
        });
        return this;
    }

    private TaskFileInputPropertySpecInternal addSpec(String taskName, Object paths) {
        return addSpec(taskName, false, paths);
    }

    private TaskFileInputPropertySpecInternal addSpec(String taskName, boolean skipWhenEmpty, Object paths) {
        DefaultTaskFilePropertyInputSpec spec = new DefaultTaskFilePropertyInputSpec(taskName, skipWhenEmpty, paths);
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

    private static class DefaultTaskFilePropertyInputSpec implements TaskFileInputPropertySpecInternal {

        private final TaskPropertyFileCollection files;
        private String propertyName;
        private boolean skipWhenEmpty;
        private boolean optional;

        public DefaultTaskFilePropertyInputSpec(String taskName, boolean skipWhenEmpty, Object paths) {
            this.files = new TaskPropertyFileCollection(taskName, "input", this, paths);
            this.skipWhenEmpty = skipWhenEmpty;
        }

        @Override
        public String getPropertyName() {
            return propertyName;
        }

        @Override
        public TaskFileInputPropertySpec withPropertyName(String propertyName) {
            this.propertyName = propertyName;
            return this;
        }

        @Override
        public TaskPropertyFileCollection getFiles() {
            return files;
        }

        @Override
        public boolean isSkipWhenEmpty() {
            return skipWhenEmpty;
        }

        @Override
        public TaskFileInputPropertySpecInternal skipWhenEmpty(boolean skipWhenEmpty) {
            this.skipWhenEmpty = skipWhenEmpty;
            return this;
        }

        @Override
        public TaskFileInputPropertySpec skipWhenEmpty() {
            return skipWhenEmpty(true);
        }

        @Override
        public boolean isOptional() {
            return optional;
        }

        @Override
        public TaskFileInputPropertySpecInternal optional(boolean optional) {
            this.optional = optional;
            return this;
        }

        @Override
        public TaskFileInputPropertySpec optional() {
            return optional(true);
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
            for (DefaultTaskFilePropertyInputSpec fileProperty : fileProperties) {
                if (!skipWhenEmptyOnly || fileProperty.isSkipWhenEmpty()) {
                    context.add(fileProperty.getFiles());
                }
            }
        }
    }
}
