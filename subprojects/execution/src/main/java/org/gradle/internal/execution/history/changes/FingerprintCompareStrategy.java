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

package org.gradle.internal.execution.history.changes;

import org.gradle.internal.fingerprint.FileCollectionFingerprint;

/**
 * Strategy to compare two {@link FileCollectionFingerprint}s.
 *
 * The strategy first tries to do a trivial comparison and delegates the more complex cases to a separate implementation.
 */
public interface FingerprintCompareStrategy {
    /**
     * Visits the changes to file contents since the given fingerprint, subject to the given filters.
     *
     * @return Whether the {@link ChangeVisitor} is looking for further changes. See {@link ChangeVisitor#visitChange(Change)}.
     */
    boolean visitChangesSince(FileCollectionFingerprint current, FileCollectionFingerprint previous, String propertyTitle, ChangeVisitor visitor);
}
