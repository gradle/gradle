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

import com.google.common.collect.Lists;
import org.gradle.api.Describable;
import org.gradle.api.NonNullApi;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.FilePropertyContainer;
import org.gradle.api.internal.TaskInputsInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.tasks.properties.FilePropertySpec;
import org.gradle.api.internal.tasks.properties.InputFilePropertySpec;
import org.gradle.api.internal.tasks.properties.InputParameterUtils;
import org.gradle.api.internal.tasks.properties.InputPropertySpec;
import org.gradle.api.internal.tasks.properties.TaskProperties;
import org.gradle.api.tasks.TaskInputPropertyBuilder;
import org.gradle.api.tasks.TaskInputs;
import org.gradle.internal.properties.InputBehavior;
import org.gradle.internal.properties.InputFilePropertyType;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.properties.PropertyVisitor;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import static org.gradle.api.internal.tasks.properties.TaskProperties.ResolutionState.DEPENDENCIES;
import static org.gradle.api.internal.tasks.properties.TaskProperties.ResolutionState.FINALIZED;
import static org.gradle.api.internal.tasks.properties.TaskProperties.ResolutionState.IDENTITIES;

@NonNullApi
public class DefaultTaskInputs implements TaskInputsInternal {
    private final FileCollection allInputFiles;
    private final FileCollection allSourceFiles;
    private final TaskInternal task;
    private final TaskMutator taskMutator;
    private final List<TaskInputPropertyRegistration> registeredProperties = Lists.newArrayList();
    private final FilePropertyContainer<TaskInputFilePropertyRegistration> registeredFileProperties = FilePropertyContainer.create();
    private final TaskInputs deprecatedThis;

    public DefaultTaskInputs(TaskInternal task, TaskMutator taskMutator) {
        this.task = task;
        this.taskMutator = taskMutator;
        String taskDisplayName = task.toString();
        this.allInputFiles = new TaskInputUnionFileCollection(taskDisplayName, "input", false, task);
        this.allSourceFiles = new TaskInputUnionFileCollection(taskDisplayName, "source", true, task);
        this.deprecatedThis = new TaskInputsDeprecationSupport();
    }

    @Override
    public boolean getHasInputs() {
        TaskProperties taskProperties = task.getTaskProperties(IDENTITIES);
        return !taskProperties.getInputProperties().isEmpty() || !taskProperties.getInputFileProperties().isEmpty();
    }

    @Override
    public void visitRegisteredProperties(PropertyVisitor visitor) {
        for (TaskInputFilePropertyRegistration registration : registeredFileProperties) {
            visitor.visitInputFileProperty(
                registration.getPropertyName(),
                registration.isOptional(),
                registration.isSkipWhenEmpty()
                    ? InputBehavior.PRIMARY
                    : InputBehavior.NON_INCREMENTAL,
                registration.getDirectorySensitivity(),
                registration.getLineEndingNormalization(),
                registration.getNormalizer(),
                registration.getValue(),
                registration.getFilePropertyType()
            );
        }
        for (TaskInputPropertyRegistration registration : registeredProperties) {
            visitor.visitInputProperty(registration.getPropertyName(), registration.getValue(), registration.isOptional());
        }
    }

    @Override
    public FileCollection getFiles() {
        return allInputFiles;
    }

    @Override
    public TaskInputFilePropertyBuilderInternal files(final Object... paths) {
        return taskMutator.mutate("TaskInputs.files(Object...)", (Callable<TaskInputFilePropertyBuilderInternal>) () -> {
            StaticValue value = new StaticValue(unpackVarargs(paths));
            TaskInputFilePropertyRegistration registration = new DefaultTaskInputFilePropertyRegistration(value, InputFilePropertyType.FILES);
            registeredFileProperties.add(registration);
            return registration;
        });
    }

    private static Object unpackVarargs(Object[] args) {
        if (args.length == 1) {
            return args[0];
        }
        return args;
    }

