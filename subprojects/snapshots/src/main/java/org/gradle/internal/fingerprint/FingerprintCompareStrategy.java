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

import org.gradle.internal.change.ChangeVisitor;
import org.gradle.internal.hash.Hasher;

import java.util.Collection;
import java.util.Map;

/**
 * Strategy to compare two {@link FileCollectionFingerprint}s.
 *
 * The strategy first tries to do a trivial comparison and delegates the more complex cases to a separate implementation.
 */
public interface FingerprintCompareStrategy {
    /**
     * @see FileCollectionFingerprint#visitChangesSince(FileCollectionFingerprint, String, boolean, ChangeVisitor)
     */
    boolean visitChangesSince(ChangeVisitor visitor, Map<String, FileSystemLocationFingerprint> current, Map<String, FileSystemLocationFingerprint> previous, String propertyTitle, boolean includeAdded);

    void appendToHasher(Hasher hasher, Collection<FileSystemLocationFingerprint> fingerprints);
}
