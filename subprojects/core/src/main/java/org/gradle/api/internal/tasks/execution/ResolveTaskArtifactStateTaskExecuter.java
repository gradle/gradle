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
import org.gradle.api.internal.TaskInputsInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository;
import org.gradle.api.internal.tasks.DeclaredTaskInputFileProperty;
import org.gradle.api.internal.tasks.DeclaredTaskInputProperty;
import org.gradle.api.internal.tasks.DeclaredTaskOutputFileProperty;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskLocalStateInternal;
import org.gradle.api.internal.tasks.TaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.internal.tasks.TaskValidationContext;
import org.gradle.api.internal.tasks.ValidatingTaskPropertySpec;
import org.gradle.api.internal.tasks.properties.CompositePropertyVisitor;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.tasks.TaskExecutionException;
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

    private final TaskExecuter executer;
    private final TaskArtifactStateRepository repository;

    public ResolveTaskArtifactStateTaskExecuter(TaskArtifactStateRepository repository, TaskExecuter executer) {
        this.executer = executer;
        this.repository = repository;
    }

    @Override
    public void execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        Timer clock = Time.startTimer();
        task.setInputsAndOutputs(createTaskInputsAndOutputs(task));
        TaskArtifactState taskArtifactState = repository.getStateFor(task);
        TaskOutputsInternal outputs = task.getOutputs();

        context.setTaskArtifactState(taskArtifactState);
        outputs.setHistory(taskArtifactState.getExecutionHistory());
        LOGGER.debug("Putting task artifact state for {} into context took {}.", task, clock.getElapsed());
        try {
            executer.execute(task, state, context);
        } finally {
            outputs.setHistory(null);
            context.setTaskArtifactState(null);
            task.setInputsAndOutputs(null);
            LOGGER.debug("Removed task artifact state for {} from context.");
        }
    }

    private static TaskProperties createTaskInputsAndOutputs(TaskInternal task) {
        TaskOutputsInternal.GetFilePropertiesVisitor outputFilePropertiesVisitor = task.getOutputs().getFilePropertiesVisitor();
        TaskInputsInternal.GetFilePropertiesVisitor inputFilePropertiesVisitor = task.getInputs().getFilePropertiesVisitor();
        TaskInputsInternal.GetInputPropertiesVisitor inputPropertiesVisitor = task.getInputs().getInputPropertiesVisitor();
        TaskLocalStateInternal.GetFilesVisitor localStateFilesVisitor = ((TaskLocalStateInternal) task.getLocalState()).getFilesVisitor();
        ValidatingPropertyVisitor taskValidationVisitor = new ValidatingPropertyVisitor();
        try {
            task.visitProperties(new CompositePropertyVisitor(
                inputPropertiesVisitor,
                inputFilePropertiesVisitor,
                outputFilePropertiesVisitor,
                taskValidationVisitor,
                localStateFilesVisitor
            ));
        } catch (Exception e) {
            throw new TaskExecutionException(task, e);
        }

        return new DefaultTaskProperties(inputPropertiesVisitor, inputFilePropertiesVisitor, outputFilePropertiesVisitor, taskValidationVisitor, localStateFilesVisitor);
    }

    private static class DefaultTaskProperties implements TaskProperties {

        private final TaskInputsInternal.GetInputPropertiesVisitor inputPropertiesVisitor;
        private final TaskInputsInternal.GetFilePropertiesVisitor inputFilePropertiesVisitor;
        private final TaskOutputsInternal.GetFilePropertiesVisitor outputFilesVisitor;
        private final ValidatingPropertyVisitor validationVisitor;
        private final TaskLocalStateInternal.GetFilesVisitor localStateFilesVisitor;

        public DefaultTaskProperties(TaskInputsInternal.GetInputPropertiesVisitor inputPropertiesVisitor, TaskInputsInternal.GetFilePropertiesVisitor inputFilePropertiesVisitor, TaskOutputsInternal.GetFilePropertiesVisitor outputFilesVisitor, ValidatingPropertyVisitor validationVisitor, TaskLocalStateInternal.GetFilesVisitor localStateFilesVisitor) {
            this.inputPropertiesVisitor = inputPropertiesVisitor;
            this.inputFilePropertiesVisitor = inputFilePropertiesVisitor;
            this.outputFilesVisitor = outputFilesVisitor;
            this.validationVisitor = validationVisitor;
            this.localStateFilesVisitor = localStateFilesVisitor;
        }

        @Override
        public ImmutableSortedSet<TaskOutputFilePropertySpec> getOutputFileProperties() {
            return outputFilesVisitor.getFileProperties();
        }

        @Override
        public FileCollection getOutputFiles() {
            return outputFilesVisitor.getFiles();
        }

        @Override
        public FileCollection getSourceFiles() {
            return inputFilePropertiesVisitor.getSourceFiles();
        }

        @Override
        public boolean hasSourceFiles() {
            return inputFilePropertiesVisitor.hasSourceFiles();
        }

        @Override
        public FileCollection getInputFiles() {
            return inputFilePropertiesVisitor.getFiles();
        }

        @Override
        public ImmutableSortedSet<DeclaredTaskInputFileProperty> getInputFileProperties() {
            return inputFilePropertiesVisitor.getFileProperties();
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
            return localStateFilesVisitor.getFiles();
        }
    }

    private static class ValidatingPropertyVisitor extends PropertyVisitor.Adapter {
        private final List<ValidatingTaskPropertySpec> taskPropertySpecs = new ArrayList<ValidatingTaskPropertySpec>();
        private final Multimap<TaskValidationContext.Severity, String> messages = ArrayListMultimap.create();

        public ValidatingPropertyVisitor() {
        }

        @Override
        public void visitInputFileProperty(DeclaredTaskInputFileProperty inputFileProperty) {
            taskPropertySpecs.add(inputFileProperty);
        }

        @Override
        public void visitInputProperty(DeclaredTaskInputProperty inputProperty) {
            taskPropertySpecs.add(inputProperty);
        }

        @Override
        public void visitOutputFileProperty(DeclaredTaskOutputFileProperty outputFileProperty) {
            taskPropertySpecs.add(outputFileProperty);
        }

        public Multimap<TaskValidationContext.Severity, String> getMessages() {
            return messages;
        }

        public List<ValidatingTaskPropertySpec> getTaskPropertySpecs() {
            return taskPropertySpecs;
        }
    }
}
