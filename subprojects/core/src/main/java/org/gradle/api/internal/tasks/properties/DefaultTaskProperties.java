/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks.properties;

import com.google.common.collect.ImmutableSortedSet;
import org.gradle.api.NonNullApi;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.tasks.TaskPropertyUtils;
import org.gradle.api.internal.tasks.TaskValidationContext;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.internal.reflect.TypeValidationContext.ReplayingTypeValidationContext;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

@NonNullApi
public class DefaultTaskProperties implements TaskProperties {

    private final Supplier<Map<String, Object>> inputPropertyValues;
    private final ImmutableSortedSet<InputFilePropertySpec> inputFileProperties;
    private final ImmutableSortedSet<OutputFilePropertySpec> outputFileProperties;
    private final FileCollection inputFiles;
    private final boolean hasSourceFiles;
    private final FileCollection sourceFiles;
    private final boolean hasDeclaredOutputs;
    private final ReplayingTypeValidationContext validationProblems;
    private final FileCollection outputFiles;
    private final FileCollection localStateFiles;
    private final FileCollection destroyableFiles;
    private final List<ValidatingProperty> validatingProperties;

    public static TaskProperties resolve(PropertyWalker propertyWalker, FileCollectionFactory fileCollectionFactory, TaskInternal task) {
        String beanName = task.toString();
        GetInputFilesVisitor inputFilesVisitor = new GetInputFilesVisitor(beanName, fileCollectionFactory);
        GetOutputFilesVisitor outputFilesVisitor = new GetOutputFilesVisitor(beanName, fileCollectionFactory);
        GetInputPropertiesVisitor inputPropertiesVisitor = new GetInputPropertiesVisitor(beanName);
        GetLocalStateVisitor localStateVisitor = new GetLocalStateVisitor(beanName, fileCollectionFactory);
        GetDestroyablesVisitor destroyablesVisitor = new GetDestroyablesVisitor(beanName, fileCollectionFactory);
        ValidationVisitor validationVisitor = new ValidationVisitor();
        ReplayingTypeValidationContext validationContext = new ReplayingTypeValidationContext();
        try {
            TaskPropertyUtils.visitProperties(propertyWalker, task, validationContext, new CompositePropertyVisitor(
                inputPropertiesVisitor,
                inputFilesVisitor,
                outputFilesVisitor,
                validationVisitor,
                destroyablesVisitor,
                localStateVisitor
            ));
        } catch (Exception e) {
            throw new TaskExecutionException(task, e);
        }

        return new DefaultTaskProperties(
            task.toString(),
            inputPropertiesVisitor.getPropertyValuesSupplier(),
            inputFilesVisitor.getFileProperties(),
            outputFilesVisitor.getFileProperties(),
            outputFilesVisitor.hasDeclaredOutputs(),
            localStateVisitor.getFiles(),
            destroyablesVisitor.getFiles(),
            validationVisitor.getTaskPropertySpecs(),
            validationContext);
    }

