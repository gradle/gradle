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
import org.gradle.api.internal.project.taskfactory.DefaultSchemaExtractor;
import org.gradle.api.internal.project.taskfactory.SchemaExtractor;
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
    private final TaskInputsInternal taskInputs;
    private final TaskOutputsInternal taskOutputs;
    private final TaskDestroyables taskDestroyables;
    private DefaultSchemaExtractor.SchemaRoot schemaRoot;

    public ChangeDetection(TaskInternal task, SchemaExtractor schemaExtractor, FileResolver fileResolver, TaskMutator taskMutator) {
        this.task = task;
        this.schemaExtractor = schemaExtractor;
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
            registerInputsAndOutputs(schemaRoot);
        }
    }

    private void registerInputsAndOutputs(DefaultSchemaExtractor.SchemaRoot schemaRoot) {
        registerInputsAndOutputs(null, schemaRoot.getChildren());
    }

    private void registerInputsAndOutputs(@Nullable String parentPropertyName, Map<String, DefaultSchemaExtractor.SchemaNode> children) {
        for (Map.Entry<String, DefaultSchemaExtractor.SchemaNode> entry : children.entrySet()) {
            String propertyName = parentPropertyName == null ? entry.getKey() : parentPropertyName + "." + entry.getKey();
            DefaultSchemaExtractor.SchemaNode node = entry.getValue();
            updateInputsAndOutputs(node, propertyName);
            if (node instanceof DefaultSchemaExtractor.NestedSchema) {
                registerInputsAndOutputs(propertyName, ((DefaultSchemaExtractor.NestedSchema) node).getChildren());
            }
        }
    }

    private void updateInputsAndOutputs(DefaultSchemaExtractor.SchemaNode value, String propertyName) {
        value.getConfigureAction().updateInputs(taskInputs, propertyName, value);
        value.getConfigureAction().updateOutputs(taskOutputs, propertyName, value);
        value.getConfigureAction().updateDestroyables(taskDestroyables, propertyName, value);
    }

}
