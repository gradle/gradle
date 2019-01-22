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

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import org.gradle.api.NonNullApi;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.internal.tasks.TaskPropertyUtils;
import org.gradle.api.internal.tasks.TaskValidationContext;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.internal.Factory;
import org.gradle.internal.file.PathToFileResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@NonNullApi
public class DefaultTaskProperties implements TaskProperties {

    private final Factory<Map<String, Object>> inputPropertyValues;
    private final ImmutableSortedSet<InputFilePropertySpec> inputFileProperties;
    private final ImmutableSortedSet<OutputFilePropertySpec> outputFileProperties;
    private final FileCollection inputFiles;
    private final boolean hasSourceFiles;
    private final FileCollection sourceFiles;
    private final boolean hasDeclaredOutputs;
    private final FileCollection outputFiles;
    private final FileCollection localStateFiles;
    private FileCollection destroyableFiles;
    private List<ValidatingProperty> validatingProperties;

    public static TaskProperties resolve(PropertyWalker propertyWalker, PathToFileResolver resolver, TaskInternal task) {
        String beanName = task.toString();
        GetInputFilesVisitor inputFilesVisitor = new GetInputFilesVisitor(beanName, resolver);
        GetOutputFilesVisitor outputFilesVisitor = new GetOutputFilesVisitor(beanName, resolver);
        GetInputPropertiesVisitor inputPropertiesVisitor = new GetInputPropertiesVisitor(beanName);
        GetLocalStateVisitor localStateVisitor = new GetLocalStateVisitor(beanName, resolver);
        GetDestroyablesVisitor destroyablesVisitor = new GetDestroyablesVisitor(beanName, resolver);
        ValidationVisitor validationVisitor = new ValidationVisitor();
        try {
            TaskPropertyUtils.visitProperties(propertyWalker, task, new CompositePropertyVisitor(
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
            inputPropertiesVisitor.getPropertyValuesFactory(),
            inputFilesVisitor.getFileProperties(),
            outputFilesVisitor.getFileProperties(),
            outputFilesVisitor.hasDeclaredOutputs(),
            localStateVisitor.getFiles(),
            destroyablesVisitor.getFiles(),
            validationVisitor.getTaskPropertySpecs());
    }

    private DefaultTaskProperties(final String name, Factory<Map<String, Object>> inputPropertyValues, final ImmutableSortedSet<InputFilePropertySpec> inputFileProperties, final ImmutableSortedSet<OutputFilePropertySpec> outputFileProperties, boolean hasDeclaredOutputs, FileCollection localStateFiles, FileCollection destroyableFiles, List<ValidatingProperty> validatingProperties) {
        this.validatingProperties = validatingProperties;

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
            public void visitContents(FileCollectionResolveContext context) {
                for (InputFilePropertySpec filePropertySpec : inputFileProperties) {
                    context.add(filePropertySpec.getPropertyFiles());
                }
            }
        };
        this.sourceFiles = new CompositeFileCollection() {
            @Override
            public String getDisplayName() {
                return name + " source files";
            }

            @Override
            public void visitContents(FileCollectionResolveContext context) {
                for (InputFilePropertySpec filePropertySpec : inputFileProperties) {
                    if (filePropertySpec.isSkipWhenEmpty()) {
                        context.add(filePropertySpec.getPropertyFiles());
                    }
                }
            }
        };
        this.hasSourceFiles = Iterables.any(inputFileProperties, new Predicate<InputFilePropertySpec>() {
            @Override
            @SuppressWarnings("NullableProblems")
            public boolean apply(InputFilePropertySpec property) {
                return property.isSkipWhenEmpty();
            }
        });
        this.outputFiles = new CompositeFileCollection() {
            @Override
            public String getDisplayName() {
                return "output files";
            }

            @Override
            public void visitContents(FileCollectionResolveContext context) {
                for (FilePropertySpec propertySpec : outputFileProperties) {
                    context.add(propertySpec.getPropertyFiles());
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
        for (ValidatingProperty validatingProperty : validatingProperties) {
            validatingProperty.validate(validationContext);
        }
    }

    @Override
    public boolean hasDeclaredOutputs() {
        return hasDeclaredOutputs;
    }

    @Override
    public Factory<Map<String, Object>> getInputPropertyValues() {
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
        private final PathToFileResolver resolver;
        private List<Object> localState = new ArrayList<Object>();

        public GetLocalStateVisitor(String beanName, PathToFileResolver resolver) {
            this.beanName = beanName;
            this.resolver = resolver;
        }

        @Override
        public void visitLocalStateProperty(Object value) {
            localState.add(value);
        }

        public FileCollection getFiles() {
            return new DefaultConfigurableFileCollection(beanName + " local state", resolver, null, localState);
        }
    }

    private static class GetDestroyablesVisitor extends PropertyVisitor.Adapter {
        private final String beanName;
        private final PathToFileResolver resolver;
        private List<Object> destroyables = new ArrayList<Object>();

        public GetDestroyablesVisitor(String beanName, PathToFileResolver resolver) {
            this.beanName = beanName;
            this.resolver = resolver;
        }

        @Override
        public void visitDestroyableProperty(Object value) {
            destroyables.add(value);
        }

        public FileCollection getFiles() {
            return new DefaultConfigurableFileCollection(beanName + " destroy files", resolver, null, destroyables);
        }
    }

    private static class ValidationVisitor extends PropertyVisitor.Adapter {
        private final List<ValidatingProperty> taskPropertySpecs = new ArrayList<ValidatingProperty>();

        @Override
        public void visitInputFileProperty(String propertyName, boolean optional, boolean skipWhenEmpty, Class<? extends FileNormalizer> fileNormalizer, PropertyValue value, InputFilePropertyType filePropertyType) {
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
