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

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.tasks.Nested;

import java.lang.annotation.Annotation;
import java.util.concurrent.Callable;

public class NestedBeanPropertyAnnotationHandler implements PropertyAnnotationHandler {
    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return Nested.class;
    }

    @Override
    public void attachActions(final TaskPropertyActionContext context) {
        context.setNestedType(context.getValueType());
        context.setConfigureAction(new UpdateAction() {
            public void update(TaskInternal task, final Callable<Object> futureValue) {
                task.getInputs().property(context.getName() + ".class", new Callable<Object>() {
                    public Object call() throws Exception {
                        Object bean = futureValue.call();
                        return bean == null ? null : bean.getClass().getName();
                    }
                });
            }
        });
    }

}
