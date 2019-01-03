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

import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.internal.file.TreeType;

import javax.annotation.Nullable;

public class DefaultPropertySpecFactory implements PropertySpecFactory {

    private final TaskInternal task;
    private final FileResolver resolver;

    public DefaultPropertySpecFactory(TaskInternal task, FileResolver resolver) {
        this.task = task;
        this.resolver = resolver;
    }

    @Override
    public DeclaredTaskInputFileProperty createInputFileSpec(ValidatingValue paths) {
        return createInputFilesSpec(paths, ValidationActions.INPUT_FILE_VALIDATOR);
    }

    @Override
    public DeclaredTaskInputFileProperty createInputFilesSpec(ValidatingValue paths) {
        return createInputFilesSpec(paths, ValidationActions.NO_OP);
    }

    @Override
    public DeclaredTaskInputFileProperty createInputDirSpec(ValidatingValue dirPath) {
        FileTreeInternal fileTree = resolver.resolveFilesAsTree(dirPath);
        return createInputFilesSpec(new FileTreeValue(dirPath, fileTree), ValidationActions.INPUT_DIRECTORY_VALIDATOR);
    }

    private DeclaredTaskInputFileProperty createInputFilesSpec(ValidatingValue paths, ValidationAction validationAction) {
        return new DefaultTaskInputFilePropertySpec(task.toString(), resolver, paths, validationAction);
    }

    @Override
    public DefaultTaskInputPropertySpec createInputPropertySpec(String name, ValidatingValue value) {
        return new DefaultTaskInputPropertySpec(name, value);
    }

    @Override
    public DeclaredTaskOutputFileProperty createOutputFileSpec(ValidatingValue path) {
        return createOutputFilePropertySpec(path, TreeType.FILE, ValidationActions.OUTPUT_FILE_VALIDATOR);
    }

    @Override
    public DeclaredTaskOutputFileProperty createOutputDirSpec(ValidatingValue path) {
        return createOutputFilePropertySpec(path, TreeType.DIRECTORY, ValidationActions.OUTPUT_DIRECTORY_VALIDATOR);
    }

    @Override
    public DeclaredTaskOutputFileProperty createOutputFilesSpec(ValidatingValue paths) {
        return new CompositeTaskOutputPropertySpec(task.toString(), resolver, TreeType.FILE, paths, ValidationActions.OUTPUT_FILES_VALIDATOR);
    }

    @Override
    public DeclaredTaskOutputFileProperty createOutputDirsSpec(ValidatingValue paths) {
        return new CompositeTaskOutputPropertySpec(task.toString(), resolver, TreeType.DIRECTORY, paths, ValidationActions.OUTPUT_DIRECTORIES_VALIDATOR);
    }

    private DefaultCacheableTaskOutputFilePropertySpec createOutputFilePropertySpec(ValidatingValue path, TreeType type, ValidationAction outputFileValidator) {
        return new DefaultCacheableTaskOutputFilePropertySpec(task.toString(), resolver, type, path, outputFileValidator);
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
        public void attachProducer(Task producer) {
            delegate.attachProducer(producer);
        }

        @Override
        public void maybeFinalizeValue() {
            delegate.maybeFinalizeValue();
        }

        @Override
        public void validate(String propertyName, boolean optional, ValidationAction valueValidator, TaskValidationContext context) {
            delegate.validate(propertyName, optional, valueValidator, context);
        }
    }
}
