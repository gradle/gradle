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
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import groovy.lang.Closure;
import org.gradle.api.Describable;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskExecutionHistory;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.TaskOutputCachingState;
import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec.OutputType;
import org.gradle.api.internal.tasks.execution.SelfDescribingSpec;
import org.gradle.api.specs.AndSpec;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskOutputFilePropertyBuilder;
import org.gradle.util.DeprecationLogger;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.concurrent.Callable;

public class DefaultTaskOutputs implements TaskOutputsInternal {
    private static final TaskOutputCachingState CACHING_NOT_ENABLED = DefaultTaskOutputCachingState.disabled("Caching has not been enabled for the task");
    private static final TaskOutputCachingState NO_OUTPUTS_DECLARED = DefaultTaskOutputCachingState.disabled("No outputs declared");

    private final FileCollection allOutputFiles;
    private AndSpec<TaskInternal> upToDateSpec = AndSpec.empty();
    private List<SelfDescribingSpec<TaskInternal>> cacheIfSpecs = new LinkedList<SelfDescribingSpec<TaskInternal>>();
    private List<SelfDescribingSpec<TaskInternal>> doNotCacheIfSpecs = new LinkedList<SelfDescribingSpec<TaskInternal>>();
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
        this.allOutputFiles = new TaskOutputUnionFileCollection(buildDependencies);
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
    public TaskOutputCachingState getCachingState() {
        if (cacheIfSpecs.isEmpty()) {
            return CACHING_NOT_ENABLED;
        }
        if (!hasDeclaredOutputs()) {
            return NO_OUTPUTS_DECLARED;
        }

        for (TaskPropertySpec spec : getFileProperties()) {
            if (spec instanceof NonCacheableTaskOutputPropertySpec) {
                return DefaultTaskOutputCachingState.disabled(
                    "Declares multiple output files for the single output property '"
                        + spec.getPropertyName()
                        + "' via `@OutputFiles`, `@OutputDirectories` or `TaskOutputs.files()`");
            }
        }
        for (SelfDescribingSpec<TaskInternal> selfDescribingSpec : cacheIfSpecs) {
            if (!selfDescribingSpec.isSatisfiedBy(task)) {
                return DefaultTaskOutputCachingState.disabled("'" + selfDescribingSpec.getDisplayName() + "' not satisfied");
            }
        }
        for (SelfDescribingSpec<TaskInternal> selfDescribingSpec : doNotCacheIfSpecs) {
            if (selfDescribingSpec.isSatisfiedBy(task)) {
                return DefaultTaskOutputCachingState.disabled("'" + selfDescribingSpec.getDisplayName() + "' satisfied");
            }
        }
        return DefaultTaskOutputCachingState.ENABLED;
    }

    @Override
    public void cacheIf(final Spec<? super Task> spec) {
        cacheIf("Task outputs cacheable", spec);
    }

    @Override
    public void cacheIf(final String cachingEnabledReason, final Spec<? super Task> spec) {
        taskMutator.mutate("TaskOutputs.cacheIf(Spec)", new Runnable() {
            public void run() {
                cacheIfSpecs.add(new SelfDescribingSpec<TaskInternal>(spec, cachingEnabledReason));
            }
        });
    }

    @Override
    public void doNotCacheIf(final Spec<? super Task> spec) {
        DeprecationLogger.nagUserOfReplacedMethod("doNotCacheIf(Spec)", "doNotCacheIf(String, Spec)");
        doNotCacheIf("Task outputs not cacheable", spec);
    }

    @Override
    public void doNotCacheIf(final String cachingDisabledReason, final Spec<? super Task> spec) {
        taskMutator.mutate("TaskOutputs.doNotCacheIf(Spec)", new Runnable() {
            public void run() {
                doNotCacheIfSpecs.add(new SelfDescribingSpec<TaskInternal>(spec, cachingDisabledReason));
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
            Iterator<TaskOutputFilePropertySpec> flattenedProperties = Iterators.concat(Iterables.transform(filePropertiesInternal, new Function<TaskPropertySpec, Iterator<? extends TaskOutputFilePropertySpec>>() {
                @Override
                public Iterator<? extends TaskOutputFilePropertySpec> apply(TaskPropertySpec propertySpec) {
                    if (propertySpec instanceof CompositeTaskOutputPropertySpec) {
                        return ((CompositeTaskOutputPropertySpec) propertySpec).resolveToOutputProperties();
                    } else {
                        return Iterators.singletonIterator((TaskOutputFilePropertySpec) propertySpec);
                    }
                }
            }).iterator());
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
    public TaskOutputFilePropertyBuilder files(final Object... paths) {
        return taskMutator.mutate("TaskOutputs.files(Object...)", new Callable<TaskOutputFilePropertyBuilder>() {
            @Override
            public TaskOutputFilePropertyBuilder call() throws Exception {
                return addSpec(new CompositeTaskOutputPropertySpec(DefaultTaskOutputs.this, task.getName(), resolver, OutputType.FILE, paths));
            }
        });
    }

    @Override
    public TaskOutputFilePropertyBuilder dirs(final Object... paths) {
        return taskMutator.mutate("TaskOutputs.dirs(Object...)", new Callable<TaskOutputFilePropertyBuilder>() {
            @Override
            public TaskOutputFilePropertyBuilder call() throws Exception {
                return addSpec(new CompositeTaskOutputPropertySpec(DefaultTaskOutputs.this, task.getName(), resolver, OutputType.DIRECTORY, paths));
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

    private class TaskOutputUnionFileCollection extends CompositeFileCollection implements Describable {
        private final DefaultTaskDependency buildDependencies;

        public TaskOutputUnionFileCollection(DefaultTaskDependency buildDependencies) {
            this.buildDependencies = buildDependencies;
        }

        @Override
        public String getDisplayName() {
            return "task '" + task.getName() + "' output files";
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
