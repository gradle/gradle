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

package org.gradle.api.internal.project.taskfactory;

import org.gradle.api.internal.TaskInputsInternal;
import org.gradle.api.internal.tasks.DefaultTaskInputPropertySpec;
import org.gradle.api.internal.tasks.InputsOutputVisitor;
import org.gradle.api.internal.tasks.PropertyInfo;
import org.gradle.api.internal.tasks.TaskValidationContext;
import org.gradle.api.internal.tasks.ValidatingValue;
import org.gradle.api.internal.tasks.ValidationAction;
import org.gradle.api.tasks.Nested;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;

public class NestedBeanPropertyAnnotationHandler implements PropertyAnnotationHandler {
    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return Nested.class;
    }

    @Override
    public void attachActions(final TaskPropertyActionContext context) {
    }

    @Override
    public void accept(final PropertyInfo propertyInfo, InputsOutputVisitor visitor, TaskInputsInternal inputs) {
        DefaultTaskInputPropertySpec propertySpec = inputs.createInputPropertySpec(propertyInfo.getName() + ".class", new NestedPropertyValue(propertyInfo));
        propertySpec.optional(propertyInfo.isOptional());
        visitor.visitInputProperty(propertySpec);
    }

    private static class NestedPropertyValue implements ValidatingValue {
        private final PropertyInfo propertyInfo;

        public NestedPropertyValue(PropertyInfo propertyInfo) {
            this.propertyInfo = propertyInfo;
        }

        @Nullable
        @Override
        public Object call() {
            Object value = propertyInfo.getValue();
            return value == null ? null : value.getClass().getName();
        }

        @Override
        public void validate(String propertyName, boolean optional, ValidationAction valueValidator, TaskValidationContext context) {
        }

    }
}
