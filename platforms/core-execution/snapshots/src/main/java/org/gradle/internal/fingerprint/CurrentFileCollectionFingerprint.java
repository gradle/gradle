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

import com.google.common.collect.ImmutableMultimap;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.FileSystemSnapshot;

import java.util.Map;

/**
 * A file collection fingerprint taken during this build.
 */
public interface CurrentFileCollectionFingerprint extends FileCollectionFingerprint {
    /**
     * Returns the combined hash of the contents of this {@link CurrentFileCollectionFingerprint}.
     */
    HashCode getHash();

    /**
     * An identifier for the strategy.
     *
     * Used to select a compare strategy.
     */
    String getStrategyIdentifier();

    /**
     * Returns the snapshot used to capture these fingerprints.
     */
    FileSystemSnapshot getSnapshot();

    boolean isEmpty();

    /**
     * Archive the file collection fingerprint.
     *
     * @return a file collection fingerprint which can be archived.
     */
    FileCollectionFingerprint archive(ArchivedFileCollectionFingerprintFactory factory);

    interface ArchivedFileCollectionFingerprintFactory {
        FileCollectionFingerprint createArchivedFileCollectionFingerprint(Map<String, FileSystemLocationFingerprint> fingerprints, ImmutableMultimap<String, HashCode> rootHashes, HashCode strategyConfigurationHash);
    }
}
