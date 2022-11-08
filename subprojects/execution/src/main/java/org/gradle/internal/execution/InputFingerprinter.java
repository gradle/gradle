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

package org.gradle.internal.execution;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.execution.UnitOfWork.InputVisitor;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.snapshot.ValueSnapshot;

import java.util.function.Consumer;

public interface InputFingerprinter {
    Result fingerprintInputProperties(
        ImmutableSortedMap<String, ValueSnapshot> previousValueSnapshots,
        ImmutableSortedMap<String, ? extends FileCollectionFingerprint> previousFingerprints,
        ImmutableSortedMap<String, ValueSnapshot> knownCurrentValueSnapshots,
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> knownCurrentFingerprints,
        Consumer<InputVisitor> inputs
    ) throws InputFingerprintingException, InputFileFingerprintingException;

    interface Result {
        /**
         * Returns the values snapshotted just now.
         */
        ImmutableSortedMap<String, ValueSnapshot> getValueSnapshots();

        /**
         * Returns all the value snapshots, including previously known ones.
         */
        ImmutableSortedMap<String, ValueSnapshot> getAllValueSnapshots();

        /**
         * Returns the files fingerprinted just now.
         */
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getFileFingerprints();

        /**
         * Returns all the file fingerprints, including the previously known ones.
         */
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getAllFileFingerprints();

        /**
         * Returns the file property names which need an isEmpty() check when used with {@link org.gradle.api.tasks.SkipWhenEmpty}.
         *
         * Archive file trees backed by a file need the isEmpty() check, since the fingerprint will be the backing file.
         */
        ImmutableSet<String> getPropertiesRequiringIsEmptyCheck();
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
            super(String.format("Cannot fingerprint input file property '%s': %s", propertyName, cause.getMessage()), cause);
            this.propertyName = propertyName;
        }

        public String getPropertyName() {
            return propertyName;
        }
    }
}
