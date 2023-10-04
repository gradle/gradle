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

package org.gradle.internal.isolation;

import org.gradle.internal.hash.Hashable;
import org.gradle.internal.snapshot.ValueSnapshot;

import javax.annotation.Nullable;

/**
 * Isolatable objects can return an isolated instance of the given type T from which this object was created.
 * An <b>isolated</b> instance has the same internal state as the original object on which this isolatable was based,
 * but it is guaranteed not to retain any references to mutable state from the original instance.
 * <p>
 * The primary reason to need such an isolated instance of an object is to ensure that work can be done in parallel using the instance without
 * fear that its internal state is changing while the work is being carried out.
 */
public interface Isolatable<T> extends Hashable {
    /**
     * Returns this value as a {@link ValueSnapshot}. The returned value should not hold any references to user ClassLoaders.
     */
    ValueSnapshot asSnapshot();

    /**
     * Returns an instance of T that is isolated from the original object and all other instances.
     * When T is mutable, a new instance is created on each call. When T is immutable, a new instance may or may not be created on each call. This may potentially be expensive.
     */
    @Nullable
    T isolate();

    /**
     * Returns an instance of S constructed from the state of the original object, if possible.
     *
     * @return null if not supported, or the value is null.
     */
    @Nullable
    <S> S coerce(Class<S> type);
}
