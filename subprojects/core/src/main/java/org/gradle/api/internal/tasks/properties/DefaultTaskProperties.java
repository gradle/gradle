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
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.tasks.StaticValue;
import org.gradle.api.internal.tasks.TaskPropertyUtils;
import org.gradle.api.internal.tasks.TaskValidationContext;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.LineEndingSensitivity;
import org.gradle.internal.reflect.validation.ReplayingTypeValidationContext;
import org.gradle.internal.reflect.validation.TypeValidationContext;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@NonNullApi
public class DefaultTaskProperties implements TaskProperties {

    private final ImmutableSortedSet<InputPropertySpec> inputProperties;
    private final ImmutableSortedSet<InputFilePropertySpec> inputFileProperties;
    private final ImmutableSortedSet<OutputFilePropertySpec> outputFileProperties;
    private final boolean hasDeclaredOutputs;
    private final ReplayingTypeValidationContext validationProblems;
    private final FileCollection localStateFiles;
    private final FileCollection destroyableFiles;
    private final List<ValidatingProperty> validatingProperties;

    public static TaskProperties resolve(PropertyWalker propertyWalker, FileCollectionFactory fileCollectionFactory, TaskInternal task) {
        String beanName = task.toString();
        GetInputPropertiesVisitor inputPropertiesVisitor = new GetInputPropertiesVisitor();
        GetInputFilesVisitor inputFilesVisitor = new GetInputFilesVisitor(beanName, fileCollectionFactory);
        ValidationVisitor validationVisitor = new ValidationVisitor();
        OutputFilesCollector outputFilesCollector = new OutputFilesCollector();
        OutputUnpacker outputUnpacker = new OutputUnpacker(
            beanName,
            fileCollectionFactory,
            true,
            true,
            OutputUnpacker.UnpackedOutputConsumer.composite(outputFilesCollector, validationVisitor)
        );
        GetLocalStateVisitor localStateVisitor = new GetLocalStateVisitor(beanName, fileCollectionFactory);
        GetDestroyablesVisitor destroyablesVisitor = new GetDestroyablesVisitor(beanName, fileCollectionFactory);
        ReplayingTypeValidationContext validationContext = new ReplayingTypeValidationContext();
        try {
            TaskPropertyUtils.visitProperties(propertyWalker, task, validationContext, new CompositePropertyVisitor(
                inputPropertiesVisitor,
                inputFilesVisitor,
                outputUnpacker,
                validationVisitor,
                destroyablesVisitor,
                localStateVisitor
            ));
        } catch (Exception e) {
            throw new TaskExecutionException(task, e);
        }

        return new DefaultTaskProperties(
            inputPropertiesVisitor.getProperties(),
            inputFilesVisitor.getFileProperties(),
            outputFilesCollector.getFileProperties(),
            outputUnpacker.hasDeclaredOutputs(),
            localStateVisitor.getFiles(),
            destroyablesVisitor.getFiles(),
            validationVisitor.getTaskPropertySpecs(),
            validationContext);
    }

    private DefaultTaskProperties(
        ImmutableSortedSet<InputPropertySpec> inputProperties,
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

        this.inputProperties = inputProperties;
        this.inputFileProperties = inputFileProperties;
        this.outputFileProperties = outputFileProperties;
        this.hasDeclaredOutputs = hasDeclaredOutputs;
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
    public ImmutableSortedSet<InputFilePropertySpec> getInputFileProperties() {
        return inputFileProperties;
    }

    @Override
    public void validateType(TypeValidationContext validationContext) {
        validationProblems.replay(null, validationContext);
    }

    @Override
    public void validate(TaskValidationContext validationContext) {
        for (ValidatingProperty validatingProperty : validatingProperties) {
            validatingProperty.validate(validationContext);
        }
    }

    @Override
    public boolean hasDeclaredOutputs() {
        return hasDeclaredOutputs;
    }

    @Override
    public ImmutableSortedSet<InputPropertySpec> getInputProperties() {
        return inputProperties;
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
        private final List<Object> localState = new ArrayList<>();

        public GetLocalStateVisitor(String beanName, FileCollectionFactory fileCollectionFactory) {
            this.beanName = beanName;
            this.fileCollectionFactory = fileCollectionFactory;
        }

        @Override
        public void visitLocalStateProperty(Object value) {
            localState.add(value);
        }

        public FileCollection getFiles() {
            return fileCollectionFactory.resolvingLeniently(beanName + " local state", localState);
        }
    }

    private static class GetDestroyablesVisitor extends PropertyVisitor.Adapter {
        private final String beanName;
        private final FileCollectionFactory fileCollectionFactory;
        private final List<Object> destroyables = new ArrayList<>();

        public GetDestroyablesVisitor(String beanName, FileCollectionFactory fileCollectionFactory) {
            this.beanName = beanName;
            this.fileCollectionFactory = fileCollectionFactory;
        }

        @Override
        public void visitDestroyableProperty(Object value) {
            destroyables.add(value);
        }

        public FileCollection getFiles() {
            return fileCollectionFactory.resolvingLeniently(beanName + " destroy files", destroyables);
        }
    }

    private static class ValidationVisitor extends PropertyVisitor.Adapter implements OutputUnpacker.UnpackedOutputConsumer {
        private final List<ValidatingProperty> taskPropertySpecs = new ArrayList<>();

        @Override
        public void visitInputFileProperty(
            String propertyName,
            boolean optional,
            boolean skipWhenEmpty,
            DirectorySensitivity directorySensitivity,
            LineEndingSensitivity lineEndingSensitivity,
            boolean incremental,
            @Nullable Class<? extends FileNormalizer> fileNormalizer,
            PropertyValue value,
            InputFilePropertyType filePropertyType
        ) {
            taskPropertySpecs.add(new DefaultFinalizingValidatingProperty(propertyName, value, optional, filePropertyType.getValidationAction()));
        }

        @Override
        public void visitInputProperty(String propertyName, PropertyValue value, boolean optional) {
            taskPropertySpecs.add(new DefaultValidatingProperty(propertyName, value, optional, ValidationActions.NO_OP));
        }

        @Override
        public void visitUnpackedOutputFileProperty(String propertyName, boolean optional, PropertyValue value, OutputFilePropertySpec spec) {
            taskPropertySpecs.add(new DefaultValidatingProperty(
                propertyName,
                new StaticValue(spec.getOutputFile()),
                optional,
                ValidationActions.outputValidationActionFor(spec))
            );
        }

        @Override
        public void visitEmptyOutputFileProperty(String propertyName, boolean optional, PropertyValue value) {
            taskPropertySpecs.add(new DefaultValidatingProperty(propertyName, value, optional, ValidationActions.NO_OP));
        }

        public List<ValidatingProperty> getTaskPropertySpecs() {
            return taskPropertySpecs;
        }
    }
}
