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

package org.gradle.internal.snapshot.impl;

import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprinter;
import org.gradle.internal.fingerprint.FileCollectionFingerprinterRegistry;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.Snapshot;
import org.gradle.internal.snapshot.SnapshottingService;

import javax.inject.Inject;
import java.nio.file.Path;

public class DefaultSnapshottingService implements SnapshottingService {

    private final FileCollectionFingerprinterRegistry fingerprinterRegistry;
    private final FileCollectionFactory fileCollectionFactory;

    @Inject
    public DefaultSnapshottingService(FileCollectionFingerprinterRegistry fingerprinterRegistry, FileCollectionFactory fileCollectionFactory) {
        this.fingerprinterRegistry = fingerprinterRegistry;
        this.fileCollectionFactory = fileCollectionFactory;
    }

    @Override
    public Snapshot snapshotFor(Path filePath, Class<? extends FileNormalizer> normalizationType) {
        FileCollectionFingerprinter fingerprinter = fingerprinterRegistry.getFingerprinter(normalizationType);
        FileCollectionInternal fileCollection = fileCollectionFactory.fixed(filePath.toFile());
        CurrentFileCollectionFingerprint fingerprint = fingerprinter.fingerprint(fileCollection);
        return new DefaultSnapshot(fingerprint.getHash());
    }

    private static class DefaultSnapshot implements Snapshot {

        private final HashCode hashCode;

        public DefaultSnapshot(HashCode hashCode) {
            this.hashCode = hashCode;
        }

        @Override
        public String getHashValue() {
            return hashCode.toString();
        }
    }
}
