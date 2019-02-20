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

package org.gradle.internal.state;

import javax.annotation.Nullable;

/**
 * Implemented by types whose state is fully managed by Gradle. Mixed into generated classes whose state is fully managed.
 */
public interface Managed {
    /**
     * Returns a snapshot of the current state of this object. This can be passed to the {@link Factory#fromState(Class, Object)} method to recreate this object from the snapshot.
     * Note that the state may not be immutable, so should be made isolated to reuse in another context. The state can also be fingerprinted to generate a fingerprint of this object.
     *
     * <p><em>Note that currently the state should reference only JVM and core Gradle types when {@link #immutable()} returns true.</em></p>
     */
    Object unpackState();

    /**
     * Is this object graph immutable? Returns false if this object <em>may</em> be mutable, in which case the state should be unpacked and isolated.
     */
    boolean immutable();

    /**
     * Returns the public type of this managed instance. Currently is used to identify the implementation.
     */
    Class<?> publicType();

    /**
     * Returns the factory that can be used to create new instances of this type.
     */
    Factory managedFactory();

    interface Factory {
        /**
         * Creates an instance of the given type from the given state, if possible.
         *
         * @return null when the given type is not supported.
         */
        @Nullable
        <T> T fromState(Class<T> type, Object state);
    }
}
