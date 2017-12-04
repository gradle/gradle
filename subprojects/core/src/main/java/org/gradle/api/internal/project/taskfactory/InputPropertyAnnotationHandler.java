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
import org.gradle.api.tasks.Input;

import java.lang.annotation.Annotation;

public class InputPropertyAnnotationHandler implements PropertyAnnotationHandler {
    public Class<? extends Annotation> getAnnotationType() {
        return Input.class;
    }

    @SuppressWarnings("Since15")
    public void attachActions(final TaskPropertyActionContext context) {
    }

    @Override
    public void accept(PropertyInfo propertyInfo, InputsOutputVisitor visitor, TaskInputsInternal inputs) {
        DefaultTaskInputPropertySpec declaration = inputs.createInputPropertySpec(propertyInfo.getName(), propertyInfo);
        declaration.optional(propertyInfo.isOptional());
        visitor.visitInputProperty(declaration);
//        FIXME wolfs
//        Class<?> valueType = context.getValueType();
//        if (File.class.isAssignableFrom(valueType)
//            || java.nio.file.Path.class.isAssignableFrom(valueType)
//            || FileCollection.class.isAssignableFrom(valueType)) {
//            context.validationMessage("has @Input annotation used on property of type " + valueType.getName());
//        }
    }

}
