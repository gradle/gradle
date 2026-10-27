/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.api.internal;

import org.gradle.api.Action;
import org.gradle.api.Describable;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.internal.collections.ElementSource;
import org.gradle.api.internal.collections.EventSubscriptionVerifier;

/**
 * Internal counterpart to {@link DomainObjectCollection}.
 */
public interface DomainObjectCollectionInternal<T> extends DomainObjectCollection<T>, Describable {

    /**
     * @see ElementSource#estimatedSize()
     */
    int estimatedSize();

    /**
     * Provide an action to be executed before any changes are made to the collection.
     */
    void beforeCollectionChanges(Action<String> action);

    /**
     * Forwards elements to another collection, such as a composite: like {@link #configureEach(Action)}, the action is executed for each element once it is realized.
     * <p>
     * Unlike {@code configureEach}, the action does not run under the lazy behavior guard, so listeners of the other collection may mutate this one.
     * Eager listeners of the other collection, as reported by {@code targetSubscriptions}, count as eager listeners of this collection,
     * so pending elements are realized for them as they would be for listeners registered here directly.
     */
    void forwardElements(Action<? super T> action, EventSubscriptionVerifier<? super T> targetSubscriptions);

    /**
     * Stops counting the eager listeners of a collection that elements were forwarded to, when this collection is no longer part of it.
     */
    void stopForwardingElements(EventSubscriptionVerifier<? super T> targetSubscriptions);

    /**
     * Realizes the pending elements whose type is subscribed to by the given subscriptions.
     */
    void realizePendingSubscribedBy(EventSubscriptionVerifier<? super T> subscriptions);

    /**
     * Whether there are pending elements that {@link #realizePendingSubscribedBy(EventSubscriptionVerifier)} would realize.
     */
    boolean hasPendingElementsSubscribedBy(EventSubscriptionVerifier<? super T> subscriptions);

    /**
     * Whether there are elements that are not realized yet.
     */
    boolean hasPendingElements();

}
