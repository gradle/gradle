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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Callables;
import groovy.lang.Closure;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskExecutionHistory;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.internal.tasks.properties.DefaultCacheableTaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.properties.CacheableTaskOutputFilePropertySpec.OutputType;
import org.gradle.api.internal.tasks.properties.CompositeTaskOutputPropertySpec;
import org.gradle.api.internal.tasks.properties.NonCacheableTaskOutputPropertySpec;
import org.gradle.api.internal.tasks.properties.TaskFilePropertySpec;
import org.gradle.api.internal.tasks.properties.TaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.properties.TaskOutputPropertySpecAndBuilder;
import org.gradle.api.internal.tasks.properties.TaskPropertySpec;
import org.gradle.api.internal.tasks.properties.TaskPropertyUtils;
import org.gradle.api.specs.AndSpec;
import org.gradle.api.specs.OrSpec;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskOutputFilePropertyBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.Callable;

public class DefaultTaskOutputs implements TaskOutputsInternal {
    private final FileCollection allOutputFiles;
    private AndSpec<TaskInternal> upToDateSpec = AndSpec.empty();
    private AndSpec<TaskInternal> cacheIfSpec = AndSpec.empty();
    private OrSpec<TaskInternal> doNotCacheIfSpec = OrSpec.empty();
    private TaskExecutionHistory history;
    private final List<TaskOutputPropertySpecAndBuilder> filePropertiesInternal = Lists.newArrayList();
    private SortedSet<TaskOutputFilePropertySpec> fileProperties;
    private final FileResolver resolver;
    private final TaskInternal task;
    private final TaskMutator taskMutator;

