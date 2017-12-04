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

import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.internal.TaskInputsInternal;
import org.gradle.api.internal.tasks.DeclaredTaskInputFileProperty;
import org.gradle.api.internal.tasks.PropertyInfo;
import org.gradle.api.internal.tasks.TaskValidationContext;
import org.gradle.api.internal.tasks.ValidationAction;
import org.gradle.api.tasks.InputDirectory;

import java.io.File;
import java.lang.annotation.Annotation;

public class InputDirectoryPropertyAnnotationHandler extends AbstractInputPropertyAnnotationHandler {
    public Class<? extends Annotation> getAnnotationType() {
        return InputDirectory.class;
    }

    @Override
    protected DeclaredTaskInputFileProperty createFileSpec(PropertyInfo propertyInfo, TaskInputsInternal inputs) {
        return inputs.createDirSpec(propertyInfo, INPUT_DIRECTORY_VALIDATOR);
    }

    public static final ValidationAction INPUT_DIRECTORY_VALIDATOR = new ValidationAction() {
        @Override
        public void validate(String propertyName, Object value, TaskValidationContext context, TaskValidationContext.Severity severity) {
            File directory = toDirectory(context, value);
            if (!directory.exists()) {
                context.recordValidationMessage(severity, String.format("Directory '%s' specified for property '%s' does not exist.", directory, propertyName));
            } else if (!directory.isDirectory()) {
                context.recordValidationMessage(severity, String.format("Directory '%s' specified for property '%s' is not a directory.", directory, propertyName));
            }
        }
    };

    private static File toDirectory(TaskValidationContext context, Object value) {
        if (value instanceof ConfigurableFileTree) {
            return ((ConfigurableFileTree) value).getDir();
        }
        return toFile(context, value);
    }

}
