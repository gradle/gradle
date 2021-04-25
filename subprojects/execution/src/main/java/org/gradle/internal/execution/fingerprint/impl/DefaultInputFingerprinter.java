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
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.execution.fingerprint.FileCollectionFingerprinterRegistry;
import org.gradle.internal.execution.fingerprint.FileNormalizationSpec;
import org.gradle.internal.execution.fingerprint.InputFingerprinter;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
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
        ImmutableSortedMap<String, ValueSnapshot> knownValueSnapshots,
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> knownFingerprints,
        Consumer<InputVisitor> inputs
    ) {
        InputCollectingVisitor visitor = new InputCollectingVisitor(previousValueSnapshots, fingerprinterRegistry, valueSnapshotter, knownValueSnapshots, knownFingerprints);
        inputs.accept(visitor);
        return visitor.complete();
    }

    @Override
    public FileCollectionFingerprinterRegistry getFingerprinterRegistry() {
        return fingerprinterRegistry;
    }

    private static class InputCollectingVisitor implements InputVisitor {
        private final ImmutableSortedMap<String, ValueSnapshot> previousValueSnapshots;
        private final FileCollectionFingerprinterRegistry fingerprinterRegistry;
        private final ValueSnapshotter valueSnapshotter;
        private final ImmutableSortedMap<String, ValueSnapshot> knownValueSnapshots;
        private final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> knownFingerprints;

        private final ImmutableSortedMap.Builder<String, ValueSnapshot> valueSnapshotsBuilder = ImmutableSortedMap.naturalOrder();
        private final ImmutableSortedMap.Builder<String, CurrentFileCollectionFingerprint> fingerprintsBuilder = ImmutableSortedMap.naturalOrder();

        public InputCollectingVisitor(
            ImmutableSortedMap<String, ValueSnapshot> previousValueSnapshots,
            FileCollectionFingerprinterRegistry fingerprinterRegistry,
            ValueSnapshotter valueSnapshotter,
            ImmutableSortedMap<String, ValueSnapshot> knownValueSnapshots,
            ImmutableSortedMap<String, CurrentFileCollectionFingerprint> knownFingerprints
        ) {
            this.previousValueSnapshots = previousValueSnapshots;
            this.fingerprinterRegistry = fingerprinterRegistry;
            this.valueSnapshotter = valueSnapshotter;
            this.knownValueSnapshots = knownValueSnapshots;
            this.knownFingerprints = knownFingerprints;
        }

        @Override
        public void visitInputProperty(String propertyName, ValueSupplier value) {
            if (knownValueSnapshots.containsKey(propertyName)) {
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
            if (knownFingerprints.containsKey(propertyName)) {
                return;
            }

            FileNormalizationSpec normalizationSpec = DefaultFileNormalizationSpec.from(value.getNormalizer(), value.getDirectorySensitivity());
            CurrentFileCollectionFingerprint fingerprint = fingerprinterRegistry.getFingerprinter(normalizationSpec)
                .fingerprint(value.getFiles());
            fingerprintsBuilder.put(propertyName, fingerprint);
        }

        public Result complete() {
            return new InputFingerprints(valueSnapshotsBuilder.build(), fingerprintsBuilder.build());
        }
    }

    @VisibleForTesting
    public static class InputFingerprints implements InputFingerprinter.Result {
        private final ImmutableSortedMap<String, ValueSnapshot> valueSnapshots;
        private final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> fileFingerprints;

        public InputFingerprints(ImmutableSortedMap<String, ValueSnapshot> valueSnapshots, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> fileFingerprints) {
            this.valueSnapshots = valueSnapshots;
            this.fileFingerprints = fileFingerprints;
        }

        public ImmutableSortedMap<String, ValueSnapshot> getValueSnapshots() {
            return valueSnapshots;
        }

        public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getFileFingerprints() {
            return fileFingerprints;
        }
    }
}
