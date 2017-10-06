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
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import groovy.lang.Closure;
import org.gradle.api.Describable;
import org.gradle.api.NonNullApi;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.OverlappingOutputs;
import org.gradle.api.internal.TaskExecutionHistory;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.TaskOutputCachingState;
import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.internal.tasks.execution.SelfDescribingSpec;
import org.gradle.api.specs.AndSpec;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskOutputFilePropertyBuilder;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.gradle.api.internal.tasks.TaskOutputCachingDisabledReasonCategory.*;

@NonNullApi
public class DefaultTaskOutputs implements TaskOutputsInternal {
    private static final TaskOutputCachingState ENABLED = DefaultTaskOutputCachingState.enabled();
    public static final TaskOutputCachingState DISABLED = DefaultTaskOutputCachingState.disabled(BUILD_CACHE_DISABLED, "Task output caching is disabled");
    private static final TaskOutputCachingState CACHING_NOT_ENABLED = DefaultTaskOutputCachingState.disabled(TaskOutputCachingDisabledReasonCategory.NOT_ENABLED_FOR_TASK, "Caching has not been enabled for the task");
    private static final TaskOutputCachingState NO_OUTPUTS_DECLARED = DefaultTaskOutputCachingState.disabled(TaskOutputCachingDisabledReasonCategory.NO_OUTPUTS_DECLARED, "No outputs declared");

    private final FileCollection allOutputFiles;
    private AndSpec<TaskInternal> upToDateSpec = AndSpec.empty();
    private List<SelfDescribingSpec<TaskInternal>> cacheIfSpecs = new LinkedList<SelfDescribingSpec<TaskInternal>>();
    private List<SelfDescribingSpec<TaskInternal>> doNotCacheIfSpecs = new LinkedList<SelfDescribingSpec<TaskInternal>>();
    private TaskExecutionHistory history;
    private final List<DeclaredTaskOutputFileProperty> declaredFileProperties = Lists.newArrayList();
    private ImmutableSortedSet<TaskOutputFilePropertySpec> fileProperties;
    private final FileResolver resolver;
    private final TaskInternal task;
    private final TaskMutator taskMutator;

    public DefaultTaskOutputs(FileResolver resolver, final TaskInternal task, TaskMutator taskMutator) {
        this.resolver = resolver;
        this.task = task;
        this.taskMutator = taskMutator;
        this.allOutputFiles = new TaskOutputUnionFileCollection(task);
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

        OverlappingOutputs overlappingOutputs = getOverlappingOutputs();
        if (overlappingOutputs!=null) {
            String relativePath = task.getProject().relativePath(overlappingOutputs.getOverlappedFilePath());
            return DefaultTaskOutputCachingState.disabled(TaskOutputCachingDisabledReasonCategory.OVERLAPPING_OUTPUTS,
                String.format("Gradle does not know how file '%s' was created (output property '%s'). Task output caching requires exclusive access to output paths to guarantee correctness.",
                    relativePath, overlappingOutputs.getPropertyName()));
        }

        for (TaskPropertySpec spec : getFileProperties()) {
            if (spec instanceof NonCacheableTaskOutputPropertySpec) {
                return DefaultTaskOutputCachingState.disabled(
                    PLURAL_OUTPUTS,
                    "Declares multiple output files for the single output property '"
                        + ((NonCacheableTaskOutputPropertySpec) spec).getOriginalPropertyName()
                        + "' via `@OutputFiles`, `@OutputDirectories` or `TaskOutputs.files()`"
                );
            }
        }

        for (SelfDescribingSpec<TaskInternal> selfDescribingSpec : cacheIfSpecs) {
            if (!selfDescribingSpec.isSatisfiedBy(task)) {
                return DefaultTaskOutputCachingState.disabled(
                    CACHE_IF_SPEC_NOT_SATISFIED,
                    "'" + selfDescribingSpec.getDisplayName() + "' not satisfied"
                );
            }
        }

        for (SelfDescribingSpec<TaskInternal> selfDescribingSpec : doNotCacheIfSpecs) {
            if (selfDescribingSpec.isSatisfiedBy(task)) {
                return DefaultTaskOutputCachingState.disabled(
                    DO_NOT_CACHE_IF_SPEC_SATISFIED,
                    "'" + selfDescribingSpec.getDisplayName() + "' satisfied"
                );
            }
        }
        return ENABLED;
    }

