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
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.tasks.*;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.concurrent.Callable;

public class InputDirectoryPropertyAnnotationHandler implements PropertyAnnotationHandler {
    private final ValidationAction inputDirValidation = new ValidationAction() {
        public void validate(String propertyName, Object value) throws InvalidUserDataException {
            File fileValue = (value instanceof ConfigurableFileTree) ? ((ConfigurableFileTree) value).getDir() : (File) value;
            if (!fileValue.exists()) {
                throw new InvalidUserDataException(String.format(
                        "Directory '%s' specified for property '%s' does not exist.", fileValue, propertyName));
            }
            if (!fileValue.isDirectory()) {
                throw new InvalidUserDataException(String.format(
                        "Directory '%s' specified for property '%s' is not a directory.", fileValue, propertyName));
            }
        }
    };
    private final ValidationAction skipEmptyDirectoryAction = new ValidationAction() {
        public void validate(String propertyName, Object value) throws InvalidUserDataException {
            File fileValue = (File) value;
            if (!fileValue.exists() || fileValue.isDirectory() && fileValue.list().length == 0) {
                throw new StopExecutionException(String.format("Directory %s is empty or does not exist.", fileValue));
            }
        }
    };

    public Class<? extends Annotation> getAnnotationType() {
        return InputDirectory.class;
    }

    public PropertyActions getActions(final AnnotatedElement target, String propertyName) {
        return new PropertyActions() {
            public ValidationAction getValidationAction() {
                return inputDirValidation;
            }

            public ValidationAction getSkipAction() {
                if (target.getAnnotation(SkipWhenEmpty.class) != null) {
                    return skipEmptyDirectoryAction;
                }
                return null;
            }

            public void attachInputs(TaskInputs inputs, Callable<Object> futureValue) {
                inputs.dir(futureValue);
            }

            public void attachOutputs(TaskOutputs outputs, Callable<Object> futureValue) {
            }
        };
    }
}
