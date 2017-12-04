/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.internal.tasks.DeclaredTaskInputFileProperty;
import org.gradle.api.internal.tasks.InputsOutputVisitor;
import org.gradle.api.internal.tasks.PropertyInfo;
import org.gradle.api.internal.tasks.PropertySpecFactory;
import org.gradle.api.internal.tasks.TaskValidationContext;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;

import java.io.File;

abstract class AbstractInputPropertyAnnotationHandler implements PropertyAnnotationHandler {

    public void attachActions(final TaskPropertyActionContext context) {
    }

    @Override
    public void accept(PropertyInfo propertyInfo, InputsOutputVisitor visitor, PropertySpecFactory specFactory) {
        PathSensitive pathSensitive = propertyInfo.getAnnotation(PathSensitive.class);
        final PathSensitivity pathSensitivity;
        if (pathSensitive == null) {
//            FIXME wolfs
//            if (context.isCacheable()) {
//                context.validationMessage("is missing a @PathSensitive annotation, defaulting to PathSensitivity.ABSOLUTE");
//            }
            pathSensitivity = PathSensitivity.ABSOLUTE;
        } else {
            pathSensitivity = pathSensitive.value();
        }
        DeclaredTaskInputFileProperty fileSpec = createFileSpec(propertyInfo, specFactory);
        fileSpec
            .withPropertyName(propertyInfo.getName()).optional(propertyInfo.isOptional())
            .withPathSensitivity(pathSensitivity)
            .skipWhenEmpty(propertyInfo.isAnnotationPresent(SkipWhenEmpty.class))
            .optional(propertyInfo.isOptional());
        visitor.visitInputFileProperty(fileSpec);
    }

    protected abstract DeclaredTaskInputFileProperty createFileSpec(PropertyInfo propertyInfo, PropertySpecFactory specFactory);

    protected static File toFile(TaskValidationContext context, Object value) {
        return context.getResolver().resolve(value);
    }
}
