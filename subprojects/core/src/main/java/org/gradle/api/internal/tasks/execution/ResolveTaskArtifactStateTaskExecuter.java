/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.tasks.execution;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Multimap;
import org.gradle.api.NonNullApi;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository;
import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskFilePropertySpec;
import org.gradle.api.internal.tasks.TaskInputFilePropertySpec;
import org.gradle.api.internal.tasks.TaskInputPropertySpec;
import org.gradle.api.internal.tasks.TaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.TaskPropertyUtils;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.internal.tasks.TaskValidationContext;
import org.gradle.api.internal.tasks.ValidatingTaskPropertySpec;
import org.gradle.api.internal.tasks.properties.CompositePropertyVisitor;
import org.gradle.api.internal.tasks.properties.GetInputFilesVisitor;
import org.gradle.api.internal.tasks.properties.GetInputPropertiesVisitor;
import org.gradle.api.internal.tasks.properties.GetLocalStateVisitor;
import org.gradle.api.internal.tasks.properties.GetOutputFilesVisitor;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@NonNullApi
public class ResolveTaskArtifactStateTaskExecuter implements TaskExecuter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResolveTaskArtifactStateTaskExecuter.class);

    private final PropertyWalker propertyWalker;
    private final PathToFileResolver resolver;
    private final TaskExecuter executer;
    private final TaskArtifactStateRepository repository;

    public ResolveTaskArtifactStateTaskExecuter(TaskArtifactStateRepository repository, PathToFileResolver resolver, PropertyWalker propertyWalker, TaskExecuter executer) {
        this.propertyWalker = propertyWalker;
        this.resolver = resolver;
        this.executer = executer;
        this.repository = repository;
    }

    @Override
    public void execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        Timer clock = Time.startTimer();
        TaskProperties taskProperties = createTaskProperties(task);
        context.setTaskProperties(taskProperties);
        TaskArtifactState taskArtifactState = repository.getStateFor(task, taskProperties);
        TaskOutputsInternal outputs = task.getOutputs();

        context.setTaskArtifactState(taskArtifactState);
        outputs.setHistory(taskArtifactState.getExecutionHistory());
        LOGGER.debug("Putting task artifact state for {} into context took {}.", task, clock.getElapsed());
        try {
            executer.execute(task, state, context);
        } finally {
            outputs.setHistory(null);
            context.setTaskArtifactState(null);
            context.setTaskProperties(null);
            LOGGER.debug("Removed task artifact state for {} from context.");
        }
    }

    private TaskProperties createTaskProperties(TaskInternal task) {
        GetOutputFilesVisitor outputFilesVisitor = new GetOutputFilesVisitor();
        String beanName = task.toString();
        GetInputFilesVisitor inputFilesVisitor = new GetInputFilesVisitor(beanName);
        GetInputPropertiesVisitor inputPropertiesVisitor = new GetInputPropertiesVisitor(beanName);
        GetLocalStateVisitor localStateVisitor = new GetLocalStateVisitor(beanName, resolver);
        ValidationVisitor validationVisitor = new ValidationVisitor();
        try {
            TaskPropertyUtils.visitProperties(propertyWalker, task, new CompositePropertyVisitor(
                inputPropertiesVisitor,
                inputFilesVisitor,
                outputFilesVisitor,
                validationVisitor,
                localStateVisitor
            ));
        } catch (Exception e) {
            throw new TaskExecutionException(task, e);
        }

        return new DefaultTaskProperties(inputPropertiesVisitor, inputFilesVisitor, outputFilesVisitor, validationVisitor, localStateVisitor);
    }

    private static class DefaultTaskProperties implements TaskProperties {

        private final GetInputPropertiesVisitor inputPropertiesVisitor;
        private final GetInputFilesVisitor inputFilesVisitor;
        private final GetOutputFilesVisitor outputFilesVisitor;
        private final ValidationVisitor validationVisitor;
        private final GetLocalStateVisitor localStateVisitor;

        public DefaultTaskProperties(GetInputPropertiesVisitor inputPropertiesVisitor, GetInputFilesVisitor inputFilesVisitor, GetOutputFilesVisitor outputFilesVisitor, ValidationVisitor validationVisitor, GetLocalStateVisitor localStateVisitor) {
            this.inputPropertiesVisitor = inputPropertiesVisitor;
            this.inputFilesVisitor = inputFilesVisitor;
            this.outputFilesVisitor = outputFilesVisitor;
            this.validationVisitor = validationVisitor;
            this.localStateVisitor = localStateVisitor;
        }

        @Override
        public ImmutableSortedSet<TaskOutputFilePropertySpec> getOutputFileProperties() {
            return outputFilesVisitor.getFileProperties();
        }

        @Override
        public FileCollection getOutputFiles() {
            return new CompositeFileCollection() {
                @Override
                public String getDisplayName() {
                    return "output files";
                }

                @Override
                public void visitContents(FileCollectionResolveContext context) {
                    for (TaskFilePropertySpec propertySpec : outputFilesVisitor.getFileProperties()) {
                        context.add(propertySpec.getPropertyFiles());
                    }
                }
            };
        }

        @Override
        public FileCollection getSourceFiles() {
            return inputFilesVisitor.getSourceFiles();
        }

        @Override
        public boolean hasSourceFiles() {
            return inputFilesVisitor.hasSourceFiles();
        }

        @Override
        public FileCollection getInputFiles() {
            return inputFilesVisitor.getFiles();
        }

        @Override
        public ImmutableSortedSet<TaskInputFilePropertySpec> getInputFileProperties() {
            return inputFilesVisitor.getFileProperties();
        }

        @Override
        public void validate(TaskValidationContext validationContext) {
            for (ValidatingTaskPropertySpec validatingTaskPropertySpec : validationVisitor.getTaskPropertySpecs()) {
                validatingTaskPropertySpec.validate(validationContext);
            }
            for (Map.Entry<TaskValidationContext.Severity, String> entry : validationVisitor.getMessages().entries()) {
                validationContext.recordValidationMessage(entry.getKey(), entry.getValue());
            }
        }

        @Override
        public boolean hasDeclaredOutputs() {
            return outputFilesVisitor.hasDeclaredOutputs();
        }

        @Override
        public Map<String, Object> getInputProperties() {
            return inputPropertiesVisitor.getProperties();
        }

        @Override
        public FileCollection getLocalStateFiles() {
            return localStateVisitor.getFiles();
        }
    }

    private static class ValidationVisitor extends PropertyVisitor.Adapter {
        private final List<ValidatingTaskPropertySpec> taskPropertySpecs = new ArrayList<ValidatingTaskPropertySpec>();
        private final Multimap<TaskValidationContext.Severity, String> messages = ArrayListMultimap.create();

        @Override
        public void visitInputFileProperty(TaskInputFilePropertySpec inputFileProperty) {
            taskPropertySpecs.add((ValidatingTaskPropertySpec) inputFileProperty);
        }

        @Override
        public void visitInputProperty(TaskInputPropertySpec inputProperty) {
            taskPropertySpecs.add((ValidatingTaskPropertySpec) inputProperty);
        }

        @Override
        public void visitOutputFileProperty(TaskOutputFilePropertySpec outputFileProperty) {
            taskPropertySpecs.add((ValidatingTaskPropertySpec) outputFileProperty);
        }

        public Multimap<TaskValidationContext.Severity, String> getMessages() {
            return messages;
        }

        public List<ValidatingTaskPropertySpec> getTaskPropertySpecs() {
            return taskPropertySpecs;
        }
    }
}
