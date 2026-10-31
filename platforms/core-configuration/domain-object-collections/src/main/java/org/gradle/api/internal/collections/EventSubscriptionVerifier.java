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

package org.gradle.api.internal.collections;

import org.jspecify.annotations.Nullable;

/**
 * Tracks which types are subscribed to by eager event listeners. Lazy elements of a
 * subscribed type should be immediately realized when added to a container.
 */
public interface EventSubscriptionVerifier<T> {

    /**
     * Determines whether events should be emitted for elements of the given type.
     */
    boolean isSubscribed(@Nullable Class<? extends T> type);

    /**
     * Whether an eager listener is subscribed to the given type, rather than only a composite collection forwarding the elements.
     * Pending elements realized on add only to be forwarded are queried again when the collection is read,
     * so that derived collections such as {@code Configuration.getAllDependencies()} don't capture values early.
     */
    default boolean isSubscribedByListener(@Nullable Class<? extends T> type) {
        return isSubscribed(type);
    }
}
