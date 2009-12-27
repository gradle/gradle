/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.project;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskInputs;
import org.gradle.api.tasks.TaskOutputs;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.concurrent.Callable;

public class OutputFilePropertyAnnotationHandler implements PropertyAnnotationHandler {
    private final ValidationAction ouputFileValidation = new ValidationAction() {
        public void validate(String propertyName, Object value) throws InvalidUserDataException {
            File fileValue = GFileUtils.canonicalise((File) value);
            if (fileValue.exists() && !fileValue.isFile()) {
                throw new InvalidUserDataException(String.format(
                        "Cannot write to file '%s' specified for property '%s' as it is a directory.", fileValue,
                        propertyName));
            }
            if (!fileValue.getParentFile().isDirectory() && !fileValue.getParentFile().mkdirs()) {
                throw new InvalidUserDataException(String.format(
                        "Cannot create parent directory '%s' of file specified for property '%s'.",
                        fileValue.getParentFile(), propertyName));
            }
        }
    };
    private final PropertyActions propertyActions = new PropertyActions() {
        public ValidationAction getValidationAction() {
            return ouputFileValidation;
        }

        public ValidationAction getSkipAction() {
            return null;
        }

        public void attachInputs(TaskInputs inputs, Callable<Object> futureValue) {
        }

        public void attachOutputs(TaskOutputs outputs, Callable<Object> futureValue) {
            outputs.files(futureValue);
        }
    };

    public Class<? extends Annotation> getAnnotationType() {
        return OutputFile.class;
    }

    public PropertyActions getActions(AnnotatedElement target, String propertyName) {
        return propertyActions;
    }
}
