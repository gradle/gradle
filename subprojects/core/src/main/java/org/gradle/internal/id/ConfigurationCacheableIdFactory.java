/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.id;


import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.concurrent.ThreadSafe;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Creates unique ids for objects created during configuration.
 * <p>
 * Usage of this factory in object factories ensures that ids are reused consistently when loading from the configuration cache.
 */
@ThreadSafe
@ServiceScope(Scopes.BuildTree.class)
public class ConfigurationCacheableIdFactory {

    private static final long USED_ASSIGNED_ID_MARKER = -1;

    private final AtomicLong sequence = new AtomicLong(0);

    /**
     * Creates a new unique id.
     * <p>
     * New ids can only be created if no ids have been loaded from the configuration cache.
     * When re-creating an object due to loading from the configuration cache,
     * {@link #idRecreated()} must be called before the object is re-created to make sure the consistency of the ids.
     *
     * @throws IllegalStateException if an id has already been loaded.
     */
    public long createId() {
        long newId = sequence.updateAndGet(it -> it == USED_ASSIGNED_ID_MARKER ? it : it + 1);
        if (newId == USED_ASSIGNED_ID_MARKER) {
            throw new IllegalStateException("Cannot create a new id after one has been loaded");
        }

        return newId;
    }

    /**
     * This method must be called whenever an object is about to be re-created with the previously assigned id
     * due to loading from the configuration cache.
     */
    public void idRecreated() {
        sequence.set(USED_ASSIGNED_ID_MARKER);
    }
}
