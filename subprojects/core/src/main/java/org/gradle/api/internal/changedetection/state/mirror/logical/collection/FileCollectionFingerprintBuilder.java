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

package org.gradle.api.internal.changedetection.state.mirror.logical.collection;

import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotBuilder;
import org.gradle.api.internal.changedetection.state.VisitingFileCollectionSnapshotBuilder;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalFileSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalMissingSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshot;

import java.util.ArrayList;
import java.util.List;

public class FileCollectionFingerprintBuilder implements FileCollectionSnapshotBuilder, VisitingFileCollectionSnapshotBuilder {

    private final List<PhysicalSnapshot> roots = new ArrayList<PhysicalSnapshot>();
    private final FingerprintingStrategy fingerprintingStrategy;

    public FileCollectionFingerprintBuilder(FingerprintingStrategy fingerprintingStrategy) {
        this.fingerprintingStrategy = fingerprintingStrategy;
    }

    @Override
    public FileCollectionSnapshot build() {
        return new DefaultFileCollectionFingerprint(fingerprintingStrategy.getCompareStrategy(), fingerprintingStrategy.collectSnapshots(roots));
    }

    @Override
    public void visitFileTreeSnapshot(PhysicalSnapshot tree) {
        roots.add(tree);
    }

    @Override
    public void visitDirectorySnapshot(PhysicalSnapshot directory) {
        roots.add(directory);
    }

    @Override
    public void visitFileSnapshot(PhysicalFileSnapshot file) {
        roots.add(file);
    }

    @Override
    public void visitMissingFileSnapshot(PhysicalMissingSnapshot missingFile) {
        if (missingFile != PhysicalMissingSnapshot.INSTANCE) {
            roots.add(missingFile);
        }
    }
}
