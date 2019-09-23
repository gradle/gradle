/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.api.internal.tasks.TaskValidationContext;
import org.gradle.util.DeferredUtil;

import static org.gradle.internal.reflect.TypeValidationContext.Severity.ERROR;

public abstract class AbstractValidatingProperty implements ValidatingProperty {
    private final String propertyName;
    private final PropertyValue value;
    private final boolean optional;
    private final ValidationAction validationAction;

    public AbstractValidatingProperty(String propertyName, PropertyValue value, boolean optional, ValidationAction validationAction) {
        this.propertyName = propertyName;
        this.value = value;
        this.optional = optional;
        this.validationAction = validationAction;
    }

    @Override
    public void validate(TaskValidationContext context) {
        Object unpacked = DeferredUtil.unpack(value.call());
        if (unpacked == null) {
            if (!optional) {
                context.visitPropertyProblem(ERROR, String.format("No value has been specified for property '%s'", propertyName));
            }
        } else {
            validationAction.validate(propertyName, unpacked, context);
        }
    }

    @Override
    public void prepareValue() {
        value.maybeFinalizeValue();
    }

    @Override
    public void cleanupValue() {
    }
}
