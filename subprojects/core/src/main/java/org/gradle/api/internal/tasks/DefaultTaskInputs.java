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
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.internal.tasks.properties.GetInputFilesVisitor;
import org.gradle.api.internal.tasks.properties.GetInputPropertiesVisitor;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.api.tasks.TaskInputPropertyBuilder;
import org.gradle.api.tasks.TaskInputs;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@NonNullApi
public class DefaultTaskInputs implements TaskInputsInternal {
    private final FileCollection allInputFiles;
    private final FileCollection allSourceFiles;
    private final TaskInternal task;
    private final TaskMutator taskMutator;
    private final PropertyWalker propertyWalker;
    private final List<DeclaredTaskInputProperty> registeredProperties = Lists.newArrayList();
    private final FilePropertyContainer<DeclaredTaskInputFileProperty> registeredFileProperties = FilePropertyContainer.create();
    private final TaskInputs deprecatedThis;
    private final PropertySpecFactory specFactory;

    public DefaultTaskInputs(TaskInternal task, TaskMutator taskMutator, PropertyWalker propertyWalker, PropertySpecFactory specFactory) {
        this.task = task;
        this.taskMutator = taskMutator;
        this.propertyWalker = propertyWalker;
        String taskDisplayName = task.toString();
        this.allInputFiles = new TaskInputUnionFileCollection(taskDisplayName, "input", false, task, propertyWalker);
        this.allSourceFiles = new TaskInputUnionFileCollection(taskDisplayName, "source", true, task, propertyWalker);
        this.deprecatedThis = new TaskInputsDeprecationSupport();
        this.specFactory = specFactory;
    }

    @Override
    public boolean getHasInputs() {
        HasInputsVisitor visitor = new HasInputsVisitor();
        TaskPropertyUtils.visitProperties(propertyWalker, task, visitor);
        return visitor.hasInputs();
    }

    @Override
    public void visitRegisteredProperties(PropertyVisitor visitor) {
        for (DeclaredTaskInputFileProperty fileProperty : registeredFileProperties) {
            visitor.visitInputFileProperty(fileProperty);
        }
        for (DeclaredTaskInputProperty inputProperty : registeredProperties) {
            visitor.visitInputProperty(inputProperty);
        }
    }

    @Override
    public FileCollection getFiles() {
        return allInputFiles;
    }

    @Override
    public TaskInputFilePropertyBuilderInternal files(final Object... paths) {
        return taskMutator.mutate("TaskInputs.files(Object...)", new Callable<TaskInputFilePropertyBuilderInternal>() {
            @Override
            public TaskInputFilePropertyBuilderInternal call() {
                StaticValue value = new StaticValue(unpackVarargs(paths));
                DeclaredTaskInputFileProperty fileSpec = specFactory.createInputFilesSpec(value);
                registeredFileProperties.add(fileSpec);
                return fileSpec;
            }
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
        return taskMutator.mutate("TaskInputs.file(Object)", new Callable<TaskInputFilePropertyBuilderInternal>() {
            @Override
            public TaskInputFilePropertyBuilderInternal call() {
                StaticValue value = new StaticValue(path);
                DeclaredTaskInputFileProperty fileSpec = specFactory.createInputFileSpec(value);
                registeredFileProperties.add(fileSpec);
                return fileSpec;
            }
        });
    }

    @Override
    public TaskInputFilePropertyBuilderInternal dir(final Object dirPath) {
        return taskMutator.mutate("TaskInputs.dir(Object)", new Callable<TaskInputFilePropertyBuilderInternal>() {
            @Override
            public TaskInputFilePropertyBuilderInternal call() {
                StaticValue value = new StaticValue(dirPath);
                DeclaredTaskInputFileProperty dirSpec = specFactory.createInputDirSpec(value);
                registeredFileProperties.add(dirSpec);
                return dirSpec;
            }
        });
    }

    @Override
    public boolean getHasSourceFiles() {
        GetInputFilesVisitor visitor = new GetInputFilesVisitor();
        TaskPropertyUtils.visitProperties(propertyWalker, task, visitor);
        return visitor.hasSourceFiles();
    }

    @Override
    public FileCollection getSourceFiles() {
        return allSourceFiles;
    }

    @Override
    public Map<String, Object> getProperties() {
        GetInputPropertiesVisitor visitor = new GetInputPropertiesVisitor(task.getName());
        TaskPropertyUtils.visitProperties(propertyWalker, task, visitor);
        //noinspection ConstantConditions
        return visitor.getPropertyValuesFactory().create();
    }

    @Override
    public TaskInputPropertyBuilder property(final String name, @Nullable final Object value) {
        return taskMutator.mutate("TaskInputs.property(String, Object)", new Callable<TaskInputPropertyBuilder>() {
            @Override
            public TaskInputPropertyBuilder call() {
                StaticValue staticValue = new StaticValue(value);
                DefaultTaskInputPropertySpec inputPropertySpec = specFactory.createInputPropertySpec(name, staticValue);
                registeredProperties.add(inputPropertySpec);
                return inputPropertySpec;
            }
        });
    }

    @Override
    public TaskInputs properties(final Map<String, ?> newProps) {
        taskMutator.mutate("TaskInputs.properties(Map)", new Runnable() {
            @Override
            public void run() {
                for (Map.Entry<String, ?> entry : newProps.entrySet()) {
                    StaticValue staticValue = new StaticValue(entry.getValue());
                    String name = entry.getKey();
                    registeredProperties.add(specFactory.createInputPropertySpec(name, staticValue));
                }
            }
        });
        return deprecatedThis;
    }

    private static class TaskInputUnionFileCollection extends CompositeFileCollection implements Describable {
        private final boolean skipWhenEmptyOnly;
        private final String taskDisplayName;
        private final String type;
        private final TaskInternal task;
        private final PropertyWalker propertyWalker;

        TaskInputUnionFileCollection(String taskDisplayName, String type, boolean skipWhenEmptyOnly, TaskInternal task, PropertyWalker propertyWalker) {
            this.taskDisplayName = taskDisplayName;
            this.type = type;
            this.skipWhenEmptyOnly = skipWhenEmptyOnly;
            this.task = task;
            this.propertyWalker = propertyWalker;
        }

        @Override
        public String getDisplayName() {
            return taskDisplayName + " " + type + " files";
        }

        @Override
        public void visitContents(final FileCollectionResolveContext context) {
            TaskPropertyUtils.visitProperties(propertyWalker, task, new PropertyVisitor.Adapter() {
                @Override
                public void visitInputFileProperty(TaskInputFilePropertySpec fileProperty) {
                    if (!TaskInputUnionFileCollection.this.skipWhenEmptyOnly || fileProperty.isSkipWhenEmpty()) {
                        context.add(fileProperty.getPropertyFiles());
                    }
                }
            });
        }
    }

    private static class HasInputsVisitor extends PropertyVisitor.Adapter {
        private boolean hasInputs;

        public boolean hasInputs() {
            return hasInputs;
        }

        @Override
        public void visitInputFileProperty(TaskInputFilePropertySpec inputFileProperty) {
            hasInputs = true;
        }

        @Override
        public void visitInputProperty(TaskInputPropertySpec inputProperty) {
            hasInputs = true;
        }
    }

}
