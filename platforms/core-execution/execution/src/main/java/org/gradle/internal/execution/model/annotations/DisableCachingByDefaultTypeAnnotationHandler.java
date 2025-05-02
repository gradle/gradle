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

package org.gradle.internal.execution.model.annotations;

import org.gradle.api.Task;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.internal.properties.annotations.AbstractTypeAnnotationHandler;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.work.DisableCachingByDefault;

public class DisableCachingByDefaultTypeAnnotationHandler extends AbstractTypeAnnotationHandler {
    public DisableCachingByDefaultTypeAnnotationHandler() {
        super(DisableCachingByDefault.class);
    }

    @Override
    public void validateTypeMetadata(Class<?> classWithAnnotationAttached, TypeValidationContext visitor) {
        if (!Task.class.isAssignableFrom(classWithAnnotationAttached) && !TransformAction.class.isAssignableFrom(classWithAnnotationAttached)) {
            reportInvalidUseOfTypeAnnotation(classWithAnnotationAttached,
                visitor,
                getAnnotationType(),
                Task.class, TransformAction.class);
        }
    }
}
