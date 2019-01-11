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
import org.gradle.api.NonNullApi;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.FilePropertyContainer;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.internal.tasks.execution.SelfDescribingSpec;
import org.gradle.api.internal.tasks.properties.GetOutputFilesVisitor;
import org.gradle.api.internal.tasks.properties.OutputFilePropertyType;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.api.specs.AndSpec;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskOutputFilePropertyBuilder;
import org.gradle.internal.file.TreeType;

import javax.annotation.Nullable;
import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

@NonNullApi
public class DefaultTaskOutputs implements TaskOutputsInternal {
    private final FileCollection allOutputFiles;
    private final PropertyWalker propertyWalker;
    private final FileResolver fileResolver;
    private AndSpec<TaskInternal> upToDateSpec = AndSpec.empty();
    private List<SelfDescribingSpec<TaskInternal>> cacheIfSpecs = new LinkedList<SelfDescribingSpec<TaskInternal>>();
    private List<SelfDescribingSpec<TaskInternal>> doNotCacheIfSpecs = new LinkedList<SelfDescribingSpec<TaskInternal>>();
    private FileCollection previousOutputFiles;
    private final FilePropertyContainer<DeclaredTaskOutputFileProperty> registeredFileProperties = FilePropertyContainer.create();
    private final TaskInternal task;
    private final TaskMutator taskMutator;

    public DefaultTaskOutputs(final TaskInternal task, TaskMutator taskMutator, PropertyWalker propertyWalker, FileResolver fileResolver) {
        this.task = task;
        this.taskMutator = taskMutator;
        this.allOutputFiles = new TaskOutputUnionFileCollection(task);
        this.propertyWalker = propertyWalker;
        this.fileResolver = fileResolver;
    }

    @Override
    public void visitRegisteredProperties(PropertyVisitor visitor) {
        for (DeclaredTaskOutputFileProperty fileProperty : registeredFileProperties) {
            OutputFilePropertyType filePropertyType = determineFilePropertyType(fileProperty);
            visitor.visitOutputFileProperty(fileProperty.getPropertyName(), fileProperty.isOptional(), fileProperty.getValidatingValue(), filePropertyType);
        }
    }

    private static OutputFilePropertyType determineFilePropertyType(DeclaredTaskOutputFileProperty fileProperty) {
        TreeType treeType = fileProperty.getOutputType();
        if (fileProperty instanceof CompositeTaskOutputPropertySpec) {
            return treeType == TreeType.FILE ? OutputFilePropertyType.FILES : OutputFilePropertyType.DIRECTORIES;
        }
        return treeType == TreeType.FILE ? OutputFilePropertyType.FILE : OutputFilePropertyType.DIRECTORY;
    }

    @Override
    public AndSpec<? super TaskInternal> getUpToDateSpec() {
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

    public ImmutableSortedSet<TaskOutputFilePropertySpec> getFileProperties() {
        GetOutputFilesVisitor visitor = new GetOutputFilesVisitor(task.toString(), fileResolver);
        TaskPropertyUtils.visitProperties(propertyWalker, task, visitor);
        return visitor.getFileProperties();
    }

    @Override
    public TaskOutputFilePropertyBuilder file(final Object path) {
        return taskMutator.mutate("TaskOutputs.file(Object)", new Callable<TaskOutputFilePropertyBuilder>() {
            @Override
            public TaskOutputFilePropertyBuilder call() {
                StaticValue value = new StaticValue(path);
                value.attachProducer(task);
                DeclaredTaskOutputFileProperty outputFileSpec = createOutputFileSpec(value);
                registeredFileProperties.add(outputFileSpec);
                return outputFileSpec;
            }
        });
    }

    @Override
    public TaskOutputFilePropertyBuilder dir(final Object path) {
        return taskMutator.mutate("TaskOutputs.dir(Object)", new Callable<TaskOutputFilePropertyBuilder>() {
            @Override
            public TaskOutputFilePropertyBuilder call() {
                StaticValue value = new StaticValue(path);
                value.attachProducer(task);
                DeclaredTaskOutputFileProperty outputDirSpec = createOutputDirSpec(value);
                registeredFileProperties.add(outputDirSpec);
                return outputDirSpec;
            }
        });
    }

    @Override
    public TaskOutputFilePropertyBuilder files(final @Nullable Object... paths) {
        return taskMutator.mutate("TaskOutputs.files(Object...)", new Callable<TaskOutputFilePropertyBuilder>() {
            @Override
            public TaskOutputFilePropertyBuilder call() {
                StaticValue value = new StaticValue(resolveSingleArray(paths));
                DeclaredTaskOutputFileProperty outputFilesSpec = createOutputFilesSpec(value);
                registeredFileProperties.add(outputFilesSpec);
                return outputFilesSpec;
            }
        });
    }

    @Override
    public TaskOutputFilePropertyBuilder dirs(final Object... paths) {
        return taskMutator.mutate("TaskOutputs.dirs(Object...)", new Callable<TaskOutputFilePropertyBuilder>() {
            @Override
            public TaskOutputFilePropertyBuilder call() {
                StaticValue value = new StaticValue(resolveSingleArray(paths));
                DeclaredTaskOutputFileProperty outputDirsSpec = createOutputDirsSpec(value);
                registeredFileProperties.add(outputDirsSpec);
                return outputDirsSpec;
            }
        });
    }

    @Nullable
    private static Object resolveSingleArray(@Nullable Object[] paths) {
        return (paths != null && paths.length == 1) ? paths[0] : paths;
    }

    private DeclaredTaskOutputFileProperty createOutputFileSpec(ValidatingValue path) {
        return createOutputFilePropertySpec(path, TreeType.FILE, ValidationActions.OUTPUT_FILE_VALIDATOR);
    }

    private DeclaredTaskOutputFileProperty createOutputDirSpec(ValidatingValue path) {
        return createOutputFilePropertySpec(path, TreeType.DIRECTORY, ValidationActions.OUTPUT_DIRECTORY_VALIDATOR);
    }

    private DeclaredTaskOutputFileProperty createOutputFilesSpec(ValidatingValue paths) {
        return new CompositeTaskOutputPropertySpec(task.toString(), fileResolver, TreeType.FILE, paths, ValidationActions.OUTPUT_FILES_VALIDATOR);
    }

    private DeclaredTaskOutputFileProperty createOutputDirsSpec(ValidatingValue paths) {
        return new CompositeTaskOutputPropertySpec(task.toString(), fileResolver, TreeType.DIRECTORY, paths, ValidationActions.OUTPUT_DIRECTORIES_VALIDATOR);
    }

    private DefaultCacheableTaskOutputFilePropertySpec createOutputFilePropertySpec(ValidatingValue path, TreeType type, ValidationAction outputFileValidator) {
        return new DefaultCacheableTaskOutputFilePropertySpec(task.toString(), fileResolver, type, path, outputFileValidator);
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

    private static class HasDeclaredOutputsVisitor extends PropertyVisitor.Adapter {
        boolean hasDeclaredOutputs;

        @Override
        public void visitOutputFileProperty(String propertyName, boolean optional, ValidatingValue value, OutputFilePropertyType filePropertyType) {
            hasDeclaredOutputs = true;
        }

        public boolean hasDeclaredOutputs() {
            return hasDeclaredOutputs;
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
}
