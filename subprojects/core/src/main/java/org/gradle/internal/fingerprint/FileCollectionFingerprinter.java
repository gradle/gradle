/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;

public interface FileCollectionFingerprinter {
    /**
     * The type used to refer to this fingerprinter in the {@link FileCollectionFingerprinterRegistry}.
     */
    Class<? extends FileNormalizer> getRegisteredType();

    /**
     * Creates a fingerprint of the contents of the given collection.
     */
    CurrentFileCollectionFingerprint fingerprint(FileCollection files);

    /**
     * Creates a fingerprint of the contents of the given roots.
     */
    CurrentFileCollectionFingerprint fingerprint(Iterable<? extends FileSystemSnapshot> roots);

    /**
     * Returns an empty fingerprint.
     */
    CurrentFileCollectionFingerprint empty();

    /**
     * Returns the normalized path to use for the given root
     */
    String normalizePath(CompleteFileSystemLocationSnapshot root);
}
