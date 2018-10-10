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

package org.gradle.internal.snapshot;

import org.gradle.api.UncheckedIOException;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.isolation.IsolatableFactory;

public interface ValueSnapshotter extends IsolatableFactory {
    /**
     * Creates a {@link ValueSnapshot} of the given value, that contains a snapshot of the current state of the value. A snapshot represents an immutable fingerprint of the value that can be later used to determine if a value has changed.
     *
     * <p>The snapshots must contain no references to the ClassLoader of the value.</p>
     *
     * @throws UncheckedIOException On failure to snapshot the value.
     */
    ValueSnapshot snapshot(Object value) throws UncheckedIOException;

    /**
     * Create an {@link Isolatable} of a value. An isolatable represents a snapshot of the state of the value that can later be used to recreate the value as a Java object.
     *
     * <p>The isolatable may contain references to the ClassLoader of the value.</p>
     *
     * @throws UncheckedIOException On failure to snapshot the value.
     */
    @Override
    <T> Isolatable<T> isolate(T value);

    ValueSnapshot isolatableSnapshot(Object value) throws UncheckedIOException;

    /**
     * Creates a snapshot of the given value, given a candidate snapshot. If the value is the same as the value provided by the candidate snapshot, the candidate _must_ be returned.
     */
    ValueSnapshot snapshot(Object value, ValueSnapshot candidate);
}
