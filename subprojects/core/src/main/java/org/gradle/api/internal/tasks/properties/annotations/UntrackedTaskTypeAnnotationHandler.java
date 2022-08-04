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

package org.gradle.api.internal.tasks.properties.annotations;

import org.gradle.api.Task;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.internal.reflect.validation.TypeValidationContext;

import java.lang.annotation.Annotation;

import static org.gradle.api.internal.tasks.properties.annotations.TypeAnnotationHandlerSupport.reportInvalidUseOfTypeAnnotation;

public class UntrackedTaskTypeAnnotationHandler implements TypeAnnotationHandler {
    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return UntrackedTask.class;
    }

    @Override
    public void validateTypeMetadata(Class<?> classWithAnnotationAttached, TypeValidationContext visitor) {
        if (!Task.class.isAssignableFrom(classWithAnnotationAttached)) {
            reportInvalidUseOfTypeAnnotation(classWithAnnotationAttached, visitor, getAnnotationType(), Task.class);
        }
    }

}
