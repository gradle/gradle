/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.instantiation;

/**
 * Mixed into each generated class, to mark it as fully managed.
 */
public interface Managed {
    /**
     * Returns a snapshot of the current state of this object. This can be passed to the {@link Factory#fromState(Class, Object)} method to recreate this object from the snapshot.
     * Note that the state may not be immutable, so should be made isolated to reuse in another context. The state can also be fingerprinted to generate a fingerprint of this object.
     */
    Object unpackState();

    /**
     * Is this object graph immutable? Returns false if this object <em>may</em> be mutable, in which case the state should be unpacked and isolated.
     */
    boolean immutable();

    Class<?> publicType();

    Factory managedFactory();

    interface Factory {
        <T> T fromState(Class<T> type, Object state);
    }
}
