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

package org.gradle.kotlin.dsl.support.delegates

import groovy.lang.Closure

import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.NamedDomainObjectCollectionSchema
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.Namer
import org.gradle.api.Rule
import org.gradle.api.provider.Provider
import org.gradle.api.specs.Spec

import java.util.SortedMap
import java.util.SortedSet


/**
 * Facilitates the implementation of the [NamedDomainObjectContainer] interface by delegation via subclassing.
 *
 * See [GradleDelegate] for why this is currently necessary.
 */
abstract class NamedDomainObjectContainerDelegate<T : Any> : NamedDomainObjectContainer<T> {

    internal
    abstract val delegate: NamedDomainObjectContainer<T>

    override fun contains(element: T): Boolean =
        delegate.contains(element)

    override fun addAll(elements: Collection<T>): Boolean =
        delegate.addAll(elements)

    override fun matching(spec: Spec<in T>): NamedDomainObjectSet<T> =
        delegate.matching(spec)

    override fun matching(spec: Closure<Any>): NamedDomainObjectSet<T> =
        delegate.matching(spec)

    override fun clear() =
        delegate.clear()

    override fun addRule(rule: Rule): Rule =
        delegate.addRule(rule)

    override fun addRule(description: String, ruleAction: Closure<Any>): Rule =
        delegate.addRule(description, ruleAction)

    override fun addRule(description: String, ruleAction: Action<String>): Rule =
        delegate.addRule(description, ruleAction)

    override fun configure(configureClosure: Closure<Any>): NamedDomainObjectContainer<T> =
        delegate.configure(configureClosure)

    override fun addAllLater(provider: Provider<out Iterable<T>>) =
        delegate.addAllLater(provider)

    override fun create(name: String): T =
        delegate.create(name)

    override fun create(name: String, configureClosure: Closure<Any>): T =
        delegate.create(name, configureClosure)

    override fun create(name: String, configureAction: Action<in T>): T =
        delegate.create(name, configureAction)

    override fun removeAll(elements: Collection<T>): Boolean =
        delegate.removeAll(elements)

    override fun add(element: T): Boolean =
        delegate.add(element)

    override fun all(action: Action<in T>) =
        delegate.all(action)

    override fun all(action: Closure<Any>) =
        delegate.all(action)

    override fun register(name: String, configurationAction: Action<in T>): NamedDomainObjectProvider<T> =
        delegate.register(name, configurationAction)

    override fun register(name: String): NamedDomainObjectProvider<T> =
        delegate.register(name)

    override fun iterator(): MutableIterator<T> =
        delegate.iterator()

    override fun getNamer(): Namer<T> =
        delegate.namer

    override fun getRules(): MutableList<Rule> =
        delegate.rules

    override fun named(name: String): NamedDomainObjectProvider<T> =
        delegate.named(name)

    override fun named(name: String, configurationAction: Action<in T>): NamedDomainObjectProvider<T> =
        delegate.named(name, configurationAction)

    override fun <S : T> named(name: String, type: Class<S>): NamedDomainObjectProvider<S> =
        delegate.named(name, type)

    override fun getCollectionSchema(): NamedDomainObjectCollectionSchema =
        delegate.collectionSchema

    override fun whenObjectRemoved(action: Action<in T>): Action<in T> =
        delegate.whenObjectRemoved(action)

    override fun whenObjectRemoved(action: Closure<Any>) =
        delegate.whenObjectRemoved(action)

    override fun findAll(spec: Closure<Any>): MutableSet<T> =
        delegate.findAll(spec)

    override fun addLater(provider: Provider<out T>) =
        delegate.addLater(provider)

    override fun containsAll(elements: Collection<T>): Boolean =
        delegate.containsAll(elements)

    override fun isEmpty(): Boolean =
        delegate.isEmpty()

    override fun remove(element: T): Boolean =
        delegate.remove(element)

    override fun getAsMap(): SortedMap<String, T> =
        delegate.asMap

    override fun getNames(): SortedSet<String> =
        delegate.names

    override fun getByName(name: String): T =
        delegate.getByName(name)

    override fun getByName(name: String, configureClosure: Closure<Any>): T =
        delegate.getByName(name, configureClosure)

    override fun getByName(name: String, configureAction: Action<in T>): T =
        delegate.getByName(name, configureAction)

    override fun <S : T> withType(type: Class<S>, configureClosure: Closure<Any>): DomainObjectCollection<S> =
        delegate.withType(type, configureClosure)

    override fun configureEach(action: Action<in T>) =
        delegate.configureEach(action)

    override fun maybeCreate(name: String): T =
        delegate.maybeCreate(name)

    override fun <S : T> withType(type: Class<S>): NamedDomainObjectSet<S> =
        delegate.withType(type)

    override fun <S : T> withType(type: Class<S>, configureAction: Action<in S>): DomainObjectCollection<S> =
        delegate.withType(type, configureAction)

    override fun findByName(name: String): T? =
        delegate.findByName(name)

    override fun whenObjectAdded(action: Action<in T>): Action<in T> =
        delegate.whenObjectAdded(action)

    override fun whenObjectAdded(action: Closure<Any>) =
        delegate.whenObjectAdded(action)

    override fun retainAll(elements: Collection<T>): Boolean =
        delegate.retainAll(elements)

    override fun getAt(name: String): T =
        delegate.getAt(name)

    override fun <S : T> named(name: String, type: Class<S>, configurationAction: Action<in S>): NamedDomainObjectProvider<S> =
        delegate.named(name, type, configurationAction)

    override val size: Int
        get() = delegate.size
}
