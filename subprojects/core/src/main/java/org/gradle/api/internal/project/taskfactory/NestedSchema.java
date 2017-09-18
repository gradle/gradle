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

package org.gradle.api.internal.project.taskfactory;

import org.gradle.api.GradleException;

import java.util.Map;

public class NestedSchema implements SchemaNode {
    private final Map<String, SchemaNode> children;
    private final DefaultSchemaExtractor.ChangeDetectionProperty property;
    private final Object parent;
    private final UpdateAction configureAction;
    private final TaskPropertyValue scannedValue;

    NestedSchema(Map<String, SchemaNode> children, DefaultSchemaExtractor.ChangeDetectionProperty property, Object parent, TaskPropertyValue taskPropertyValue) {
        this.children = children;
        this.property = property;
        this.parent = parent;
        this.configureAction = property.getConfigureAction();
        this.scannedValue = taskPropertyValue;
    }

    public Map<String, SchemaNode> getChildren() {
        return children;
    }

    @Override
    public UpdateAction getConfigureAction() {
        return configureAction;
    }

    @Override
    public Object call() throws Exception {
        checkIfConsistent();
        return property.getValue(parent).getValue();
    }

    private void checkIfConsistent() {
        Class<?> typeUsedForSchema = scannedValue.getValue().getClass();
        Object currentValue = property.getValue(parent).getValue();
        if (currentValue == null || !typeUsedForSchema.equals(currentValue.getClass())) {
            throw new GradleException("Nested property '" + property.getName() + "' changed after first scanning from " + typeUsedForSchema + " to " + currentValue +"!");
        }
    }
}
