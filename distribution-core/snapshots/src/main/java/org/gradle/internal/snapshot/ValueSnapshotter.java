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

import javax.annotation.Nullable;

public interface ValueSnapshotter {
    /**
     * Creates a {@link ValueSnapshot} of the given value, that contains a snapshot of the current state of the value. A snapshot represents an immutable fingerprint of the value that can be later used to determine if a value has changed.
     *
     * <p>The snapshots must contain no references to the ClassLoader of the value.</p>
     *
     * @throws ValueSnapshottingException On failure to snapshot the value.
     */
    ValueSnapshot snapshot(@Nullable Object value) throws ValueSnapshottingException;

    /**
     * Creates a snapshot of the given value, given a candidate snapshot. If the value is the same as the value provided by the candidate snapshot, the candidate <em>must</em> be returned.
     *
     * @throws ValueSnapshottingException On failure to snapshot the value.
     */
    ValueSnapshot snapshot(@Nullable Object value, ValueSnapshot candidate) throws ValueSnapshottingException;
}
