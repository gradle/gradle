/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.execution.fingerprint.impl;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.execution.fingerprint.FileCollectionFingerprinterRegistry;
import org.gradle.internal.execution.fingerprint.FileNormalizationSpec;
import org.gradle.internal.execution.fingerprint.InputFingerprinter;
import org.gradle.internal.fingerprint.ContentTracking;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshotter;

import java.util.function.Consumer;

public class DefaultInputFingerprinter implements InputFingerprinter {

    private final FileCollectionFingerprinterRegistry fingerprinterRegistry;
    private final ValueSnapshotter valueSnapshotter;

    public DefaultInputFingerprinter(
        FileCollectionFingerprinterRegistry fingerprinterRegistry,
        ValueSnapshotter valueSnapshotter
    ) {
        this.fingerprinterRegistry = fingerprinterRegistry;
        this.valueSnapshotter = valueSnapshotter;
    }

    @Override
    public Result fingerprintInputProperties(
        ImmutableSortedMap<String, ValueSnapshot> previousValueSnapshots,
        ImmutableSortedMap<String, ? extends FileCollectionFingerprint> previousFingerprints,
        ImmutableSortedMap<String, ValueSnapshot> knownCurrentValueSnapshots,
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> knownCurrentFingerprints,
        Consumer<InputVisitor> inputs
    ) {
        InputCollectingVisitor visitor = new InputCollectingVisitor(previousValueSnapshots, previousFingerprints, fingerprinterRegistry, valueSnapshotter, knownCurrentValueSnapshots, knownCurrentFingerprints);
        inputs.accept(visitor);
        return visitor.complete();
    }

    @Override
    public FileCollectionFingerprinterRegistry getFingerprinterRegistry() {
        return fingerprinterRegistry;
    }

    private static class InputCollectingVisitor implements InputVisitor {
        private final ImmutableSortedMap<String, ValueSnapshot> previousValueSnapshots;
        private final ImmutableSortedMap<String, ? extends FileCollectionFingerprint> previousFingerprints;
        private final FileCollectionFingerprinterRegistry fingerprinterRegistry;
        private final ValueSnapshotter valueSnapshotter;
        private final ImmutableSortedMap<String, ValueSnapshot> knownCurrentValueSnapshots;
        private final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> knownCurrentFingerprints;

        private final ImmutableSortedMap.Builder<String, ValueSnapshot> valueSnapshotsBuilder = ImmutableSortedMap.naturalOrder();
        private final ImmutableSortedMap.Builder<String, CurrentFileCollectionFingerprint> fingerprintsBuilder = ImmutableSortedMap.naturalOrder();
        private final ImmutableSortedSet.Builder<String> untrackedPropertiesBuilder = ImmutableSortedSet.naturalOrder();

        public InputCollectingVisitor(
            ImmutableSortedMap<String, ValueSnapshot> previousValueSnapshots,
            ImmutableSortedMap<String, ? extends FileCollectionFingerprint> previousFingerprints,
            FileCollectionFingerprinterRegistry fingerprinterRegistry,
            ValueSnapshotter valueSnapshotter,
            ImmutableSortedMap<String, ValueSnapshot> knownCurrentValueSnapshots,
            ImmutableSortedMap<String, CurrentFileCollectionFingerprint> knownCurrentFingerprints
        ) {
            this.previousValueSnapshots = previousValueSnapshots;
            this.previousFingerprints = previousFingerprints;
            this.fingerprinterRegistry = fingerprinterRegistry;
            this.valueSnapshotter = valueSnapshotter;
            this.knownCurrentValueSnapshots = knownCurrentValueSnapshots;
            this.knownCurrentFingerprints = knownCurrentFingerprints;
        }

        @Override
        public void visitInputProperty(String propertyName, ValueSupplier value) {
            if (knownCurrentValueSnapshots.containsKey(propertyName)) {
                return;
            }
            Object actualValue = value.getValue();
            try {
                ValueSnapshot previousSnapshot = previousValueSnapshots.get(propertyName);
                if (previousSnapshot == null) {
                    valueSnapshotsBuilder.put(propertyName, valueSnapshotter.snapshot(actualValue));
                } else {
                    valueSnapshotsBuilder.put(propertyName, valueSnapshotter.snapshot(actualValue, previousSnapshot));
                }
            } catch (Exception e) {
                throw new UncheckedIOException(String.format("Input property '%s' with value '%s' cannot be serialized.",
                    propertyName, value.getValue()), e);
            }
        }

        @Override
        public void visitInputFileProperty(String propertyName, InputPropertyType type, FileValueSupplier value) {
            if (knownCurrentFingerprints.containsKey(propertyName)) {
                return;
            }
            if (value.getContentTracking() == ContentTracking.UNTRACKED) {
                untrackedPropertiesBuilder.add(propertyName);
                return;
            }

            FileCollectionFingerprint previousFingerprint = previousFingerprints.get(propertyName);
            FileNormalizationSpec normalizationSpec = DefaultFileNormalizationSpec.from(value.getNormalizer(), value.getDirectorySensitivity(), value.getLineEndingNormalization());
            CurrentFileCollectionFingerprint fingerprint = fingerprinterRegistry.getFingerprinter(normalizationSpec)
                .fingerprint(value.getFiles(), previousFingerprint);
            fingerprintsBuilder.put(propertyName, fingerprint);
        }

        public Result complete() {
            return new InputFingerprints(valueSnapshotsBuilder.build(), fingerprintsBuilder.build(), untrackedPropertiesBuilder.build());
        }
    }

    @VisibleForTesting
    public static class InputFingerprints implements InputFingerprinter.Result {
        private final ImmutableSortedMap<String, ValueSnapshot> valueSnapshots;
        private final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> fileFingerprints;
        private final ImmutableSortedSet<String> untrackedProperties;

        public InputFingerprints(ImmutableSortedMap<String, ValueSnapshot> valueSnapshots, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> fileFingerprints, ImmutableSortedSet<String> untrackedProperties) {
            this.valueSnapshots = valueSnapshots;
            this.fileFingerprints = fileFingerprints;
            this.untrackedProperties = untrackedProperties;
        }

        public ImmutableSortedMap<String, ValueSnapshot> getValueSnapshots() {
            return valueSnapshots;
        }

        public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getFileFingerprints() {
            return fileFingerprints;
        }

        @Override
        public ImmutableSortedSet<String> getUntrackedProperties() {
            return untrackedProperties;
        }
    }
}
