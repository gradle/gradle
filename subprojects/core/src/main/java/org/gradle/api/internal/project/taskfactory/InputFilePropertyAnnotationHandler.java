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

import org.gradle.api.internal.tasks.DeclaredTaskInputFileProperty;
import org.gradle.api.internal.tasks.PropertyInfo;
import org.gradle.api.internal.tasks.PropertySpecFactory;
import org.gradle.api.internal.tasks.TaskValidationContext;
import org.gradle.api.internal.tasks.ValidationAction;
import org.gradle.api.tasks.InputFile;

import java.io.File;
import java.lang.annotation.Annotation;

public class InputFilePropertyAnnotationHandler extends AbstractInputPropertyAnnotationHandler {
    public Class<? extends Annotation> getAnnotationType() {
        return InputFile.class;
    }

    @Override
    protected DeclaredTaskInputFileProperty createFileSpec(PropertyInfo propertyInfo, PropertySpecFactory specFactory) {
        return specFactory.createInputFileSpec(propertyInfo, INPUT_FILE_VALIDATOR);
    }

    public static final ValidationAction INPUT_FILE_VALIDATOR = new ValidationAction() {
        @Override
        public void validate(String propertyName, Object value, TaskValidationContext context, TaskValidationContext.Severity severity) {
            File file = toFile(context, value);
            if (!file.exists()) {
                context.recordValidationMessage(severity, String.format("File '%s' specified for property '%s' does not exist.", file, propertyName));
            } else if (!file.isFile()) {
                context.recordValidationMessage(severity, String.format("File '%s' specified for property '%s' is not a file.", file, propertyName));
            }
        }
    };
}
