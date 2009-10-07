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

import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Transformer;

import java.lang.reflect.AnnotatedElement;
import java.lang.annotation.Annotation;
import java.io.File;

public class OutputDirectoryPropertyAnnotationHandler implements PropertyAnnotationHandler {
    private final ValidationAction outputDirValidation = new ValidationAction() {
        public void validate(String propertyName, Object value) throws InvalidUserDataException {
            File fileValue = (File) value;
            if (!fileValue.isDirectory() && !fileValue.mkdirs()) {
                throw new InvalidUserDataException(String.format(
                        "Cannot create directory '%s' specified for property '%s'.", fileValue, propertyName));
            }
        }
    };
    private final PropertyActions propertyActions = new PropertyActions() {
        public ValidationAction getValidationAction() {
            return outputDirValidation;
        }

        public ValidationAction getSkipAction() {
            return null;
        }

        public Transformer<Object> getTaskDependency() {
            return null;
        }

        public Transformer<Object> getInputFiles() {
            return null;
        }

        public Transformer<Object> getOutputFiles() {
            return new Transformer<Object>() {
                public Object transform(Object original) {
                    return original;
                }
            };
        }
    };

    public Class<? extends Annotation> getAnnotationType() {
        return OutputDirectory.class;
    }

    public PropertyActions getActions(AnnotatedElement target, String propertyName) {
        return propertyActions;
    }
}
