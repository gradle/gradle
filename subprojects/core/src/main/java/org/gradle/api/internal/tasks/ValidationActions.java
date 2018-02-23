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

package org.gradle.api.internal.tasks;

import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileCollection;

import java.io.File;
import java.util.Map;

public enum ValidationActions implements ValidationAction {
    NO_OP {
        @Override
        public void validate(String propertyName, Object value, TaskValidationContext context, TaskValidationContext.Severity severity) {
        }
    },
    INPUT_FILE_VALIDATOR {
        @Override
        public void validate(String propertyName, Object value, TaskValidationContext context, TaskValidationContext.Severity severity) {
            File file = toFile(context, value);
            if (!file.exists()) {
                context.recordValidationMessage(severity, String.format("File '%s' specified for property '%s' does not exist.", file, propertyName));
            } else if (!file.isFile()) {
                context.recordValidationMessage(severity, String.format("File '%s' specified for property '%s' is not a file.", file, propertyName));
            }
        }
    },
    INPUT_DIRECTORY_VALIDATOR {
        @Override
        public void validate(String propertyName, Object value, TaskValidationContext context, TaskValidationContext.Severity severity) {
            File directory = toDirectory(context, value);
            if (!directory.exists()) {
                context.recordValidationMessage(severity, String.format("Directory '%s' specified for property '%s' does not exist.", directory, propertyName));
            } else if (!directory.isDirectory()) {
                context.recordValidationMessage(severity, String.format("Directory '%s' specified for property '%s' is not a directory.", directory, propertyName));
            }
        }
    },
    OUTPUT_DIRECTORY_VALIDATOR {
        @Override
        public void validate(String propertyName, Object value, TaskValidationContext context, TaskValidationContext.Severity severity) {
            File directory = toFile(context, value);
            if (directory.exists()) {
                if (!directory.isDirectory()) {
                    context.recordValidationMessage(severity, String.format("Directory '%s' specified for property '%s' is not a directory.", directory, propertyName));
                }
            } else {
                for (File candidate = directory.getParentFile(); candidate != null && !candidate.isDirectory(); candidate = candidate.getParentFile()) {
                    if (candidate.exists() && !candidate.isDirectory()) {
                        context.recordValidationMessage(severity, String.format("Cannot write to directory '%s' specified for property '%s', as ancestor '%s' is not a directory.", directory, propertyName, candidate));
                        return;
                    }
                }
            }
        }
    },
    OUTPUT_DIRECTORIES_VALIDATOR {
        @Override
        public void validate(String propertyName, Object values, TaskValidationContext context, TaskValidationContext.Severity severity) {
            for (File directory : toFiles(context, values)) {
                OUTPUT_DIRECTORY_VALIDATOR.validate(propertyName, directory, context, severity);
            }
        }
    },
    OUTPUT_FILE_VALIDATOR {
        @Override
        public void validate(String propertyName, Object value, TaskValidationContext context, TaskValidationContext.Severity severity) {
            File file = toFile(context, value);
            if (file.exists()) {
                if (file.isDirectory()) {
                    context.recordValidationMessage(severity, String.format("Cannot write to file '%s' specified for property '%s' as it is a directory.", file, propertyName));
                }
                // else, assume we can write to anything that exists and is not a directory
            } else {
                for (File candidate = file.getParentFile(); candidate != null && !candidate.isDirectory(); candidate = candidate.getParentFile()) {
                    if (candidate.exists() && !candidate.isDirectory()) {
                        context.recordValidationMessage(severity, String.format("Cannot write to file '%s' specified for property '%s', as ancestor '%s' is not a directory.", file, propertyName, candidate));
                        break;
                    }
                }
            }
        }
    },
    OUTPUT_FILES_VALIDATOR {
        @Override
        public void validate(String propertyName, Object values, TaskValidationContext context, TaskValidationContext.Severity severity) {
            for (File file : toFiles(context, values)) {
                OUTPUT_FILE_VALIDATOR.validate(propertyName, file, context, severity);
            }
        }
    };

    private static File toDirectory(TaskValidationContext context, Object value) {
        if (value instanceof ConfigurableFileTree) {
            return ((ConfigurableFileTree) value).getDir();
        }
        return toFile(context, value);
    }

    private static File toFile(TaskValidationContext context, Object value) {
        return context.getResolver().resolve(value);
    }

    private static Iterable<? extends File> toFiles(TaskValidationContext context, Object value) {
        if (value instanceof Map) {
            return toFiles(context, ((Map) value).values());
        } else if (value instanceof FileCollection) {
            return ((FileCollection) value).getFiles();
        } else {
            return context.getResolver().resolveFiles(value);
        }
    }
}
