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

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.tasks.OutputFile;

import java.io.File;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.concurrent.Callable;

import static org.gradle.api.internal.project.taskfactory.OutputPropertyAnnotationUtil.validateFile;
import static org.gradle.api.internal.project.taskfactory.PropertyAnnotationUtils.getPathSensitivity;
import static org.gradle.api.internal.tasks.TaskOutputsUtil.ensureParentDirectoryExists;
import static org.gradle.util.GUtil.uncheckedCall;

public class OutputFilePropertyAnnotationHandler extends AbstractOutputPropertyAnnotationHandler {

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return OutputFile.class;
    }

    @Override
    protected void validate(String propertyName, Object value, Collection<String> messages) {
        validateFile(propertyName, (File) value, messages);
    }

    @Override
    protected void update(TaskPropertyActionContext context, TaskInternal task, final Callable<Object> futureValue) {
        task.getOutputs().file(futureValue)
            .withPropertyName(context.getName())
            .withPathSensitivity(getPathSensitivity(context));
        task.prependParallelSafeAction(new Action<Task>() {
            public void execute(Task task) {
                File file = (File) uncheckedCall(futureValue);
                if (file != null) {
                    ensureParentDirectoryExists(file);
                }
            }
        });
    }
}
