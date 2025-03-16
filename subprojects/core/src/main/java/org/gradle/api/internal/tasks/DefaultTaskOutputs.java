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

import com.google.common.collect.ImmutableSortedSet;
import groovy.lang.Closure;
import org.gradle.api.Describable;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.FilePropertyContainer;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.TaskOutputsEnterpriseInternal;
import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.tasks.execution.SelfDescribingSpec;
import org.gradle.api.internal.tasks.properties.OutputFilePropertySpec;
import org.gradle.api.internal.tasks.properties.OutputFilesCollector;
import org.gradle.api.internal.tasks.properties.OutputUnpacker;
import org.gradle.api.specs.AndSpec;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskOutputFilePropertyBuilder;
import org.gradle.internal.properties.OutputFilePropertyType;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.properties.PropertyVisitor;
import org.gradle.internal.properties.StaticValue;
import org.gradle.internal.properties.bean.PropertyWalker;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

@NullMarked
public class DefaultTaskOutputs implements TaskOutputsEnterpriseInternal {
    private final FileCollection allOutputFiles;
    private final PropertyWalker propertyWalker;
    private final FileCollectionFactory fileCollectionFactory;
    private AndSpec<TaskInternal> upToDateSpec = AndSpec.empty();
    private boolean storeInCache = true;
    private final List<SelfDescribingSpec<TaskInternal>> cacheIfSpecs = new LinkedList<>();
    private final List<SelfDescribingSpec<TaskInternal>> doNotCacheIfSpecs = new LinkedList<>();
    private FileCollection previousOutputFiles;
    private final FilePropertyContainer<TaskOutputFilePropertyRegistration> registeredFileProperties = FilePropertyContainer.create();
    private final TaskInternal task;
    private final TaskMutator taskMutator;

    public DefaultTaskOutputs(final TaskInternal task, TaskMutator taskMutator, PropertyWalker propertyWalker, TaskDependencyFactory taskDependencyFactory, FileCollectionFactory fileCollectionFactory) {
        this.task = task;
        this.taskMutator = taskMutator;
        this.allOutputFiles = new TaskOutputUnionFileCollection(taskDependencyFactory, task);
        this.propertyWalker = propertyWalker;
        this.fileCollectionFactory = fileCollectionFactory;
    }

    @Override
    public void visitRegisteredProperties(PropertyVisitor visitor) {
        for (TaskOutputFilePropertyRegistration registration : registeredFileProperties) {
            visitor.visitOutputFileProperty(registration.getPropertyName(), registration.isOptional(), registration.getValue(), registration.getPropertyType());
        }
    }

    @Override
    public AndSpec<? super TaskInternal> getUpToDateSpec() {
        return upToDateSpec;
    }

    @Override
    public void upToDateWhen(final Closure upToDateClosure) {
        taskMutator.mutate("TaskOutputs.upToDateWhen(Closure)", () -> {
            upToDateSpec = upToDateSpec.and(upToDateClosure);
        });
    }

    @Override
    public void upToDateWhen(final Spec<? super Task> spec) {
        taskMutator.mutate("TaskOutputs.upToDateWhen(Spec)", () -> {
            upToDateSpec = upToDateSpec.and(spec);
        });
    }

    @Override
    public boolean getStoreInCache() {
        return storeInCache;
    }

    @Override
    public void doNotStoreInCache() {
        // Not wrapped in TaskMutator to allow calls during task execution
        storeInCache = false;
    }

    @Override
    public void cacheIf(final Spec<? super Task> spec) {
        cacheIf("Task outputs cacheable", spec);
    }

    @Override
    public void cacheIf(final String cachingEnabledReason, final Spec<? super Task> spec) {
        taskMutator.mutate("TaskOutputs.cacheIf(Spec)", () -> {
            cacheIfSpecs.add(new SelfDescribingSpec<>(spec, cachingEnabledReason));
        });
    }

    @Override
    public void doNotCacheIf(final String cachingDisabledReason, final Spec<? super Task> spec) {
        taskMutator.mutate("TaskOutputs.doNotCacheIf(Spec)", () -> {
            doNotCacheIfSpecs.add(new SelfDescribingSpec<>(spec, cachingDisabledReason));
        });
    }

    @Override
    public List<SelfDescribingSpec<TaskInternal>> getCacheIfSpecs() {
        return cacheIfSpecs;
    }

    @Override
    public List<SelfDescribingSpec<TaskInternal>> getDoNotCacheIfSpecs() {
        return doNotCacheIfSpecs;
    }

