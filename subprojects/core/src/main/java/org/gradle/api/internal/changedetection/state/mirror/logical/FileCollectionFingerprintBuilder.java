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

package org.gradle.api.internal.changedetection.state.mirror.logical;

import org.gradle.api.internal.changedetection.state.EmptyFileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotBuilder;
import org.gradle.api.internal.changedetection.state.NormalizedFileSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FileCollectionFingerprintBuilder implements FileCollectionSnapshotBuilder {

    private final List<PhysicalSnapshot> roots = new ArrayList<PhysicalSnapshot>();
    private final FingerprintingStrategy fingerprintingStrategy;

    public FileCollectionFingerprintBuilder(FingerprintingStrategy fingerprintingStrategy) {
        this.fingerprintingStrategy = fingerprintingStrategy;
    }

    @Override
    public FileCollectionSnapshot build() {
        if (roots.isEmpty()) {
            return EmptyFileCollectionSnapshot.INSTANCE;
        }
        Map<String, NormalizedFileSnapshot> snapshots = fingerprintingStrategy.collectSnapshots(roots);
        if (snapshots.isEmpty()) {
            return EmptyFileCollectionSnapshot.INSTANCE;
        }

        return new DefaultFileCollectionFingerprint(fingerprintingStrategy.getCompareStrategy(), snapshots);
    }

    @Override
    public void collectRoot(PhysicalSnapshot root) {
        roots.add(root);
    }
}
