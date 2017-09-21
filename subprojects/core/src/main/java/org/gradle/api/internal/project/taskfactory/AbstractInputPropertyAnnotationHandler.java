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

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskPropertyValue;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskInputFilePropertyBuilder;

abstract class AbstractInputPropertyAnnotationHandler implements PropertyAnnotationHandler {

    public void attachActions(final TaskPropertyActionContext context) {
        PathSensitive pathSensitive = context.getAnnotation(PathSensitive.class);
        final PathSensitivity pathSensitivity;
        if (pathSensitive == null) {
            if (context.isCacheable()) {
                context.validationMessage("is missing a @PathSensitive annotation, defaulting to PathSensitivity.ABSOLUTE");
            }
            pathSensitivity = PathSensitivity.ABSOLUTE;
        } else {
            pathSensitivity = pathSensitive.value();
        }

        context.setConfigureAction(new UpdateAction() {
            public void update(TaskInternal task, TaskPropertyValue futureValue) {
                final TaskInputFilePropertyBuilder propertyBuilder = createPropertyBuilder(context, task, futureValue);
                propertyBuilder
                    .withPropertyName(context.getName())
                    .withPathSensitivity(pathSensitivity)
                    .skipWhenEmpty(context.isAnnotationPresent(SkipWhenEmpty.class))
                    .optional(context.isOptional());
            }
        });
    }

    protected abstract TaskInputFilePropertyBuilder createPropertyBuilder(TaskPropertyActionContext context, TaskInternal task, TaskPropertyValue futureValue);
}