    private DefaultTaskProperties(
        String name,
        Supplier<Map<String, Object>> inputPropertyValues,
        ImmutableSortedSet<InputFilePropertySpec> inputFileProperties,
        ImmutableSortedSet<OutputFilePropertySpec> outputFileProperties,
        boolean hasDeclaredOutputs,
        FileCollection localStateFiles,
        FileCollection destroyableFiles,
        List<ValidatingProperty> validatingProperties,
        ReplayingTypeValidationContext validationProblems
    ) {
        this.validatingProperties = validatingProperties;
        this.validationProblems = validationProblems;

        this.inputPropertyValues = inputPropertyValues;
        this.inputFileProperties = inputFileProperties;
        this.outputFileProperties = outputFileProperties;
        this.hasDeclaredOutputs = hasDeclaredOutputs;

        this.inputFiles = new CompositeFileCollection() {
            @Override
            public String getDisplayName() {
                return name + " input files";
            }

            @Override
            protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
                for (InputFilePropertySpec filePropertySpec : inputFileProperties) {
                    visitor.accept(filePropertySpec.getPropertyFiles());
                }
            }
        };
        this.sourceFiles = new CompositeFileCollection() {
            @Override
            public String getDisplayName() {
                return name + " source files";
            }

            @Override
            protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
                for (InputFilePropertySpec filePropertySpec : inputFileProperties) {
                    if (filePropertySpec.isSkipWhenEmpty()) {
                        visitor.accept(filePropertySpec.getPropertyFiles());
                    }
                }
            }
        };
        this.hasSourceFiles = inputFileProperties.stream()
            .anyMatch(InputFilePropertySpec::isSkipWhenEmpty);
        this.outputFiles = new CompositeFileCollection() {
            @Override
            public String getDisplayName() {
                return "output files";
            }

            @Override
            protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
                for (FilePropertySpec propertySpec : outputFileProperties) {
                    visitor.accept(propertySpec.getPropertyFiles());
                }
            }
        };
        this.localStateFiles = localStateFiles;
        this.destroyableFiles = destroyableFiles;
    }

    @Override
    public Iterable<? extends LifecycleAwareValue> getLifecycleAwareValues() {
        return validatingProperties;
    }

    @Override
    public ImmutableSortedSet<OutputFilePropertySpec> getOutputFileProperties() {
        return outputFileProperties;
    }

    @Override
    public FileCollection getOutputFiles() {
        return outputFiles;
    }

    @Override
    public FileCollection getSourceFiles() {
        return sourceFiles;
    }

    @Override
    public boolean hasSourceFiles() {
        return hasSourceFiles;
    }

    @Override
    public FileCollection getInputFiles() {
        return inputFiles;
    }

    @Override
    public ImmutableSortedSet<InputFilePropertySpec> getInputFileProperties() {
        return inputFileProperties;
    }

    @Override
    public void validate(TaskValidationContext validationContext) {
        validationProblems.replay(null, validationContext);
        for (ValidatingProperty validatingProperty : validatingProperties) {
            validatingProperty.validate(validationContext);
        }
    }

    @Override
    public boolean hasDeclaredOutputs() {
        return hasDeclaredOutputs;
    }

    @Override
    public Supplier<Map<String, Object>> getInputPropertyValues() {
        return inputPropertyValues;
    }

    @Override
    public FileCollection getLocalStateFiles() {
        return localStateFiles;
    }

    @Override
    public FileCollection getDestroyableFiles() {
        return destroyableFiles;
    }

    private static class GetLocalStateVisitor extends PropertyVisitor.Adapter {
        private final String beanName;
        private final FileCollectionFactory fileCollectionFactory;
        private List<Object> localState = new ArrayList<>();

        public GetLocalStateVisitor(String beanName, FileCollectionFactory fileCollectionFactory) {
            this.beanName = beanName;
            this.fileCollectionFactory = fileCollectionFactory;
        }

        @Override
        public void visitLocalStateProperty(Object value) {
            localState.add(value);
        }

        public FileCollection getFiles() {
            return fileCollectionFactory.resolving(beanName + " local state", localState);
        }
    }

    private static class GetDestroyablesVisitor extends PropertyVisitor.Adapter {
        private final String beanName;
        private final FileCollectionFactory fileCollectionFactory;
        private List<Object> destroyables = new ArrayList<>();

        public GetDestroyablesVisitor(String beanName, FileCollectionFactory fileCollectionFactory) {
            this.beanName = beanName;
            this.fileCollectionFactory = fileCollectionFactory;
        }

        @Override
        public void visitDestroyableProperty(Object value) {
            destroyables.add(value);
        }

        public FileCollection getFiles() {
            return fileCollectionFactory.resolving(beanName + " destroy files", destroyables);
        }
    }

    private static class ValidationVisitor extends PropertyVisitor.Adapter {
        private final List<ValidatingProperty> taskPropertySpecs = new ArrayList<>();

        @Override
        public void visitInputFileProperty(String propertyName, boolean optional, boolean skipWhenEmpty, boolean incremental, @Nullable Class<? extends FileNormalizer> fileNormalizer, PropertyValue value, InputFilePropertyType filePropertyType) {
            taskPropertySpecs.add(new DefaultFinalizingValidatingProperty(propertyName, value, optional, filePropertyType.getValidationAction()));
        }

        @Override
        public void visitInputProperty(String propertyName, PropertyValue value, boolean optional) {
            taskPropertySpecs.add(new DefaultValidatingProperty(propertyName, value, optional, ValidationActions.NO_OP));
        }

        @Override
        public void visitOutputFileProperty(String propertyName, boolean optional, PropertyValue value, OutputFilePropertyType filePropertyType) {
            taskPropertySpecs.add(new DefaultValidatingProperty(propertyName, value, optional, filePropertyType.getValidationAction()));
        }

        public List<ValidatingProperty> getTaskPropertySpecs() {
            return taskPropertySpecs;
        }
    }
}
