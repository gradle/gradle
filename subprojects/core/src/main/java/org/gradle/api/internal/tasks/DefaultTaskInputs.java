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

import com.google.common.collect.Lists;
import groovy.lang.GString;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInputsInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.state.SnapshotNormalizationStrategy;
import org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareStrategy;
import org.gradle.api.internal.changedetection.state.TaskFilePropertySnapshotNormalizationStrategy;
import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskInputFilePropertyBuilder;
import org.gradle.api.tasks.TaskInputs;
import org.gradle.util.DeprecationLogger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.Callable;

import static org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareStrategy.ORDERED;
import static org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareStrategy.UNORDERED;
import static org.gradle.api.internal.changedetection.state.TaskFilePropertySnapshotNormalizationStrategy.ABSOLUTE;
import static org.gradle.api.internal.tasks.TaskPropertyUtils.ensurePropertiesHaveNames;
import static org.gradle.util.GUtil.uncheckedCall;

public class DefaultTaskInputs implements TaskInputsInternal {
    private final FileCollection allInputFiles;
    private final FileCollection allSourceFiles;
    private final FileResolver resolver;
    private final TaskInternal task;
    private final TaskMutator taskMutator;
    private final Map<String, Object> properties = new HashMap<String, Object>();
    private final List<PropertySpec> filePropertiesInternal = Lists.newArrayList();
    private SortedSet<TaskInputFilePropertySpec> fileProperties;

    public DefaultTaskInputs(FileResolver resolver, TaskInternal task, TaskMutator taskMutator) {
        this.resolver = resolver;
        this.task = task;
        this.taskMutator = taskMutator;
        String taskName = task.getName();
        this.allInputFiles = new TaskInputUnionFileCollection("task '" + taskName + "' input files", false);
        this.allSourceFiles = new TaskInputUnionFileCollection("task '" + taskName + "' source files", true);
    }

    @Override
    public boolean getHasInputs() {
        return !filePropertiesInternal.isEmpty() || !properties.isEmpty();
    }

    @Override
    public FileCollection getFiles() {
        return allInputFiles;
    }

    @Override
    public SortedSet<TaskInputFilePropertySpec> getFileProperties() {
        if (fileProperties == null) {
            ensurePropertiesHaveNames(filePropertiesInternal);
            fileProperties = TaskPropertyUtils.<TaskInputFilePropertySpec>collectFileProperties("input", filePropertiesInternal);
        }
        return fileProperties;
    }

    @Override
    public TaskInputFilePropertyBuilderInternal files(final Object... paths) {
        return taskMutator.mutate("TaskInputs.files(Object...)", new Callable<TaskInputFilePropertyBuilderInternal>() {
            @Override
            public TaskInputFilePropertyBuilderInternal call() {
                return addSpec(paths);
            }
        });
    }

    @Override
    public TaskInputFilePropertyBuilderInternal file(final Object path) {
        return taskMutator.mutate("TaskInputs.file(Object)", new Callable<TaskInputFilePropertyBuilderInternal>() {
            @Override
            public TaskInputFilePropertyBuilderInternal call() {
                return addSpec(path);
            }
        });
    }

    @Override
    public TaskInputFilePropertyBuilderInternal dir(final Object dirPath) {
        return taskMutator.mutate("TaskInputs.dir(Object)", new Callable<TaskInputFilePropertyBuilderInternal>() {
            @Override
            public TaskInputFilePropertyBuilderInternal call() {
                return addSpec(resolver.resolveFilesAsTree(dirPath));
            }
        });
    }

