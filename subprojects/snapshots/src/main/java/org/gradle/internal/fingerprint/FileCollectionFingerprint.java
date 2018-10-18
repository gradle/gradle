/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.fingerprint;

import com.google.common.collect.Multimap;
import org.gradle.internal.changes.TaskStateChange;
import org.gradle.internal.changes.TaskStateChangeVisitor;
import org.gradle.internal.hash.HashCode;

import java.util.Map;

/**
 * An immutable snapshot of some aspects of the contents and meta-data of a collection of files or directories.
 */
public interface FileCollectionFingerprint {

    /**
     * Visits the changes to file contents since the given fingerprint, subject to the given filters.
     *
     * @return Whether the {@link TaskStateChangeVisitor} is looking for further changes. See {@link TaskStateChangeVisitor#visitChange(TaskStateChange)}.
     */
    boolean visitChangesSince(FileCollectionFingerprint oldFingerprint, String title, boolean includeAdded, TaskStateChangeVisitor visitor);

    /**
     * The underlying fingerprints.
     */
    Map<String, FileSystemLocationFingerprint> getFingerprints();

    /**
     * The Merkle hashes of the roots which make up this file collection fingerprint.
     */
    Multimap<String, HashCode> getRootHashes();
}
