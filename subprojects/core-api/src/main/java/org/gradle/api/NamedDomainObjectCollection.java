/*
 * Copyright 2011 the original author or authors.
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
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Internal;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;

/**
 * <p>A {@code NamedDomainObjectCollection} represents a collection of domain objects that have an inherent, constant, name.</p>
 *
 * <p>Objects to be added to a named domain object collection must implement {@code equals()} in such a way that no two objects
 * with different names are considered equal. That is, all equality tests <strong>must</strong> consider the name as an
 * equality key. Behavior is undefined if two objects with different names are considered equal by their {@code equals()} implementation.</p>
 *
 * <p>All implementations <strong>must</strong> guarantee that all elements in the collection are uniquely named. That is,
 * an attempt to add an object with a name equal to the name of any existing object in the collection will fail.
 * Implementations may choose to simply return false from {@code add(T)} or to throw an exception.</p>
 *
 * <p>Objects in the collection are accessible as read-only properties, using the name of the object
 * as the property name. For example (assuming the 'name' property provides the object name):</p>
 *
 * <pre>
 * books.add(new Book(name: "gradle", title: null))
 * books.gradle.title = "Gradle in Action"
 * </pre>
 *
 * <p>A dynamic method is added for each object which takes a configuration closure. This is equivalent to calling
 * {@link #getByName(String, groovy.lang.Closure)}. For example:</p>
 *
 * <pre>
 * books.add(new Book(name: "gradle", title: null))
 * books.gradle {
 *   title = "Gradle in Action"
 * }
 * </pre>
 *
 * <p>You can also use the {@code []} operator to access the objects of a collection by name. For example:</p>
 *
 * <pre>
 * books.add(new Book(name: "gradle", title: null))
 * books['gradle'].title = "Gradle in Action"
 * </pre>
 *
 * <p>{@link Rule} objects can be attached to the collection in order to respond to requests for objects by name
 * where no object with name exists in the collection. This mechanism can be used to create objects on demand.
 * For example: </p>
 *
 * <pre>
 * books.addRule('create any') { books.add(new Book(name: "gradle", title: null)) }
 * books.gradle.name == "gradle"
 * </pre>
 *
 * @param <T> The type of domain objects in this collection.
 */
public interface NamedDomainObjectCollection<T> extends DomainObjectCollection<T> {

    /**
     * Adds an object to the collection, if there is no existing object in the collection with the same name.
     *
     * @param e the item to add to the collection
     * @return {@code true} if the item was added, or {@code} false if an item with the same name already exists.
     */
    @Override
    boolean add(T e);

    /**
     * Adds any of the given objects to the collection that do not have the same name as any existing element.
     *
     * @param c the items to add to the collection
     * @return {@code true} if any item was added, or {@code} false if all items have non unique names within this collection.
     */
    @Override
    boolean addAll(Collection<? extends T> c);

    /**
     * An object that represents the naming strategy used to name objects of this collection.
     *
     * @return Object representing the naming strategy.
     */
    Namer<T> getNamer();

    /**
     * <p>Returns the objects in this collection, as a map from object name to object instance.</p>
     *
     * <p>The map is ordered by the <em>natural ordering</em> of the object names (i.e. keys).</p>
     *
     * @return The objects. Returns an empty map if this collection is empty.
     */
    SortedMap<String, T> getAsMap();

    /**
     * <p>Returns the names of the objects in this collection as a Set of Strings.</p>
     *
     * <p>The set of names is in <em>natural ordering</em>.</p>
     *
     * @return The names. Returns an empty set if this collection is empty.
     */
    SortedSet<String> getNames();

    /**
     * Locates an object by name, returning null if there is no such object.
     *
     * @param name The object name
     * @return The object with the given name, or null if there is no such object in this collection.
     */
    @Nullable
    T findByName(String name);

    /**
     * Locates an object by name, failing if there is no such object.
     *
     * @param name The object name
     * @return The object with the given name. Never returns null.
     * @throws UnknownDomainObjectException when there is no such object in this collection.
     */
    T getByName(String name) throws UnknownDomainObjectException;

    /**
     * Locates an object by name, failing if there is no such object. The given configure closure is executed against
     * the object before it is returned from this method. The object is passed to the closure as its delegate.
     *
     * @param name The object name
     * @param configureClosure The closure to use to configure the object.
     * @return The object with the given name, after the configure closure has been applied to it. Never returns null.
     * @throws UnknownDomainObjectException when there is no such object in this collection.
     */
    T getByName(String name, Closure configureClosure) throws UnknownDomainObjectException;

