/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;

import java.util.Collection;

/**
 * <p>A {@code DomainObjectCollection} is a specialised {@link Collection} that adds the ability to receive modification notifications and use live filtered sub collections.</p>
 *
 * <p>The filtered collections returned by the filtering methods, such as {@link #matching(Closure)}, return collections that are <em>live</em>. That is, they reflect
 * changes made to the source collection that they were created from. This is true for filtered collections made from filtered collections etc.</p>
 * <p>
 * You can also add actions that are executed as elements are added to the collection. Actions added to filtered collections will be fired if an addition/removal
 * occurs for the source collection that matches the filter.</p>
 * <p>
 * {@code DomainObjectCollection} instances are not <em>thread-safe</em> and undefined behavior may result from the invocation of any method on a collection that is being mutated by another
 * thread; this includes direct invocations, passing the collection to a method that might perform invocations, and using an existing iterator to examine the collection.
 * </p>
 *
 * @param <T> The type of objects in this collection.
 */
public interface DomainObjectCollection<T> extends Collection<T> {
    /**
     * Adds an element to this collection, given a {@link Provider} that will provide the element when required.
     *
     * @param provider A {@link Provider} that can provide the element when required.
     * @since 4.8
     */
    void addLater(Provider<? extends T> provider);

    /**
     * Adds elements to this collection, given a {@link Provider} of {@link Iterable} that will provide the elements when required.
     *
     * @param provider A {@link Provider} of {@link Iterable} that can provide the elements when required.
     * @since 5.0
     */
    void addAllLater(Provider<? extends Iterable<T>> provider);

    /**
     * Returns a collection containing the objects in this collection of the given type.  The returned collection is
     * live, so that when matching objects are later added to this collection, they are also visible in the filtered
     * collection.
     * <p>
     * This method is an intermediate operation. It does not change the realized/unrealized state of the elements in the collection.
     *
     * @param type The type of objects to find.
     * @return The matching objects. Returns an empty collection if there are no such objects in this collection.
     */
    <S extends T> DomainObjectCollection<S> withType(Class<S> type);

    /**
     * Returns a collection containing the objects in this collection of the given type. Equivalent to calling
     * {@code withType(type).all(configureAction)}
     * <p>
     * This method is a terminal eager operation. It will cause the realization of all elements of this collection.
     *
     * @param type The type of objects to find.
     * @param configureAction The action to execute for each object in the resulting collection.
     * @return The matching objects. Returns an empty collection if there are no such objects in this collection.
     */
    <S extends T> DomainObjectCollection<S> withType(Class<S> type, Action<? super S> configureAction);

    /**
     * Returns a collection containing the objects in this collection of the given type. Equivalent to calling
     * {@code withType(type).all(configureClosure)}.
     * <p>
     * This method is a terminal eager operation. It will cause the realization of all elements of this collection.
     *
     * @param type The type of objects to find.
     * @param configureClosure The closure to execute for each object in the resulting collection.
     * @return The matching objects. Returns an empty collection if there are no such objects in this collection.
     */
    <S extends T> DomainObjectCollection<S> withType(@DelegatesTo.Target Class<S> type, @DelegatesTo(genericTypeIndex = 0) Closure configureClosure);

    /**
     * Returns a collection which contains the objects in this collection which meet the given specification. The
     * returned collection is live, so that when matching objects are added to this collection, they are also visible in
     * the filtered collection.
     * <p>
     * This method is an intermediate eager operation. The application of the filter forces the realization of the collection elements.
     *
     * @param spec The specification to use.
     * @return The collection of matching objects. Returns an empty collection if there are no such objects in this
     *         collection.
     */
    DomainObjectCollection<T> matching(Spec<? super T> spec);

    /**
     * Returns a collection which contains the objects in this collection which meet the given closure specification. The
     * returned collection is live, so that when matching objects are added to this collection, they are also visible in
     * the filtered collection.
     * <p>
     * This method is an intermediate eager operation. The application of the filter forces the realization of the collection elements.
     *
     * @param spec The specification to use. The closure gets a collection element as an argument.
     * @return The collection of matching objects. Returns an empty collection if there are no such objects in this
     *         collection.
     */
    DomainObjectCollection<T> matching(Closure spec);

    /**
     * Adds an {@code Action} to be executed when an object is added to this collection.
     * <p>
     * Like {@link #all(Action)}, this method will cause all objects in this container to be realized.
     *
     * @param action The action to be executed
     * @return the supplied action
     */
    Action<? super T> whenObjectAdded(Action<? super T> action);

    /**
     * Adds a closure to be called when an object is added to this collection. The newly added object is passed to the
     * closure as the parameter.
     * <p>
     * Like {@link #all(Action)}, this method will cause all objects in this container to be realized.
     *
     * @param action The closure to be called
     * @see #whenObjectAdded(Action)
     */
    void whenObjectAdded(Closure action);

    /**
     * Adds an {@code Action} to be executed when an object is removed from this collection.
     * <p>
     * This method will only be invoked for elements effectively added to the collection.
     * Elements that are {@link #addLater(Provider)} will be seen by this action only if they have been observed as part of the collection.
     * All elements affected by this action will be realized.
     *
     * @param action The action to be executed
     * @return the supplied action
     */
    Action<? super T> whenObjectRemoved(Action<? super T> action);

    /**
     * Adds a closure to be called when an object is removed from this collection. The removed object is passed to the
     * closure as the parameter.
     * <p>
     * This method will only be invoked for elements effectively added to the collection.
     * Elements that are {@link #addLater(Provider)} will be seen by this action only if they have been observed as part of the collection.
     * All elements affected by this action will be realized.
     *
     * @param action The closure to be called
     */
    void whenObjectRemoved(Closure action);

    /**
     * Executes the given action against all objects in this collection, and any objects subsequently added to this
     * collection.
     * <p>
     * This method is a terminal eager operation. It will cause the realization of all elements of this collection.
     *
     * @param action The action to be executed
     */
    void all(Action<? super T> action);

    /**
     * Executes the given closure against all objects in this collection, and any objects subsequently added to this collection. The object is passed to the closure as the closure
     * delegate. Alternatively, it is also passed as a parameter.
     * <p>
     * This method is a terminal eager operation. It will cause the realization of all elements of this collection.
     *
     * @param action The action to be executed
     */
    void all(Closure action);

    /**
     * Configures each element in this collection using the given action, as each element is required. Actions are run in the order added.
     * <p>
     * This method is a terminal lazy operation. Lazy collection elements will not be realized.
     *
     * @param action A {@link Action} that can configure the element when required.
     * @since 4.9
     */
    void configureEach(Action<? super T> action);

    // note: this is here to override the default Groovy Collection.findAll { } method.
    /**
     * Returns a collection which contains the objects in this collection which meet the given closure specification.
     * <p>
     * This method is an eager operation. The application of the filter forces the realization of all of the underlying collection elements.
     *
     * @param spec The specification to use. The closure gets a collection element as an argument.
     * @return The collection of matching objects. Returns an empty collection if there are no such objects in this
     *         collection.
     */
    Collection<T> findAll(Closure spec);
}
