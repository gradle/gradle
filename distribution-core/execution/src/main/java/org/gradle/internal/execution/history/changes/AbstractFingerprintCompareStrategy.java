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
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;

public abstract class AbstractFingerprintCompareStrategy extends CompareStrategy<FileCollectionFingerprint, FileSystemLocationFingerprint> implements FingerprintCompareStrategy {

    protected static final ChangeFactory<FileSystemLocationFingerprint> FINGERPRINT_CHANGE_FACTORY = new ChangeFactory<FileSystemLocationFingerprint>() {
        @Override
        public Change added(String path, String propertyTitle, FileSystemLocationFingerprint current) {
            return DefaultFileChange.added(path, propertyTitle, current.getType(), current.getNormalizedPath());
        }

        @Override
        public Change removed(String path, String propertyTitle, FileSystemLocationFingerprint previous) {
            return DefaultFileChange.removed(path, propertyTitle, previous.getType(), previous.getNormalizedPath());
        }

        @Override
        public Change modified(String path, String propertyTitle, FileSystemLocationFingerprint previous, FileSystemLocationFingerprint current) {
            return DefaultFileChange.modified(path, propertyTitle, previous.getType(), current.getType(), current.getNormalizedPath());
        }
    };

    private static final TrivialChangeDetector.ItemComparator<FileSystemLocationFingerprint> ITEM_COMPARATOR = new TrivialChangeDetector.ItemComparator<FileSystemLocationFingerprint>() {
        @Override
        public boolean hasSamePath(FileSystemLocationFingerprint previous, FileSystemLocationFingerprint current) {
            return previous.getNormalizedPath().equals(current.getNormalizedPath());
        }

        @Override
        public boolean hasSameContent(FileSystemLocationFingerprint previous, FileSystemLocationFingerprint current) {
            return previous.getNormalizedContentHash().equals(current.getNormalizedContentHash());
        }
    };

    public AbstractFingerprintCompareStrategy(ChangeDetector<FileSystemLocationFingerprint> changeDetector) {
        super(
            FileCollectionFingerprint::getFingerprints,
            FileCollectionFingerprint::getRootHashes,
            new TrivialChangeDetector<>(ITEM_COMPARATOR, FINGERPRINT_CHANGE_FACTORY, changeDetector)
        );
    }
}
