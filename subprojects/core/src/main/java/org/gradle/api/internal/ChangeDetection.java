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

package org.gradle.api.internal;

import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.taskfactory.NestedSchema;
import org.gradle.api.internal.project.taskfactory.SchemaExtractor;
import org.gradle.api.internal.project.taskfactory.SchemaNode;
import org.gradle.api.internal.project.taskfactory.SchemaRoot;
import org.gradle.api.internal.tasks.DefaultInputPropertyRegistration;
import org.gradle.api.internal.tasks.DefaultOutputPropertyRegistration;
import org.gradle.api.internal.tasks.DefaultTaskDestroyables;
import org.gradle.api.internal.tasks.DefaultTaskInputs;
import org.gradle.api.internal.tasks.DefaultTaskOutputs;
import org.gradle.api.internal.tasks.TaskMutator;
import org.gradle.api.tasks.TaskDestroyables;

import javax.annotation.Nullable;
import java.util.Map;

public class ChangeDetection {

    private final TaskInternal task;
    private final SchemaExtractor schemaExtractor;
    private final DefaultTaskInputs taskInputs;
    private final DefaultTaskOutputs taskOutputs;
    private final TaskDestroyables taskDestroyables;
    private final TaskMutator taskMutator;
    private final FileResolver resolver;
    private SchemaRoot schemaRoot;

    public ChangeDetection(TaskInternal task, SchemaExtractor schemaExtractor, FileResolver fileResolver, TaskMutator taskMutator) {
        this.task = task;
        this.taskMutator = taskMutator;
        this.schemaExtractor = schemaExtractor;
        this.resolver = fileResolver;
        this.taskInputs = new DefaultTaskInputs(fileResolver, task, taskMutator, this);
        this.taskOutputs = new DefaultTaskOutputs(fileResolver, task, taskMutator, this);
        this.taskDestroyables = new DefaultTaskDestroyables(fileResolver, task, taskMutator, this);
    }

    public TaskInputsInternal getInputs() {
        return taskInputs;
    }

    public TaskOutputsInternal getOutputs() {
        return taskOutputs;
    }

    public TaskDestroyables getDestroyables() {
        return taskDestroyables;
    }


    public void ensureTaskInputsAndOutputsDiscovered() {
        if (schemaRoot == null) {
            schemaRoot = schemaExtractor.extractSchema(task);
            String taskName = task.getName();
            DefaultInputPropertyRegistration inputPropertyRegistration = new DefaultInputPropertyRegistration(taskName, taskMutator, resolver);
            DefaultOutputPropertyRegistration outputPropertyRegistration = new DefaultOutputPropertyRegistration(taskName, taskMutator, resolver);
            registerInputsAndOutputs(inputPropertyRegistration, outputPropertyRegistration, schemaRoot);
            taskInputs.setDiscoveredProperties(inputPropertyRegistration);
            taskOutputs.setDiscoveredProperties(outputPropertyRegistration);
        }
    }

    private void registerInputsAndOutputs(DefaultInputPropertyRegistration inputPropertyRegistration, DefaultOutputPropertyRegistration outputPropertyRegistration, SchemaRoot schemaRoot) {
        registerInputsAndOutputs(inputPropertyRegistration, outputPropertyRegistration, null, schemaRoot.getChildren());
    }

    private void registerInputsAndOutputs(DefaultInputPropertyRegistration inputPropertyRegistration, DefaultOutputPropertyRegistration outputPropertyRegistration, @Nullable String parentPropertyName, Map<String, SchemaNode> children) {
        for (Map.Entry<String, SchemaNode> entry : children.entrySet()) {
            String propertyName = parentPropertyName == null ? entry.getKey() : parentPropertyName + "." + entry.getKey();
            SchemaNode node = entry.getValue();
            updateInputsAndOutputs(inputPropertyRegistration, outputPropertyRegistration, node, propertyName);
            if (node instanceof NestedSchema) {
                registerInputsAndOutputs(inputPropertyRegistration, outputPropertyRegistration, propertyName, ((NestedSchema) node).getChildren());
            }
        }
    }

    private void updateInputsAndOutputs(DefaultInputPropertyRegistration inputPropertyRegistration, DefaultOutputPropertyRegistration outputPropertyRegistration, SchemaNode value, String propertyName) {
        value.getConfigureAction().updateInputs(inputPropertyRegistration, propertyName, value);
        value.getConfigureAction().updateOutputs(outputPropertyRegistration, propertyName, value);
        value.getConfigureAction().updateDestroyables(taskDestroyables, propertyName, value);
    }

}
