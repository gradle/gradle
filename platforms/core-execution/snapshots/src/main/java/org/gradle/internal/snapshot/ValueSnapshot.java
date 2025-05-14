/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.internal.hash.Hashable;
import org.jspecify.annotations.Nullable;

/**
 * An immutable snapshot of the state of some Java object or object graph.
 *
 * <p>Implementations are not required to be able to recreate the object, and should retain as little state as possible.
 * In particular, implementations should not hold on to user ClassLoaders.</p>
 */
public interface ValueSnapshot extends Hashable {
    /**
     * Takes a snapshot of the given value, using this as a candidate snapshot. If the value is the same as the value represented by this snapshot, this snapshot <em>must</em> be returned.
     */
    ValueSnapshot snapshot(@Nullable Object value, ValueSnapshotter snapshotter);
}
