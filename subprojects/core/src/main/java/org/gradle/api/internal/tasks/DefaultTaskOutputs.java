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
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskExecutionHistory;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.specs.AndSpec;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskFileOutputPropertySpec;
import org.gradle.api.tasks.TaskOutputs;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.DeprecationLogger;

import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;

public class DefaultTaskOutputs implements TaskOutputsInternal {
    private final FileCollection allOutputFiles;
    private AndSpec<TaskInternal> upToDateSpec = new AndSpec<TaskInternal>();
    private TaskExecutionHistory history;
    private final FileResolver resolver;
    private final String taskName;
    private final TaskMutator taskMutator;
    private final List<PropertySpec> fileProperties = Lists.newArrayList();
    private int legacyFilePropertyCounter;
    private Queue<Action<? super TaskOutputs>> configureActions;

    public DefaultTaskOutputs(FileResolver resolver, final TaskInternal task, TaskMutator taskMutator) {
        this.resolver = resolver;
        this.taskName = task.getName();
        this.taskMutator = taskMutator;

        final DefaultTaskDependency buildDependencies = new DefaultTaskDependency();
        buildDependencies.add(task);
        this.allOutputFiles = new CompositeFileCollection() {

            @Override
            public String getDisplayName() {
                return task + " output files";
            }

            @Override
            public void visitContents(FileCollectionResolveContext context) {
                for (PropertySpec fileProperty : fileProperties) {
                    context.add(fileProperty.getFiles());
                }
            }

            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                context.add(buildDependencies);
                super.visitDependencies(context);
            }
        };
    }

    @Override
    public Spec<? super TaskInternal> getUpToDateSpec() {
        return upToDateSpec;
    }

    @Override
    public void upToDateWhen(final Closure upToDateClosure) {
        taskMutator.mutate("TaskOutputs.upToDateWhen(Closure)", new Runnable() {
            public void run() {
                upToDateSpec = upToDateSpec.and(upToDateClosure);
            }
        });
    }

    @Override
    public void upToDateWhen(final Spec<? super Task> spec) {
        taskMutator.mutate("TaskOutputs.upToDateWhen(Spec)", new Runnable() {
            public void run() {
                upToDateSpec = upToDateSpec.and(spec);
            }
        });
    }

    @Override
    public boolean getHasOutput() {
        return !fileProperties.isEmpty() || !upToDateSpec.getSpecs().isEmpty();
    }

    @Override
    public FileCollection getFiles() {
        return allOutputFiles;
    }

    @Override
    public Collection<TaskFileOutputPropertySpecInternal> getFileProperties() {
        ImmutableList.Builder<TaskFileOutputPropertySpecInternal> builder = ImmutableList.builder();
        for (PropertySpec propertySpec : fileProperties) {
            if (propertySpec.getPropertyName() == null) {
                propertySpec.withPropertyName(nextLegacyPropertyName());
            }
            builder.add(propertySpec);
        }
        return builder.build();
    }


    @Override
    public TaskFileOutputPropertySpec includeFile(final Object path) {
        return taskMutator.mutate("TaskOutputs.includeFile(Object)", new Callable<TaskFileOutputPropertySpec>() {
            @Override
            public TaskFileOutputPropertySpec call() throws Exception {
                return addSpec(path);
            }
        });
    }

    @Override
    public TaskFileOutputPropertySpec includeDir(final Object path) {
        return taskMutator.mutate("TaskOutputs.includeDir(Object)", new Callable<TaskFileOutputPropertySpec>() {
            @Override
            public TaskFileOutputPropertySpec call() throws Exception {
                return addSpec(path);
            }
        });
    }

    @Override
    public TaskOutputs files(final Object... paths) {
        DeprecationLogger.nagUserOfDiscontinuedMethod("TaskOutputs.files(Object...)", "Please use the TaskOutputs.includeFile(Object) or the TaskOutputs.includeDir(Object) method instead.");
        taskMutator.mutate("TaskOutputs.files(Object...)", new Runnable() {
            public void run() {
                addSpec(paths);
            }
        });
        return this;
    }

    @Override
    public TaskOutputs file(final Object path) {
        DeprecationLogger.nagUserOfReplacedMethod("TaskOutputs.file(Object)", "TaskOutputs.includeFile(Object)");
        taskMutator.mutate("TaskOutputs.file(Object)", new Runnable() {
            public void run() {
                addSpec(path);
            }
        });
        return this;
    }

    @Override
    public TaskOutputs dir(final Object path) {
        DeprecationLogger.nagUserOfReplacedMethod("TaskOutputs.dir(Object)", "TaskOutputs.includeDir(Object)");
        taskMutator.mutate("TaskOutputs.dir(Object)", new Runnable() {
            public void run() {
                addSpec(path);
            }
        });
        return this;
    }

    private TaskFileOutputPropertySpecInternal addSpec(Object paths) {
        PropertySpec spec = new PropertySpec(taskName, resolver, paths);
        fileProperties.add(spec);
        return spec;
    }

    private String nextLegacyPropertyName() {
        return "$" + (++legacyFilePropertyCounter);
    }

    @Override
    public FileCollection getPreviousFiles() {
        if (history == null) {
            throw new IllegalStateException("Task history is currently not available for this task.");
        }
        return history.getOutputFiles();
    }

    @Override
    public void setHistory(TaskExecutionHistory history) {
        this.history = history;
    }

    @Override
    public TaskOutputs configure(final Action<? super TaskOutputs> action) {
        taskMutator.mutate("TaskOutputs.configure(Action)", new Runnable() {
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
    public TaskOutputs configure(Closure action) {
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

    private static class PropertySpec implements TaskFileOutputPropertySpecInternal {
        private final TaskPropertyFileCollection files;
        private String propertyName;
        private boolean optional;

        public PropertySpec(String taskName, FileResolver resolver, Object paths) {
            this.files = new TaskPropertyFileCollection(taskName, "output", this, resolver, paths);
        }

        @Override
        public TaskPropertyFileCollection getFiles() {
            return files;
        }

        @Override
        public String getPropertyName() {
            return propertyName;
        }

        @Override
        public TaskFileOutputPropertySpec withPropertyName(String propertyName) {
            this.propertyName = propertyName;
            return this;
        }

        @Override
        public boolean isOptional() {
            return optional;
        }

        @Override
        public TaskFileOutputPropertySpec optional() {
            return optional(true);
        }

        @Override
        public TaskFileOutputPropertySpec optional(boolean optional) {
            this.optional = optional;
            return this;
        }

        @Override
        public String toString() {
            return propertyName == null ? "<unnamed>" : propertyName;
        }
    }
}