    @Override
    public boolean getHasSourceFiles() {
        for (PropertySpec propertySpec : filePropertiesInternal) {
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

    private TaskInputFilePropertyBuilderInternal addSpec(Object paths) {
        return addSpec(paths, false);
    }

    private TaskInputFilePropertyBuilderInternal addSpec(Object paths, boolean skipWhenEmpty) {
        PropertySpec spec = new PropertySpec(task.getName(), skipWhenEmpty, resolver, paths);
        filePropertiesInternal.add(spec);
        return spec;
    }

    public Map<String, Object> getProperties() {
        Map<String, Object> actualProperties = new HashMap<String, Object>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String propertyName = entry.getKey();
            try {
                Object value = prepareValue(entry.getValue());
                actualProperties.put(propertyName, value);
            } catch (Exception ex) {
                throw new InvalidUserDataException(String.format("Error while evaluating property '%s' of %s", propertyName, task), ex);
            }
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

    private class PropertySpec extends AbstractTaskPropertyBuilder implements TaskInputFilePropertySpec, TaskInputFilePropertyBuilderInternal {

        private final TaskPropertyFileCollection files;
        private boolean skipWhenEmpty;
        private boolean optional;
        private TaskFilePropertyCompareStrategy compareStrategy = UNORDERED;
        private SnapshotNormalizationStrategy snapshotNormalizationStrategy = ABSOLUTE;

        public PropertySpec(String taskName, boolean skipWhenEmpty, FileResolver resolver, Object paths) {
            this.files = new TaskPropertyFileCollection(taskName, "input", this, resolver, paths);
            this.skipWhenEmpty = skipWhenEmpty;
        }

        @Override
        public FileCollection getPropertyFiles() {
            return files;
        }

        @Override
        public TaskInputFilePropertyBuilderInternal withPropertyName(String propertyName) {
            setPropertyName(propertyName);
            return this;
        }

        public boolean isSkipWhenEmpty() {
            return skipWhenEmpty;
        }

        @Override
        public TaskInputFilePropertyBuilderInternal skipWhenEmpty(boolean skipWhenEmpty) {
            this.skipWhenEmpty = skipWhenEmpty;
            return this;
        }

        @Override
        public TaskInputFilePropertyBuilderInternal skipWhenEmpty() {
            return skipWhenEmpty(true);
        }

        public boolean isOptional() {
            return optional;
        }

        @Override
        public TaskInputFilePropertyBuilderInternal optional(boolean optional) {
            this.optional = optional;
            return this;
        }

        @Override
        public TaskInputFilePropertyBuilderInternal optional() {
            return optional(true);
        }

        @Override
        public TaskFilePropertyCompareStrategy getCompareStrategy() {
            return compareStrategy;
        }

        @Override
        public TaskInputFilePropertyBuilderInternal orderSensitive() {
            return orderSensitive(true);
        }

        @Override
        public TaskInputFilePropertyBuilderInternal orderSensitive(boolean orderSensitive) {
            this.compareStrategy = orderSensitive ? ORDERED : UNORDERED;
            return this;
        }

        @Override
        public SnapshotNormalizationStrategy getSnapshotNormalizationStrategy() {
            return snapshotNormalizationStrategy;
        }

        @Override
        public TaskInputFilePropertyBuilderInternal withPathSensitivity(PathSensitivity sensitivity) {
            return withSnapshotNormalizationStrategy(TaskFilePropertySnapshotNormalizationStrategy.valueOf(sensitivity));
        }

        @Override
        public TaskInputFilePropertyBuilderInternal withSnapshotNormalizationStrategy(SnapshotNormalizationStrategy snapshotNormalizationStrategy) {
            this.snapshotNormalizationStrategy = snapshotNormalizationStrategy;
            return this;
        }

        @Override
        public String toString() {
            return getPropertyName() + " (" + compareStrategy + ", " + snapshotNormalizationStrategy + ")";
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
        public TaskInputFilePropertyBuilder files(Object... paths) {
            return getTaskInputs("files(Object...)").files(paths);
        }

        @Override
        public TaskInputFilePropertyBuilder file(Object path) {
            return getTaskInputs("file(Object)").file(path);
        }

        @Override
        public TaskInputFilePropertyBuilder dir(Object dirPath) {
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
        public int compareTo(TaskPropertySpec o) {
            return getPropertyName().compareTo(o.getPropertyName());
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
            for (PropertySpec fileProperty : filePropertiesInternal) {
                if (!skipWhenEmptyOnly || fileProperty.isSkipWhenEmpty()) {
                    context.add(fileProperty.getPropertyFiles());
                }
            }
        }
    }
}
