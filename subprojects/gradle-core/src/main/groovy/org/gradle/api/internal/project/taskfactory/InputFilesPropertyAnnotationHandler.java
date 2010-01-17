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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskInputs;
import org.gradle.api.tasks.TaskOutputs;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.concurrent.Callable;

public class InputFilesPropertyAnnotationHandler implements PropertyAnnotationHandler {
    private final ValidationAction skipEmptyFileCollection = new ValidationAction() {
        public void validate(String propertyName, Object value) throws InvalidUserDataException {
            if (value instanceof FileCollection) {
                ((FileCollection) value).stopExecutionIfEmpty();
            }
        }
    };

    public Class<? extends Annotation> getAnnotationType() {
        return InputFiles.class;
    }

    public PropertyActions getActions(AnnotatedElement target, String propertyName) {
        ValidationAction skipAction = null;
        if (target.getAnnotation(SkipWhenEmpty.class) != null) {
            skipAction = skipEmptyFileCollection;
        }
        final ValidationAction finalSkipAction = skipAction;
        return new PropertyActions() {
            public ValidationAction getValidationAction() {
                return null;
            }

            public ValidationAction getSkipAction() {
                return finalSkipAction;
            }

            public void attachInputs(TaskInputs inputs, Callable<Object> futureValue) {
                inputs.files(futureValue);
            }

            public void attachOutputs(TaskOutputs outputs, Callable<Object> futureValue) {
            }
        };
    }
}
