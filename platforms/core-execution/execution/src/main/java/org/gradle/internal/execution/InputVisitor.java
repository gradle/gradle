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
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.FileNormalizer;
import org.gradle.internal.fingerprint.LineEndingSensitivity;
import org.gradle.internal.properties.InputBehavior;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public interface InputVisitor {
    default void visitInputProperty(
        String propertyName,
        ValueSupplier value
    ) {}

    default void visitInputFileProperty(
        String propertyName,
        InputBehavior behavior,
        InputFileValueSupplier value
    ) {}

    interface ValueSupplier {
        @Nullable
        Object getValue();
    }

    interface FileValueSupplier extends ValueSupplier {
        FileCollection getFiles();
    }

    class InputFileValueSupplier implements FileValueSupplier {
        @Nullable
        private final Object value;
        private final FileNormalizer normalizer;
        private final DirectorySensitivity directorySensitivity;
        private final LineEndingSensitivity lineEndingSensitivity;
        private final Supplier<FileCollection> files;

        public InputFileValueSupplier(
            @Nullable Object value,
            FileNormalizer normalizer,
            DirectorySensitivity directorySensitivity,
            LineEndingSensitivity lineEndingSensitivity,
            Supplier<FileCollection> files
        ) {
            this.value = value;
            this.normalizer = normalizer;
            this.directorySensitivity = directorySensitivity;
            this.lineEndingSensitivity = lineEndingSensitivity;
            this.files = files;
        }

        @Nullable
        @Override
        public Object getValue() {
            return value;
        }

        public FileNormalizer getNormalizer() {
            return normalizer;
        }

        public DirectorySensitivity getDirectorySensitivity() {
            return directorySensitivity;
        }

        public LineEndingSensitivity getLineEndingNormalization() {
            return lineEndingSensitivity;
        }

        @Override
        public FileCollection getFiles() {
            return files.get();
        }
    }
}
