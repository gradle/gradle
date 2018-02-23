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

package org.gradle.api.internal.tasks;

import org.gradle.util.DeferredUtil;

import static org.gradle.api.internal.tasks.TaskValidationContext.Severity.WARNING;

public class StaticValue implements ValidatingValue {
    private final Object value;

    public StaticValue(Object value) {
        this.value = value;
    }

    @Override
    public Object call() {
        return value;
    }

    @Override
    public void validate(String propertyName, boolean optional, ValidationAction valueValidator, TaskValidationContext context) {
        Object unpacked = DeferredUtil.unpack(value);
        if (unpacked == null) {
            if (!optional) {
                context.recordValidationMessage(WARNING, String.format("No value has been specified for property '%s'.", propertyName));
            }
        } else {
            valueValidator.validate(propertyName, unpacked, context, WARNING);
        }
    }
}
