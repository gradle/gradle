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
import org.gradle.api.tasks.TaskOutputFilePropertyBuilder;
import org.gradle.api.tasks.TaskOutputs;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.DeprecationLogger;

import java.util.Queue;
import java.util.concurrent.Callable;

public class DefaultTaskOutputs extends FilePropertyContainer<DefaultTaskOutputs.PropertySpec> implements TaskOutputsInternal {
    private static final AndSpec<TaskInternal> EMPTY_AND_SPEC = new AndSpec<TaskInternal>();

    private final FileCollection allOutputFiles;
    private AndSpec<TaskInternal> upToDateSpec = EMPTY_AND_SPEC;
    private TaskExecutionHistory history;
    private final FileResolver resolver;
    private final String taskName;
    private final TaskMutator taskMutator;
    private Queue<Action<? super TaskOutputs>> configureActions;

    public DefaultTaskOutputs(FileResolver resolver, final TaskInternal task, TaskMutator taskMutator) {
        super("output");
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
                for (TaskFilePropertySpec fileProperty : fileProperties) {
                    context.add(fileProperty.getPropertyFiles());
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
        return !fileProperties.isEmpty() || !upToDateSpec.isEmpty();
    }

    @Override
    public FileCollection getFiles() {
        return allOutputFiles;
    }

    @Override
    public TaskOutputFilePropertyBuilder file(final Object path) {
        return taskMutator.mutate("TaskOutputs.file(Object)", new Callable<TaskOutputFilePropertyBuilder>() {
            @Override
            public TaskOutputFilePropertyBuilder call() throws Exception {
                return addSpec(path);
            }
        });
    }

    @Override
    public TaskOutputFilePropertyBuilder dir(final Object path) {
        return taskMutator.mutate("TaskOutputs.dir(Object)", new Callable<TaskOutputFilePropertyBuilder>() {
            @Override
            public TaskOutputFilePropertyBuilder call() throws Exception {
                return addSpec(path);
            }
        });
    }

    @Override
    public TaskOutputFilePropertyBuilder files(final Object... paths) {
        DeprecationLogger.nagUserOfDiscontinuedMethod("TaskOutputs.files(Object...)", "Please use the TaskOutputs.file(Object) or the TaskOutputs.dir(Object) method instead.");
        return taskMutator.mutate("TaskOutputs.files(Object...)", new Callable<TaskOutputFilePropertyBuilder>() {
            @Override
            public TaskOutputFilePropertyBuilder call() throws Exception {
                return addSpec(paths);
            }
        });
    }

    private TaskOutputFilePropertyBuilder addSpec(Object paths) {
        PropertySpec spec = new PropertySpec(taskName, resolver, paths);
        fileProperties.add(spec);
        return spec;
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

    class PropertySpec extends BaseTaskFilePropertySpec implements TaskOutputFilePropertyBuilder {
        private boolean optional;

        public PropertySpec(String taskName, FileResolver resolver, Object paths) {
            super(taskName, "output", resolver, paths);
        }

        @Override
        public TaskOutputFilePropertyBuilder withPropertyName(String propertyName) {
            setPropertyName(propertyName);
            return this;
        }

        public boolean isOptional() {
            return optional;
        }

        @Override
        public TaskOutputFilePropertyBuilder optional() {
            return optional(true);
        }

        @Override
        public TaskOutputFilePropertyBuilder optional(boolean optional) {
            this.optional = optional;
            return this;
        }

        // --- Deprecated delegate methods

        private TaskOutputs getTaskOutputs(String method) {
            DeprecationLogger.nagUserOfDiscontinuedMethod("chaining of the " + method, String.format("Use '%s' on TaskOutputs directly instead.", method));
            return DefaultTaskOutputs.this;
        }

        @Override
        public void upToDateWhen(Closure upToDateClosure) {
            getTaskOutputs("upToDateWhen(Closure)").upToDateWhen(upToDateClosure);
        }

        @Override
        public void upToDateWhen(Spec<? super Task> upToDateSpec) {
            getTaskOutputs("upToDateWhen(Spec)").upToDateWhen(upToDateSpec);
        }

        @Override
        public boolean getHasOutput() {
            return getTaskOutputs("getHasOutput()").getHasOutput();
        }

        @Override
        public FileCollection getFiles() {
            return getTaskOutputs("getFiles()").getFiles();
        }

        @Override
        @Deprecated
        public TaskOutputs files(Object... paths) {
            return getTaskOutputs("files(Object...)").files(paths);
        }

        @Override
        public TaskOutputFilePropertyBuilder file(Object path) {
            return getTaskOutputs("file(Object)").file(path);
        }

        @Override
        public TaskOutputFilePropertyBuilder dir(Object path) {
            return getTaskOutputs("dir(Object)").dir(path);
        }

        @Override
        public TaskOutputs configure(Action<? super TaskOutputs> action) {
            return getTaskOutputs("configure(Action)").configure(action);
        }

        @Override
        public TaskOutputs configure(Closure action) {
            return getTaskOutputs("configure(Closure)").configure(action);
        }
    }
}
