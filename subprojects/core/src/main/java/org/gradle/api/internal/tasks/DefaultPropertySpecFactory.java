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

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.FileTreeInternal;

import javax.annotation.Nullable;

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
        return createOutputFilePropertySpec(path, OutputType.FILE, ValidationActions.OUTPUT_FILE_VALIDATOR);
    }

    @Override
    public DeclaredTaskOutputFileProperty createOutputDirSpec(ValidatingValue path) {
        return createOutputFilePropertySpec(path, OutputType.DIRECTORY, ValidationActions.OUTPUT_DIRECTORY_VALIDATOR);
    }

    @Override
    public DeclaredTaskOutputFileProperty createOutputFilesSpec(ValidatingValue paths) {
        return new CompositeTaskOutputPropertySpec(task.getName(), resolver, OutputType.FILE, paths, ValidationActions.OUTPUT_FILES_VALIDATOR);
    }

    @Override
    public DeclaredTaskOutputFileProperty createOutputDirsSpec(ValidatingValue paths) {
        return new CompositeTaskOutputPropertySpec(task.getName(), resolver, OutputType.DIRECTORY, paths, ValidationActions.OUTPUT_DIRECTORIES_VALIDATOR);
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
}
