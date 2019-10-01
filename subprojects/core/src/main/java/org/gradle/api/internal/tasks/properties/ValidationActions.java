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

package org.gradle.api.internal.tasks.properties;

import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.TaskValidationContext;
import org.gradle.internal.typeconversion.UnsupportedNotationException;

import java.io.File;
import java.util.Map;

import static org.gradle.internal.reflect.TypeValidationContext.Severity.ERROR;

public enum ValidationActions implements ValidationAction {
    NO_OP("file collection") {
        @Override
        public void doValidate(String propertyName, Object value, TaskValidationContext context) {
        }
    },
    INPUT_FILE_VALIDATOR("file") {
        @Override
        public void doValidate(String propertyName, Object value, TaskValidationContext context) {
            File file = toFile(context, value);
            if (!file.exists()) {
                context.visitPropertyProblem(ERROR, String.format("File '%s' specified for property '%s' does not exist", file, propertyName));
            } else if (!file.isFile()) {
                context.visitPropertyProblem(ERROR, String.format("File '%s' specified for property '%s' is not a file", file, propertyName));
            }
        }
    },
    INPUT_DIRECTORY_VALIDATOR("directory") {
        @Override
        public void doValidate(String propertyName, Object value, TaskValidationContext context) {
            File directory = toDirectory(context, value);
            if (!directory.exists()) {
                context.visitPropertyProblem(ERROR, String.format("Directory '%s' specified for property '%s' does not exist", directory, propertyName));
            } else if (!directory.isDirectory()) {
                context.visitPropertyProblem(ERROR, String.format("Directory '%s' specified for property '%s' is not a directory", directory, propertyName));
            }
        }
    },
    OUTPUT_DIRECTORY_VALIDATOR("file") {
        @Override
        public void doValidate(String propertyName, Object value, TaskValidationContext context) {
            File directory = toFile(context, value);
            validateNotInReservedFileSystemLocation(context, directory);
            if (directory.exists()) {
                if (!directory.isDirectory()) {
                    context.visitPropertyProblem(ERROR, String.format("Directory '%s' specified for property '%s' is not a directory", directory, propertyName));
                }
            } else {
                for (File candidate = directory.getParentFile(); candidate != null && !candidate.isDirectory(); candidate = candidate.getParentFile()) {
                    if (candidate.exists() && !candidate.isDirectory()) {
                        context.visitPropertyProblem(ERROR, String.format("Cannot write to directory '%s' specified for property '%s', as ancestor '%s' is not a directory", directory, propertyName, candidate));
                        return;
                    }
                }
            }
        }
    },
    OUTPUT_DIRECTORIES_VALIDATOR("file collection") {
        @Override
        public void doValidate(String propertyName, Object values, TaskValidationContext context) {
            for (File directory : toFiles(context, values)) {
                OUTPUT_DIRECTORY_VALIDATOR.validate(propertyName, directory, context);
            }
        }
    },
    OUTPUT_FILE_VALIDATOR("file") {
        @Override
        public void doValidate(String propertyName, Object value, TaskValidationContext context) {
            File file = toFile(context, value);
            validateNotInReservedFileSystemLocation(context, file);
            if (file.exists()) {
                if (file.isDirectory()) {
                    context.visitPropertyProblem(ERROR, String.format("Cannot write to file '%s' specified for property '%s' as it is a directory", file, propertyName));
                }
                // else, assume we can write to anything that exists and is not a directory
            } else {
                for (File candidate = file.getParentFile(); candidate != null && !candidate.isDirectory(); candidate = candidate.getParentFile()) {
                    if (candidate.exists() && !candidate.isDirectory()) {
                        context.visitPropertyProblem(ERROR, String.format("Cannot write to file '%s' specified for property '%s', as ancestor '%s' is not a directory", file, propertyName, candidate));
                        break;
                    }
                }
            }
        }
    },
    OUTPUT_FILES_VALIDATOR("file collection") {
        @Override
        public void doValidate(String propertyName, Object values, TaskValidationContext context) {
            for (File file : toFiles(context, values)) {
                OUTPUT_FILE_VALIDATOR.validate(propertyName, file, context);
            }
        }
    };

    private static void validateNotInReservedFileSystemLocation(TaskValidationContext context, File location) {
        if (context.isInReservedFileSystemLocation(location)) {
            context.visitPropertyProblem(ERROR, String.format("The output %s must not be in a reserved location", location));
        }
    }

    private final String targetType;

    ValidationActions(String targetType) {
        this.targetType = targetType;
    }

    protected abstract void doValidate(String propertyName, Object value, TaskValidationContext context);

    @Override
    public void validate(String propertyName, Object value, TaskValidationContext context) {
        try {
            doValidate(propertyName, value, context);
        } catch (UnsupportedNotationException ignored) {
            context.visitPropertyProblem(ERROR, String.format("Value '%s' specified for property '%s' cannot be converted to a %s", value, propertyName, targetType));
        }
    }

    private static File toDirectory(TaskValidationContext context, Object value) {
        if (value instanceof ConfigurableFileTree) {
            return ((ConfigurableFileTree) value).getDir();
        }
        return toFile(context, value);
    }

    private static File toFile(TaskValidationContext context, Object value) {
        return context.getFileOperations().file(value);
    }

    private static Iterable<? extends File> toFiles(TaskValidationContext context, Object value) {
        if (value instanceof Map) {
            return toFiles(context, ((Map) value).values());
        } else if (value instanceof FileCollection) {
            return (FileCollection) value;
        } else {
            return context.getFileOperations().immutableFiles(value);
        }
    }
}
