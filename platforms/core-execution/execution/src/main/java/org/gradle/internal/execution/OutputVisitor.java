/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.execution;

import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Destroys;
import org.gradle.api.tasks.LocalState;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.internal.file.TreeType;

import java.io.File;
import java.util.function.Supplier;

public interface OutputVisitor {

    /**
     * Visits the output property for work, for example for task output properties are annotated with {@link OutputDirectory} or {@link OutputFile} and similar.
     */
    default void visitOutputProperty(
        String propertyName,
        TreeType type,
        OutputFileValueSupplier value
    ) {}

    /**
     * Visits the local state location, for example for task local state properties are annotated with {@link LocalState}.
     * Other work types don't have local state.
     */
    default void visitLocalState(File localStateRoot) {}

    /**
     * Visits the destroyable for work, for example for task destroyable properties are annotated with {@link Destroys}.
     * Other work types don't have destroyable.
     */
    default void visitDestroyable(File destroyableRoot) {}

    abstract class OutputFileValueSupplier implements InputVisitor.FileValueSupplier {
        private final FileCollection files;

        public OutputFileValueSupplier(FileCollection files) {
            this.files = files;
        }

        public static OutputFileValueSupplier fromStatic(File root, FileCollection fileCollection) {
            return new OutputFileValueSupplier(fileCollection) {
                @Override
                public File getValue() {
                    return root;
                }
            };
        }

        public static OutputFileValueSupplier fromSupplier(Supplier<File> root, FileCollection fileCollection) {
            return new OutputFileValueSupplier(fileCollection) {
                @Override
                public File getValue() {
                    return root.get();
                }
            };
        }

        @Override
        abstract public File getValue();

        @Override
        public FileCollection getFiles() {
            return files;
        }
    }
}
