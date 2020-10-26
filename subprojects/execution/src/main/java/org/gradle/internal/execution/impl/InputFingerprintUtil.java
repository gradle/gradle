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

package org.gradle.internal.execution.impl;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshotter;

import javax.annotation.Nullable;
import java.util.function.Supplier;

import static org.gradle.internal.execution.UnitOfWork.InputPropertyType.NON_INCREMENTAL;

public class InputFingerprintUtil {

    public static void fingerprintInputProperties(
        UnitOfWork work,
        ImmutableSortedMap<String, ValueSnapshot> previousValueSnapshots,
        ValueSnapshotter valueSnapshotter,
        ImmutableSortedMap<String, ValueSnapshot> knownValueSnapshots,
        ImmutableSortedMap.Builder<String, ValueSnapshot> valueSnapshotsBuilder,
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> knownFingerprints,
        ImmutableSortedMap.Builder<String, CurrentFileCollectionFingerprint> fingerprintsBuilder,
        InputPropertyPredicate filter
    ) {
        valueSnapshotsBuilder.putAll(knownValueSnapshots);
        fingerprintsBuilder.putAll(knownFingerprints);
        work.visitInputs(new UnitOfWork.InputVisitor() {
            @Override
            public void visitInputProperty(String propertyName, UnitOfWork.IdentityKind identity, UnitOfWork.ValueSupplier value) {
                if (knownValueSnapshots.containsKey(propertyName)) {
                    return;
                }
                if (!filter.include(propertyName, NON_INCREMENTAL, identity)) {
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
                    throw new UncheckedIOException(String.format("Unable to store input properties for %s. Property '%s' with value '%s' cannot be serialized.",
                        work.getDisplayName(), propertyName, value.getValue()), e);
                }
            }

            @Override
            public void visitInputFileProperty(String propertyName, UnitOfWork.InputPropertyType type, UnitOfWork.IdentityKind identity, @Nullable Object value, Supplier<CurrentFileCollectionFingerprint> fingerprinter) {
                if (knownFingerprints.containsKey(propertyName)) {
                    return;
                }
                if (!filter.include(propertyName, type, identity)) {
                    return;
                }
                fingerprintsBuilder.put(propertyName, fingerprinter.get());
            }
        });
    }

    public interface InputPropertyPredicate {
        boolean include(String propertyName, UnitOfWork.InputPropertyType type, UnitOfWork.IdentityKind identity);
    }
}
