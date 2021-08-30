/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.execution.fingerprint;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.LineEndingSensitivity;
import org.gradle.internal.snapshot.ValueSnapshot;

import javax.annotation.Nullable;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface InputFingerprinter {
    Result fingerprintInputProperties(
        ImmutableSortedMap<String, ValueSnapshot> previousValueSnapshots,
        ImmutableSortedMap<String, ? extends FileCollectionFingerprint> previousFingerprints,
        ImmutableSortedMap<String, ValueSnapshot> knownCurrentValueSnapshots,
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> knownCurrentFingerprints,
        Consumer<InputVisitor> inputs
    ) throws InputFingerprintingException, InputFileFingerprintingException;

    /**
     * Hack require to get normalized input path without fingerprinting contents.
     */
    FileCollectionFingerprinterRegistry getFingerprinterRegistry();

    interface InputVisitor {
        default void visitInputProperty(
            String propertyName,
            ValueSupplier value
        ) {}

        default void visitInputFileProperty(
            String propertyName,
            InputPropertyType type,
            FileValueSupplier value
        ) {}

    }

    enum InputPropertyType {
        /**
         * Non-incremental inputs.
         */
        NON_INCREMENTAL(false, false),

        /**
         * Incremental inputs.
         */
        INCREMENTAL(true, false),

        /**
         * These are the primary inputs to the incremental work item;
         * if they are empty the work item shouldn't be executed.
         */
        PRIMARY(true, true);

        private final boolean incremental;
        private final boolean skipWhenEmpty;

        InputPropertyType(boolean incremental, boolean skipWhenEmpty) {
            this.incremental = incremental;
            this.skipWhenEmpty = skipWhenEmpty;
        }

        public boolean isIncremental() {
            return incremental;
        }

        public boolean isSkipWhenEmpty() {
            return skipWhenEmpty;
        }
    }

    interface ValueSupplier {
        @Nullable
        Object getValue();
    }

    class FileValueSupplier implements ValueSupplier {
        private final Object value;
        private final Class<? extends FileNormalizer> normalizer;
        private final DirectorySensitivity directorySensitivity;
        private final LineEndingSensitivity lineEndingSensitivity;
        private final Supplier<FileCollection> files;

        public FileValueSupplier(
            @Nullable Object value,
            Class<? extends FileNormalizer> normalizer,
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

        public Class<? extends FileNormalizer> getNormalizer() {
            return normalizer;
        }

        public DirectorySensitivity getDirectorySensitivity() {
            return directorySensitivity;
        }

        public LineEndingSensitivity getLineEndingNormalization() {
            return lineEndingSensitivity;
        }

        public FileCollection getFiles() {
            return files.get();
        }
    }

    interface Result {
        ImmutableSortedMap<String, ValueSnapshot> getValueSnapshots();
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getFileFingerprints();
    }

    class InputFingerprintingException extends RuntimeException {
        private final String propertyName;

        public InputFingerprintingException(String propertyName, String message, Throwable cause) {
            super(String.format("Cannot fingerprint input property '%s': %s.", propertyName, message), cause);
            this.propertyName = propertyName;
        }

        public String getPropertyName() {
            return propertyName;
        }
    }

    class InputFileFingerprintingException extends RuntimeException {
        private final String propertyName;

        public InputFileFingerprintingException(String propertyName, Throwable cause) {
            super(String.format("Cannot fingerprint input file property '%s'.", propertyName), cause);
            this.propertyName = propertyName;
        }

        private InputFileFingerprintingException(String formattedMessage, Throwable cause, String propertyName) {
            super(formattedMessage, cause);
            this.propertyName = propertyName;
        }

        public String getPropertyName() {
            return propertyName;
        }
    }
}