    /**
     * Locates an object by name, failing if there is no such object. The given configure action is executed against
     * the object before it is returned from this method.
     *
     * @param name The object name
     * @param configureAction The action to use to configure the object.
     * @return The object with the given name, after the configure action has been applied to it. Never returns null.
     * @throws UnknownDomainObjectException when there is no such object in this collection.
     * @since 3.1
     */
    T getByName(String name, Action<? super T> configureAction) throws UnknownDomainObjectException;

    /**
     * Locates an object by name, failing if there is no such task. This method is identical to {@link
     * #getByName(String)}. You can call this method in your build script by using the groovy {@code []} operator.
     *
     * @param name The object name
     * @return The object with the given name. Never returns null.
     * @throws UnknownDomainObjectException when there is no such object in this collection.
     */
    T getAt(String name) throws UnknownDomainObjectException;

    /**
     * Adds a rule to this collection. The given rule is invoked when an unknown object is requested by name.
     *
     * @param rule The rule to add.
     * @return The added rule.
     */
    Rule addRule(Rule rule);

    /**
     * Adds a rule to this collection. The given closure is executed when an unknown object is requested by name. The
     * requested name is passed to the closure as a parameter.
     *
     * @param description The description of the rule.
     * @param ruleAction The closure to execute to apply the rule.
     * @return The added rule.
     */
    Rule addRule(String description, Closure ruleAction);

    /**
     * Adds a rule to this collection. The given action is executed when an unknown object is requested by name. The
     * requested name is passed to the action.
     *
     * @param description The description of the rule.
     * @param ruleAction The action to execute to apply the rule.
     * @return The added rule.
     * @since 3.3
     */
    Rule addRule(String description, Action<String> ruleAction);

    /**
     * Returns the rules used by this collection.
     *
     * @return The rules, in the order they will be applied.
     */
    List<Rule> getRules();

    /**
     * {@inheritDoc}
     */
    @Override
    <S extends T> NamedDomainObjectCollection<S> withType(Class<S> type);

    /**
     * {@inheritDoc}
     */
    @Override
    NamedDomainObjectCollection<T> matching(Spec<? super T> spec);

    /**
     * {@inheritDoc}
     */
    @Override
    NamedDomainObjectCollection<T> matching(Closure spec);

    /**
     * Locates a object by name, without triggering its creation or configuration, failing if there is no such object.
     *
     * @param name The object's name
     * @return A {@link Provider} that will return the object when queried. The object may be created and configured at this point, if not already.
     * @throws UnknownDomainObjectException If a object with the given name is not defined.
     * @since 4.10
     */
    NamedDomainObjectProvider<T> named(String name) throws UnknownDomainObjectException;

    /**
     * Locates a object by name, without triggering its creation or configuration, failing if there is no such object.
     * The given configure action is executed against the object before it is returned from the provider.
     *
     * @param name The object's name
     * @return A {@link Provider} that will return the object when queried. The object may be created and configured at this point, if not already.
     * @throws UnknownDomainObjectException If a object with the given name is not defined.
     * @since 5.0
     */
    NamedDomainObjectProvider<T> named(String name, Action<? super T> configurationAction) throws UnknownDomainObjectException;

    /**
     * Locates a object by name and type, without triggering its creation or configuration, failing if there is no such object.
     *
     * @param name The object's name
     * @param type The object's type
     * @return A {@link Provider} that will return the object when queried. The object may be created and configured at this point, if not already.
     * @throws UnknownDomainObjectException If a object with the given name is not defined.
     * @since 5.0
     */
    <S extends T> NamedDomainObjectProvider<S> named(String name, Class<S> type) throws UnknownDomainObjectException;

    /**
     * Locates a object by name and type, without triggering its creation or configuration, failing if there is no such object.
     * The given configure action is executed against the object before it is returned from the provider.
     *
     * @param name The object's name
     * @param type The object's type
     * @param configurationAction The action to use to configure the object.
     * @return A {@link Provider} that will return the object when queried. The object may be created and configured at this point, if not already.
     * @throws UnknownDomainObjectException If a object with the given name is not defined.
     * @since 5.0
     */
    <S extends T> NamedDomainObjectProvider<S> named(String name, Class<S> type, Action<? super S> configurationAction) throws UnknownDomainObjectException;

    /**
     * Provides access to the schema of all created or registered named domain objects in this collection.
     *
     * @since 4.10
     */
    @Internal
    NamedDomainObjectCollectionSchema getCollectionSchema();
}
