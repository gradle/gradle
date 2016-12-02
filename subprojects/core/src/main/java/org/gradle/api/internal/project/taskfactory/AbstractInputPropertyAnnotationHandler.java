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
import org.gradle.api.tasks.OrderSensitive;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskInputFilePropertyBuilder;
import org.gradle.util.DeprecationLogger;

import java.util.Collection;
import java.util.concurrent.Callable;

import static org.gradle.api.internal.project.taskfactory.PropertyAnnotationUtils.getPathSensitivity;

abstract class AbstractInputPropertyAnnotationHandler implements PropertyAnnotationHandler {

    public void attachActions(final TaskPropertyActionContext context) {
        context.setValidationAction(new ValidationAction() {
            @Override
            public void validate(String propertyName, Object value, Collection<String> messages) {
                AbstractInputPropertyAnnotationHandler.this.validate(propertyName, value, messages);
            }
        });
        context.setConfigureAction(new UpdateAction() {
            public void update(TaskInternal task, Callable<Object> futureValue) {
                final TaskInputFilePropertyBuilder propertyBuilder = createPropertyBuilder(context, task, futureValue);
                propertyBuilder
                    .withPropertyName(context.getName())
                    .withPathSensitivity(getPathSensitivity(context))
                    .skipWhenEmpty(context.isAnnotationPresent(SkipWhenEmpty.class))
                    .optional(context.isOptional());
                handleOrderSensitive(propertyBuilder, context);
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void handleOrderSensitive(final TaskInputFilePropertyBuilder propertyBuilder, TaskPropertyActionContext context) {
        if (context.isAnnotationPresent(OrderSensitive.class)) {
            DeprecationLogger.nagUserOfDeprecated("The @OrderSensitive annotation", "For classpath properties, use the @Classpath annotation instead");
            DeprecationLogger.whileDisabled(new Runnable() {
                @Override
                public void run() {
                    propertyBuilder.orderSensitive();
                }
            });
        }
    }

    protected abstract TaskInputFilePropertyBuilder createPropertyBuilder(TaskPropertyActionContext context, TaskInternal task, Callable<Object> futureValue);
    protected abstract void validate(String propertyName, Object value, Collection<String> messages);
}
