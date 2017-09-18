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

import com.google.common.base.Supplier;

import javax.annotation.Nullable;

import static org.gradle.api.internal.project.taskfactory.UpdateAction.NO_OP_CONFIGURATION_ACTION;

public class SchemaProperty implements SchemaNode {

    private final DefaultSchemaExtractor.ChangeDetectionProperty property;
    private final Object parentValue;
    private final Supplier<TaskPropertyValue> valueSupplier = new Supplier<TaskPropertyValue>() {
        @Override
        public TaskPropertyValue get() {
            return property.getValue(parentValue);
        }
    };
    private final UpdateAction configureAction;

    SchemaProperty(DefaultSchemaExtractor.ChangeDetectionProperty property, Object parentValue, @Nullable UpdateAction configureAction) {
        this.property = property;
        this.parentValue = parentValue;
        this.configureAction = configureAction == null ? NO_OP_CONFIGURATION_ACTION : configureAction;
    }

    @Override
    public UpdateAction getConfigureAction() {
        return configureAction;
    }

    @Override
    public Object call() throws Exception {
        return valueSupplier.get().getValue();
    }
}