    @Nullable
    private OverlappingOutputs getOverlappingOutputs() {
        return history != null ? history.getOverlappingOutputs() : null;
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
        return !declaredFileProperties.isEmpty();
    }

    @Override
    public FileCollection getFiles() {
        return allOutputFiles;
    }

    @Override
    public ImmutableSortedSet<TaskOutputFilePropertySpec> getFileProperties() {
        if (fileProperties == null) {
            TaskPropertyUtils.ensurePropertiesHaveNames(declaredFileProperties);
            Iterator<TaskOutputFilePropertySpec> flattenedProperties = Iterators.concat(Iterables.transform(declaredFileProperties, new Function<TaskPropertySpec, Iterator<? extends TaskOutputFilePropertySpec>>() {
                @Override
                public Iterator<? extends TaskOutputFilePropertySpec> apply(@Nullable TaskPropertySpec propertySpec) {
                    if (propertySpec instanceof CompositeTaskOutputPropertySpec) {
                        return ((CompositeTaskOutputPropertySpec) propertySpec).resolveToOutputProperties();
                    } else {
                        if (propertySpec instanceof CacheableTaskOutputFilePropertySpec) {
                            File outputFile = ((CacheableTaskOutputFilePropertySpec) propertySpec).getOutputFile();
                            if (outputFile == null) {
                                return Iterators.emptyIterator();
                            }
                        }
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
                return file(new StaticValue(path));
            }
        });
    }

    @Override
    public TaskOutputFilePropertyBuilder file(ValidatingValue path) {
        return addSpec(new DefaultCacheableTaskOutputFilePropertySpec(task.getName(), resolver, OutputType.FILE, path, OUTPUT_FILE_VALIDATOR));
    }

    @Override
    public TaskOutputFilePropertyBuilder dir(final Object path) {
        return taskMutator.mutate("TaskOutputs.dir(Object)", new Callable<TaskOutputFilePropertyBuilder>() {
            @Override
            public TaskOutputFilePropertyBuilder call() throws Exception {
                return dir(new StaticValue(path));
            }
        });
    }

    @Override
    public TaskOutputFilePropertyBuilder dir(ValidatingValue path) {
        return addSpec(new DefaultCacheableTaskOutputFilePropertySpec(task.getName(), resolver, OutputType.DIRECTORY, path, OUTPUT_DIRECTORY_VALIDATOR));
    }

    @Override
    public TaskOutputFilePropertyBuilder files(final @Nullable Object... paths) {
        return taskMutator.mutate("TaskOutputs.files(Object...)", new Callable<TaskOutputFilePropertyBuilder>() {
            @Override
            public TaskOutputFilePropertyBuilder call() throws Exception {
                return files(new StaticValue(resolveSingleArray(paths)));
            }
        });
    }

    @Override
    public TaskOutputFilePropertyBuilder files(ValidatingValue paths) {
        return addSpec(new CompositeTaskOutputPropertySpec(task.getName(), resolver, OutputType.FILE, paths, OUTPUT_FILES_VALIDATOR));
    }

    @Override
    public TaskOutputFilePropertyBuilder dirs(final Object... paths) {
        return taskMutator.mutate("TaskOutputs.dirs(Object...)", new Callable<TaskOutputFilePropertyBuilder>() {
            @Override
            public TaskOutputFilePropertyBuilder call() throws Exception {
                return dirs(new StaticValue(resolveSingleArray(paths)));
            }
        });
    }

    @Override
    public TaskOutputFilePropertyBuilder dirs(ValidatingValue paths) {
        return addSpec(new CompositeTaskOutputPropertySpec(task.getName(), resolver, OutputType.DIRECTORY, paths, OUTPUT_DIRECTORIES_VALIDATOR));
    }

    @Nullable
    private static Object resolveSingleArray(@Nullable Object[] paths) {
        return (paths != null && paths.length == 1) ? paths[0] : paths;
    }

    private TaskOutputFilePropertyBuilder addSpec(DeclaredTaskOutputFileProperty spec) {
        declaredFileProperties.add(spec);
        return spec;
    }

    @Override
    public Set<File> getPreviousOutputFiles() {
        if (history == null) {
            throw new IllegalStateException("Task history is currently not available for this task.");
        }
        return history.getOutputFiles();
    }

    @Override
    public void setHistory(@Nullable TaskExecutionHistory history) {
        this.history = history;
    }

    @Override
    public void validate(TaskValidationContext context) {
        TaskPropertyUtils.ensurePropertiesHaveNames(declaredFileProperties);
        for (DeclaredTaskOutputFileProperty property : declaredFileProperties) {
            property.validate(context);
        }
    }

    private class TaskOutputUnionFileCollection extends CompositeFileCollection implements Describable {
        private final TaskInternal buildDependencies;

        public TaskOutputUnionFileCollection(TaskInternal buildDependencies) {
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

    private static final ValidationAction OUTPUT_FILE_VALIDATOR = new ValidationAction() {
        @Override
        public void validate(String propertyName, Object value, TaskValidationContext context, TaskValidationContext.Severity severity) {
            File file = toFile(context, value);
            if (file.exists()) {
                if (file.isDirectory()) {
                    context.recordValidationMessage(severity, String.format("Cannot write to file '%s' specified for property '%s' as it is a directory.", file, propertyName));
                }
                // else, assume we can write to anything that exists and is not a directory
            } else {
                for (File candidate = file.getParentFile(); candidate != null && !candidate.isDirectory(); candidate = candidate.getParentFile()) {
                    if (candidate.exists() && !candidate.isDirectory()) {
                        context.recordValidationMessage(severity, String.format("Cannot write to file '%s' specified for property '%s', as ancestor '%s' is not a directory.", file, propertyName, candidate));
                        break;
                    }
                }
            }
        }
    };

    private static final ValidationAction OUTPUT_FILES_VALIDATOR = new ValidationAction() {
        @Override
        public void validate(String propertyName, Object values, TaskValidationContext context, TaskValidationContext.Severity severity) {
            for (File file : toFiles(context, values)) {
                OUTPUT_FILE_VALIDATOR.validate(propertyName, file, context, severity);
            }
        }
    };

    private static final ValidationAction OUTPUT_DIRECTORY_VALIDATOR = new ValidationAction() {
        @Override
        public void validate(String propertyName, Object value, TaskValidationContext context, TaskValidationContext.Severity severity) {
            File directory = toFile(context, value);
            if (directory.exists()) {
                if (!directory.isDirectory()) {
                    context.recordValidationMessage(severity, String.format("Directory '%s' specified for property '%s' is not a directory.", directory, propertyName));
                }
            } else {
                for (File candidate = directory.getParentFile(); candidate != null && !candidate.isDirectory(); candidate = candidate.getParentFile()) {
                    if (candidate.exists() && !candidate.isDirectory()) {
                        context.recordValidationMessage(severity, String.format("Cannot write to directory '%s' specified for property '%s', as ancestor '%s' is not a directory.", directory, propertyName, candidate));
                        return;
                    }
                }
            }
        }
    };

    private static File toFile(TaskValidationContext context, Object value) {
        if (value instanceof File) {
            return (File) value;
        }
        return context.getResolver().resolve(value);
    }

    private static final ValidationAction OUTPUT_DIRECTORIES_VALIDATOR = new ValidationAction() {
        @Override
        public void validate(String propertyName, Object values, TaskValidationContext context, TaskValidationContext.Severity severity) {
            for (File directory : toFiles(context, values)) {
                OUTPUT_DIRECTORY_VALIDATOR.validate(propertyName, directory, context, severity);
            }
        }
    };

    private static Iterable<? extends File> toFiles(TaskValidationContext context, Object value) {
        if (value instanceof Map) {
            return toFiles(context, ((Map) value).values());
        } else if (value instanceof FileCollection) {
            return ((FileCollection) value).getFiles();
        } else {
            return context.getResolver().resolveFiles(value);
        }
    }
}