    @Override
    public TaskInputFilePropertyBuilderInternal file(final Object path) {
        return taskMutator.mutate("TaskInputs.file(Object)", (Callable<TaskInputFilePropertyBuilderInternal>) () -> {
            StaticValue value = new StaticValue(path);
            TaskInputFilePropertyRegistration registration = new DefaultTaskInputFilePropertyRegistration(value, InputFilePropertyType.FILE);
            registeredFileProperties.add(registration);
            return registration;
        });
    }

    @Override
    public TaskInputFilePropertyBuilderInternal dir(final Object dirPath) {
        return taskMutator.mutate("TaskInputs.dir(Object)", (Callable<TaskInputFilePropertyBuilderInternal>) () -> {
            StaticValue value = new StaticValue(dirPath);
            TaskInputFilePropertyRegistration registration = new DefaultTaskInputFilePropertyRegistration(value, InputFilePropertyType.DIRECTORY);
            // Being an input directory implies ignoring of empty directories.
            registration.ignoreEmptyDirectories();
            registeredFileProperties.add(registration);
            return registration;
        });
    }

    @Override
    public boolean getHasSourceFiles() {
        return task.getTaskProperties(IDENTITIES).getInputFileProperties().stream()
            .map(InputFilePropertySpec::getBehavior)
            .anyMatch(InputBehavior::shouldSkipWhenEmpty);
    }

    @Override
    public FileCollection getSourceFiles() {
        return allSourceFiles;
    }

    @Override
    public Map<String, Object> getProperties() {
        Map<String, Object> result = new HashMap<>();
        for (InputPropertySpec inputProperty : task.getTaskProperties(FINALIZED).getInputProperties()) {
            result.put(inputProperty.getPropertyName(), InputParameterUtils.prepareInputParameterValue(inputProperty, task));
        }
        return Collections.unmodifiableMap(result);
    }

    @Override
    public TaskInputPropertyBuilder property(final String name, @Nullable final Object value) {
        return taskMutator.mutate("TaskInputs.property(String, Object)", (Callable<TaskInputPropertyBuilder>) () -> {
            StaticValue staticValue = new StaticValue(value);
            TaskInputPropertyRegistration registration = new DefaultTaskInputPropertyRegistration(name, staticValue);
            registeredProperties.add(registration);
            return registration;
        });
    }

    @Override
    public TaskInputs properties(final Map<String, ?> newProps) {
        taskMutator.mutate("TaskInputs.properties(Map)", () -> {
            for (Map.Entry<String, ?> entry : newProps.entrySet()) {
                StaticValue staticValue = new StaticValue(entry.getValue());
                String name = entry.getKey();
                registeredProperties.add(new DefaultTaskInputPropertyRegistration(name, staticValue));
            }
        });
        return deprecatedThis;
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        TaskProperties taskProperties = task.getTaskProperties(DEPENDENCIES);
        taskProperties.getInputProperties().stream()
            .map(InputPropertySpec::getValue)
            .map(PropertyValue::getTaskDependencies)
            .forEach(context::add);
        taskProperties.getInputFileProperties().stream()
            .map(FilePropertySpec::getPropertyFiles)
            .forEach(context::add);
    }

    private static class TaskInputUnionFileCollection extends CompositeFileCollection implements Describable {
        private final boolean skipWhenEmptyOnly;
        private final String taskDisplayName;
        private final String type;
        private final TaskInternal task;

        TaskInputUnionFileCollection(String taskDisplayName, String type, boolean skipWhenEmptyOnly, TaskInternal task) {
            this.taskDisplayName = taskDisplayName;
            this.type = type;
            this.skipWhenEmptyOnly = skipWhenEmptyOnly;
            this.task = task;
        }

        @Override
        public String getDisplayName() {
            return taskDisplayName + " " + type + " files";
        }

        @Override
        protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
            task.getTaskProperties(FINALIZED).getInputFileProperties().stream()
                .filter(property -> !TaskInputUnionFileCollection.this.skipWhenEmptyOnly || property.getBehavior().shouldSkipWhenEmpty())
                .map(FilePropertySpec::getPropertyFiles)
                .forEach(visitor);
        }
    }
}
