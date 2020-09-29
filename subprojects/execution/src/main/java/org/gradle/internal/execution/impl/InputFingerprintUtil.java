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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshotter;

public class InputFingerprintUtil {

    public static <B extends ImmutableMap.Builder<String, ValueSnapshot>> B fingerprintInputProperties(UnitOfWork work, ImmutableSortedMap<String, ValueSnapshot> previousSnapshots, ValueSnapshotter valueSnapshotter, B builder) {
        work.visitInputProperties((propertyName, value) -> {
            try {
                ValueSnapshot previousSnapshot = previousSnapshots.get(propertyName);
                if (previousSnapshot == null) {
                    builder.put(propertyName, valueSnapshotter.snapshot(value));
                } else {
                    builder.put(propertyName, valueSnapshotter.snapshot(value, previousSnapshot));
                }
            } catch (Exception e) {
                throw new UncheckedIOException(String.format("Unable to store input properties for %s. Property '%s' with value '%s' cannot be serialized.",
                    work.getDisplayName(), propertyName, value), e);
            }
        });
        return builder;
    }

    public static <B extends ImmutableMap.Builder<String, CurrentFileCollectionFingerprint>> B fingerprintInputFiles(UnitOfWork work, B builder) {
        work.visitInputFileProperties((propertyName, value, incremental, fingerprinter) -> builder.put(propertyName, fingerprinter.get()));
        return builder;
    }
}
