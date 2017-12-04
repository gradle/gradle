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

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.FileTreeInternal;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Map;

public class DefaultPropertySpecFactory implements PropertySpecFactory {

    private final TaskInternal task;
    private final FileResolver resolver;

    public DefaultPropertySpecFactory(TaskInternal task, FileResolver resolver) {
        this.task = task;
        this.resolver = resolver;
    }

    @Override
    public DeclaredTaskInputFileProperty createInputFileSpec(ValidatingValue paths, ValidationAction validationAction) {
        return new DefaultTaskInputFilePropertySpec(task.getName(), resolver, paths, validationAction);
    }

    @Override
    public DeclaredTaskInputFileProperty createInputDirSpec(ValidatingValue dirPath, ValidationAction validator) {
        FileTreeInternal fileTree = resolver.resolveFilesAsTree(dirPath);
        return createInputFileSpec(new FileTreeValue(dirPath, fileTree), validator);
    }

    @Override
    public DefaultTaskInputPropertySpec createInputPropertySpec(String name, ValidatingValue value) {
        return new DefaultTaskInputPropertySpec(task.getInputs(), name, value);
    }

    @Override
    public DeclaredTaskOutputFileProperty createOutputFileSpec(ValidatingValue path) {
        return createOutputFilePropertySpec(path, OutputType.FILE, OUTPUT_FILE_VALIDATOR);
    }

    @Override
    public DeclaredTaskOutputFileProperty createOutputDirSpec(ValidatingValue path) {
        return createOutputFilePropertySpec(path, OutputType.DIRECTORY, OUTPUT_DIRECTORY_VALIDATOR);
    }

    @Override
    public DeclaredTaskOutputFileProperty createOutputFilesSpec(ValidatingValue paths) {
        return new CompositeTaskOutputPropertySpec(task.getName(), resolver, OutputType.FILE, paths, OUTPUT_FILES_VALIDATOR);
    }

    @Override
    public DeclaredTaskOutputFileProperty createOutputDirsSpec(ValidatingValue paths) {
        return new CompositeTaskOutputPropertySpec(task.getName(), resolver, OutputType.DIRECTORY, paths, OUTPUT_DIRECTORIES_VALIDATOR);
    }

    private DefaultCacheableTaskOutputFilePropertySpec createOutputFilePropertySpec(ValidatingValue path, OutputType file, ValidationAction outputFileValidator) {
        return new DefaultCacheableTaskOutputFilePropertySpec(task.getName(), resolver, file, path, outputFileValidator);
    }

    private static class FileTreeValue implements ValidatingValue {
        private final ValidatingValue delegate;
        private final FileTreeInternal fileTree;

        public FileTreeValue(ValidatingValue delegate, FileTreeInternal fileTree) {
            this.delegate = delegate;
            this.fileTree = fileTree;
        }

        @Nullable
        @Override
        public Object call() {
            return fileTree;
        }

        @Override
        public void validate(String propertyName, boolean optional, ValidationAction valueValidator, TaskValidationContext context) {
            delegate.validate(propertyName, optional, valueValidator, context);
        }
    }

    private static final ValidationAction OUTPUT_DIRECTORY_VALIDATOR = new ValidationAction() {
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
    };

    private static final ValidationAction OUTPUT_FILE_VALIDATOR = new ValidationAction() {
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
    };

    private static final ValidationAction OUTPUT_FILES_VALIDATOR = new ValidationAction() {
        @Override
        public void validate(String propertyName, Object values, TaskValidationContext context, TaskValidationContext.Severity severity) {
            for (File file : toFiles(context, values)) {
                OUTPUT_FILE_VALIDATOR.validate(propertyName, file, context, severity);
            }
        }
    };

    private static File toFile(TaskValidationContext context, Object value) {
        if (value instanceof File) {
            return (File) value;
        }
        return context.getResolver().resolve(value);
    }

    private static final ValidationAction OUTPUT_DIRECTORIES_VALIDATOR = new ValidationAction() {
        @Override
        public void validate(String propertyName, Object values, TaskValidationContext context, TaskValidationContext.Severity severity) {
            for (File directory : toFiles(context, values)) {
                OUTPUT_DIRECTORY_VALIDATOR.validate(propertyName, directory, context, severity);
            }
        }
    };

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