    @Override
    public boolean getHasOutput() {
        if (!upToDateSpec.isEmpty()) {
            return true;
        }
        HasDeclaredOutputsVisitor visitor = new HasDeclaredOutputsVisitor();
        TaskPropertyUtils.visitProperties(propertyWalker, task, visitor);
        return visitor.hasDeclaredOutputs();
    }

    @Override
    public FileCollection getFiles() {
        return allOutputFiles;
    }

    public ImmutableSortedSet<OutputFilePropertySpec> getFileProperties() {
        OutputFilesCollector collector = new OutputFilesCollector();
        TaskPropertyUtils.visitProperties(propertyWalker, task, new OutputUnpacker(task.toString(), fileCollectionFactory, false, false, collector));
        return collector.getFileProperties();
    }

    @Override
    public TaskOutputFilePropertyBuilder file(final Object path) {
        return taskMutator.mutate("TaskOutputs.file(Object)", (Callable<TaskOutputFilePropertyBuilder>) () -> {
            StaticValue value = new StaticValue(path);
            value.attachProducer(task);
            TaskOutputFilePropertyRegistration registration = new DefaultTaskOutputFilePropertyRegistration(value, OutputFilePropertyType.FILE);
            registeredFileProperties.add(registration);
            return registration;
        });
    }

    @Override
    public TaskOutputFilePropertyBuilder dir(final Object path) {
        return taskMutator.mutate("TaskOutputs.dir(Object)", (Callable<TaskOutputFilePropertyBuilder>) () -> {
            StaticValue value = new StaticValue(path);
            value.attachProducer(task);
            TaskOutputFilePropertyRegistration registration = new DefaultTaskOutputFilePropertyRegistration(value, OutputFilePropertyType.DIRECTORY);
            registeredFileProperties.add(registration);
            return registration;
        });
    }

    @Override
    public TaskOutputFilePropertyBuilder files(final @Nullable Object... paths) {
        return taskMutator.mutate("TaskOutputs.files(Object...)", (Callable<TaskOutputFilePropertyBuilder>) () -> {
            StaticValue value = new StaticValue(resolveSingleArray(paths));
            TaskOutputFilePropertyRegistration registration = new DefaultTaskOutputFilePropertyRegistration(value, OutputFilePropertyType.FILES);
            registeredFileProperties.add(registration);
            return registration;
        });
    }

    @Override
    public TaskOutputFilePropertyBuilder dirs(final Object... paths) {
        return taskMutator.mutate("TaskOutputs.dirs(Object...)", (Callable<TaskOutputFilePropertyBuilder>) () -> {
            StaticValue value = new StaticValue(resolveSingleArray(paths));
            TaskOutputFilePropertyRegistration registration = new DefaultTaskOutputFilePropertyRegistration(value, OutputFilePropertyType.DIRECTORIES);
            registeredFileProperties.add(registration);
            return registration;
        });
    }

    @Nullable
    private static Object resolveSingleArray(@Nullable Object[] paths) {
        return (paths != null && paths.length == 1) ? paths[0] : paths;
    }

    @Override
    public void setPreviousOutputFiles(FileCollection previousOutputFiles) {
        this.previousOutputFiles = previousOutputFiles;
    }

    @Override
    public Set<File> getPreviousOutputFiles() {
        if (previousOutputFiles == null) {
            throw new IllegalStateException("Task history is currently not available for this task.");
        }
        return previousOutputFiles.getFiles();
    }

    private static class HasDeclaredOutputsVisitor implements PropertyVisitor {
        boolean hasDeclaredOutputs;

        @Override
        public void visitOutputFileProperty(String propertyName, boolean optional, PropertyValue value, OutputFilePropertyType filePropertyType) {
            hasDeclaredOutputs = true;
        }

        public boolean hasDeclaredOutputs() {
            return hasDeclaredOutputs;
        }
    }

    private class TaskOutputUnionFileCollection extends CompositeFileCollection implements Describable {
        private final TaskInternal buildDependencies;

        public TaskOutputUnionFileCollection(TaskDependencyFactory taskDependencyFactory, TaskInternal buildDependencies) {
            super(taskDependencyFactory);
            this.buildDependencies = buildDependencies;
        }

        @Override
        public String getDisplayName() {
            return "task '" + task.getName() + "' output files";
        }

        @Override
        protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
            for (OutputFilePropertySpec propertySpec : getFileProperties()) {
                visitor.accept(propertySpec.getPropertyFiles());
            }
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            context.add(buildDependencies);
            super.visitDependencies(context);
        }
    }
}
