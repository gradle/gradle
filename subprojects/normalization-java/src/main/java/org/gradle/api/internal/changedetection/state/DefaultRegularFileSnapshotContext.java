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

package org.gradle.api.internal.changedetection.state;

import org.gradle.internal.fingerprint.hashing.RegularFileSnapshotContext;
import org.gradle.internal.snapshot.RegularFileSnapshot;

import java.util.function.Supplier;

public class DefaultRegularFileSnapshotContext implements RegularFileSnapshotContext {
    private final Supplier<String[]> relativePathSegmentSupplier;
    private final RegularFileSnapshot snapshot;

    public DefaultRegularFileSnapshotContext(Supplier<String[]> relativePathSegmentSupplier, RegularFileSnapshot snapshot) {
        this.relativePathSegmentSupplier = relativePathSegmentSupplier;
        this.snapshot = snapshot;
    }

    @Override
    public Supplier<String[]> getRelativePathSegments() {
        return relativePathSegmentSupplier;
    }

    @Override
    public RegularFileSnapshot getSnapshot() {
        return snapshot;
    }
}
