/*
 * Copyright 2020 the original author or authors.
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

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.snapshot.FileSystemSnapshot;

import java.io.File;

public interface OutputSnapshotter {
    /**
     * Takes a snapshot of the outputs of a work.
     */
    ImmutableSortedMap<String, FileSystemSnapshot> snapshotOutputs(UnitOfWork work, File workspace)
        throws OutputFileSnapshottingException;

    class OutputFileSnapshottingException extends RuntimeException {
        private final String propertyName;

        public OutputFileSnapshottingException(String propertyName, Throwable cause) {
            this(String.format("Cannot snapshot output property '%s'.", propertyName), cause, propertyName);
        }

        private OutputFileSnapshottingException(String formattedMessage, Throwable cause, String propertyName) {
            super(formattedMessage, cause);
            this.propertyName = propertyName;
        }

        public String getPropertyName() {
            return propertyName;
        }
    }
}
