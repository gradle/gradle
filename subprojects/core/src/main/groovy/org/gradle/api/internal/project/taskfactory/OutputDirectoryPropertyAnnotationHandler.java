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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.util.GFileUtils;
import org.gradle.util.UncheckedException;

import java.io.File;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.concurrent.Callable;

public class OutputDirectoryPropertyAnnotationHandler implements PropertyAnnotationHandler {
    private final ValidationAction outputDirValidation = new ValidationAction() {
        public void validate(String propertyName, Object value, Collection<String> messages) {
            File fileValue = (File) value;
            if (fileValue.exists() && !fileValue.isDirectory()) {
                messages.add(String.format("Directory '%s' specified for property '%s' is not a directory.", fileValue, propertyName));
                return;
            }

            for (File candidate = fileValue; candidate != null && !candidate.isDirectory(); candidate = candidate.getParentFile()) {
                if (candidate.exists() && !candidate.isDirectory()) {
                    messages.add(String.format("Cannot write to directory '%s' specified for property '%s', as ancestor '%s' is not a directory.", fileValue, propertyName, candidate));
                    break;
                }
            }
        }
    };

    public Class<? extends Annotation> getAnnotationType() {
        return OutputDirectory.class;
    }

    public void attachActions(final PropertyActionContext context) {
        context.setValidationAction(outputDirValidation);
        context.setConfigureAction(new UpdateAction() {
            public void update(Task task, final Callable<Object> futureValue) {
                task.getOutputs().files(futureValue);
                task.doFirst(new Action<Task>() {
                    public void execute(Task task) {
                        File fileValue;
                        try {
                            fileValue = (File) futureValue.call();
                        } catch (Exception e) {
                            throw UncheckedException.asUncheckedException(e);
                        }
                        if (fileValue == null) {
                            return;
                        }
                        fileValue = GFileUtils.canonicalise(fileValue);
                        if (!fileValue.isDirectory() && !fileValue.mkdirs()) {
                            throw new InvalidUserDataException(String.format(
                                    "Cannot create directory '%s' specified for property '%s'.", fileValue, context.getName()));
                        }
                    }
                });
            }
        });
    }
}