    public DefaultTaskOutputs(FileResolver resolver, final TaskInternal task, TaskMutator taskMutator) {
        this.resolver = resolver;
        this.task = task;
        this.taskMutator = taskMutator;

        final DefaultTaskDependency buildDependencies = new DefaultTaskDependency();
        buildDependencies.add(task);
        this.allOutputFiles = new TaskOutputUnionFileCollection("task '" + task.getName() + "' output files", buildDependencies);
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
    public boolean isCacheEnabled() {
        return !cacheIfSpec.getSpecs().isEmpty() && cacheIfSpec.isSatisfiedBy(task)
            && (doNotCacheIfSpec.isEmpty() || !doNotCacheIfSpec.isSatisfiedBy(task));
    }

    @Override
    public boolean isCacheAllowed() {
        for (TaskPropertySpec spec : filePropertiesInternal) {
            if (spec instanceof NonCacheableTaskOutputPropertySpec) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void cacheIf(final Spec<? super Task> spec) {
        taskMutator.mutate("TaskOutputs.cacheIf(Spec)", new Runnable() {
            public void run() {
                cacheIfSpec = cacheIfSpec.and(spec);
            }
        });
    }

    @Override
    public void doNotCacheIf(final Spec<? super Task> spec) {
        taskMutator.mutate("TaskOutputs.doNotCacheIf(Spec)", new Runnable() {
            public void run() {
                doNotCacheIfSpec = doNotCacheIfSpec.or(spec);
            }
        });
    }

    @Override
    public boolean getHasOutput() {
        return hasDeclaredOutputs() || !upToDateSpec.isEmpty();
    }

    @Override
    public boolean hasDeclaredOutputs() {
        return !filePropertiesInternal.isEmpty();
    }

    @Override
    public FileCollection getFiles() {
        return allOutputFiles;
    }

    @Override
    public SortedSet<TaskOutputFilePropertySpec> getFileProperties() {
        if (fileProperties == null) {
            TaskPropertyUtils.ensurePropertiesHaveNames(filePropertiesInternal);
            Iterable<TaskOutputFilePropertySpec> flattenedProperties = Iterables.concat(
                Iterables.transform(filePropertiesInternal, new Function<TaskPropertySpec, Iterable<? extends TaskOutputFilePropertySpec>>() {
                    @Override
                    public Iterable<? extends TaskOutputFilePropertySpec> apply(TaskPropertySpec propertySpec) {
                        if (propertySpec instanceof CompositeTaskOutputPropertySpec) {
                            return (CompositeTaskOutputPropertySpec) propertySpec;
                        } else {
                            return Collections.singleton((TaskOutputFilePropertySpec) propertySpec);
                        }
                    }
                })
            );
            fileProperties = TaskPropertyUtils.collectFileProperties("output", flattenedProperties);
        }
        return fileProperties;
    }

    @Override
    public TaskOutputFilePropertyBuilder file(final Object path) {
        return taskMutator.mutate("TaskOutputs.file(Object)", new Callable<TaskOutputFilePropertyBuilder>() {
            @Override
            public TaskOutputFilePropertyBuilder call() throws Exception {
                return addSpec(new DefaultCacheableTaskOutputFilePropertySpec(DefaultTaskOutputs.this, task.getName(), resolver, OutputType.FILE, path));
            }
        });
    }

    @Override
    public TaskOutputFilePropertyBuilder dir(final Object path) {
        return taskMutator.mutate("TaskOutputs.dir(Object)", new Callable<TaskOutputFilePropertyBuilder>() {
            @Override
            public TaskOutputFilePropertyBuilder call() throws Exception {
                return addSpec(new DefaultCacheableTaskOutputFilePropertySpec(DefaultTaskOutputs.this, task.getName(), resolver, OutputType.DIRECTORY, path));
            }
        });
    }

    @Override
    public TaskOutputFilePropertyBuilder namedFiles(final Callable<Map<?, ?>> paths) {
        return taskMutator.mutate("TaskOutputs.namedFiles(Callable)", new Callable<TaskOutputFilePropertyBuilder>() {
            @Override
            public TaskOutputFilePropertyBuilder call() throws Exception {
                return addSpec(new CompositeTaskOutputPropertySpec(DefaultTaskOutputs.this, resolver, OutputType.FILE, paths));
            }
        });
    }

    @Override
    public TaskOutputFilePropertyBuilder namedFiles(final Map<?, ?> paths) {
        return taskMutator.mutate("TaskOutputs.namedFiles(Map)", new Callable<TaskOutputFilePropertyBuilder>() {
            @Override
            public TaskOutputFilePropertyBuilder call() throws Exception {
                Callable<Map<?, ?>> callable = Callables.<Map<?, ?>>returning(ImmutableMap.copyOf(paths));
                return addSpec(new CompositeTaskOutputPropertySpec(DefaultTaskOutputs.this, resolver, OutputType.FILE, callable));
            }
        });
    }

    @Override
    public TaskOutputFilePropertyBuilder namedDirectories(final Callable<Map<?, ?>> paths) {
        return taskMutator.mutate("TaskOutputs.namedDirectories(Callable)", new Callable<TaskOutputFilePropertyBuilder>() {
            @Override
            public TaskOutputFilePropertyBuilder call() throws Exception {
                return addSpec(new CompositeTaskOutputPropertySpec(DefaultTaskOutputs.this, resolver, OutputType.DIRECTORY, paths));
            }
        });
    }

    @Override
    public TaskOutputFilePropertyBuilder namedDirectories(final Map<?, ?> paths) {
        return taskMutator.mutate("TaskOutputs.namedDirectories(Map)", new Callable<TaskOutputFilePropertyBuilder>() {
            @Override
            public TaskOutputFilePropertyBuilder call() throws Exception {
                Callable<Map<?, ?>> callable = Callables.<Map<?, ?>>returning(ImmutableMap.copyOf(paths));
                return addSpec(new CompositeTaskOutputPropertySpec(DefaultTaskOutputs.this, resolver, OutputType.DIRECTORY, callable));
            }
        });
    }

    @Override
    public TaskOutputFilePropertyBuilder files(final Object... paths) {
        return taskMutator.mutate("TaskOutputs.files(Object...)", new Callable<TaskOutputFilePropertyBuilder>() {
            @Override
            public TaskOutputFilePropertyBuilder call() throws Exception {
                return addSpec(new NonCacheableTaskOutputPropertySpec(DefaultTaskOutputs.this, task.getName(), resolver, paths));
            }
        });
    }

    private TaskOutputFilePropertyBuilder addSpec(TaskOutputPropertySpecAndBuilder spec) {
        filePropertiesInternal.add(spec);
        return spec;
    }

    @Override
    public FileCollection getPreviousOutputFiles() {
        if (history == null) {
            throw new IllegalStateException("Task history is currently not available for this task.");
        }
        return history.getOutputFiles();
    }

    @Override
    public void setHistory(TaskExecutionHistory history) {
        this.history = history;
    }

    private class TaskOutputUnionFileCollection extends CompositeFileCollection {
        private final String displayName;
        private final DefaultTaskDependency buildDependencies;

        public TaskOutputUnionFileCollection(String displayName, DefaultTaskDependency buildDependencies) {
            this.displayName = displayName;
            this.buildDependencies = buildDependencies;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public void visitContents(FileCollectionResolveContext context) {
            for (TaskFilePropertySpec propertySpec : getFileProperties()) {
                context.add(propertySpec.getPropertyFiles());
            }
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            context.add(buildDependencies);
            super.visitDependencies(context);
        }
    }
}
