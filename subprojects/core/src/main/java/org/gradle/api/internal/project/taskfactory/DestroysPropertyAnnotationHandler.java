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

package org.gradle.api.internal.project.taskfactory;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskPropertyValue;
import org.gradle.api.tasks.Destroys;

import java.lang.annotation.Annotation;

public class DestroysPropertyAnnotationHandler implements PropertyAnnotationHandler {
    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return Destroys.class;
    }

    @Override
    public void attachActions(TaskPropertyActionContext context) {
        context.setConfigureAction(new UpdateAction() {
            @Override
            public void update(TaskInternal task, TaskPropertyValue futureValue) {
                task.getDestroyables().register(futureValue);
            }
        });
    }
}
