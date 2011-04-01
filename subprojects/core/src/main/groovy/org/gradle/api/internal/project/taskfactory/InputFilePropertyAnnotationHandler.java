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

import org.gradle.api.Task;
import org.gradle.api.tasks.InputFile;

import java.io.File;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.concurrent.Callable;

public class InputFilePropertyAnnotationHandler implements PropertyAnnotationHandler {
    private final ValidationAction inputFileValidation = new ValidationAction() {
        public void validate(String propertyName, Object value, Collection<String> messages) {
            File fileValue = (File) value;
            if (!fileValue.exists()) {
                messages.add(String.format("File '%s' specified for property '%s' does not exist.", fileValue, propertyName));
            } else if (!fileValue.isFile()) {
                messages.add(String.format("File '%s' specified for property '%s' is not a file.", fileValue, propertyName));
            }
        }
    };

    public Class<? extends Annotation> getAnnotationType() {
        return InputFile.class;
    }

    public void attachActions(PropertyActionContext context) {
        context.setValidationAction(inputFileValidation);
        context.setConfigureAction(new UpdateAction() {
            public void update(Task task, Callable<Object> futureValue) {
                task.getInputs().files(futureValue);
            }
        });
    }
}
