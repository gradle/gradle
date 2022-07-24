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
import org.gradle.api.Namer
import org.gradle.api.Rule
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

import java.util.SortedMap
import java.util.SortedSet


/**
 * Facilitates the implementation of the [TaskContainer] interface by delegation via subclassing.
 *
 * See [GradleDelegate] for why this is currently necessary.
 */
abstract class TaskContainerDelegate : TaskContainer {

    internal
    abstract val delegate: TaskContainer

    override fun contains(element: Task): Boolean =
        delegate.contains(element)

    override fun addAll(elements: Collection<Task>): Boolean =
        delegate.addAll(elements)

    override fun matching(spec: Spec<in Task>): TaskCollection<Task> =
        delegate.matching(spec)

    override fun matching(closure: Closure<Any>): TaskCollection<Task> =
        delegate.matching(closure)

    override fun clear() =
        delegate.clear()

    override fun addRule(rule: Rule): Rule =
        delegate.addRule(rule)

    override fun addRule(description: String, ruleAction: Closure<Any>): Rule =
        delegate.addRule(description, ruleAction)

    override fun addRule(description: String, ruleAction: Action<String>): Rule =
        delegate.addRule(description, ruleAction)

    override fun configure(configureClosure: Closure<Any>): NamedDomainObjectContainer<Task> =
        delegate.configure(configureClosure)

    override fun addAllLater(provider: Provider<out Iterable<Task>>) =
        delegate.addAllLater(provider)

    override fun create(options: Map<String, *>): Task =
        delegate.create(options)

    override fun create(options: Map<String, *>, configureClosure: Closure<Any>): Task =
        delegate.create(options, configureClosure)

    override fun create(name: String, configureClosure: Closure<Any>): Task =
        delegate.create(name, configureClosure)

    override fun create(name: String): Task =
        delegate.create(name)

    override fun <T : Task> create(name: String, type: Class<T>): T =
        delegate.create(name, type)

    override fun <T : Task> create(name: String, type: Class<T>, vararg constructorArgs: Any?): T =
        delegate.create(name, type, *constructorArgs)

    override fun <T : Task> create(name: String, type: Class<T>, configuration: Action<in T>): T =
        delegate.create(name, type, configuration)

    override fun create(name: String, configureAction: Action<in Task>): Task =
        delegate.create(name, configureAction)

    override fun whenTaskAdded(action: Action<in Task>): Action<in Task> =
        delegate.whenTaskAdded(action)

    override fun whenTaskAdded(closure: Closure<Any>) =
        delegate.whenTaskAdded(closure)

    override fun removeAll(elements: Collection<Task>): Boolean =
        delegate.removeAll(elements)

    override fun getByPath(path: String): Task =
        delegate.getByPath(path)

    override fun add(element: Task): Boolean =
        delegate.add(element)

    override fun all(action: Action<in Task>) =
        delegate.all(action)

    override fun all(action: Closure<Any>) =
        delegate.all(action)

    override fun register(name: String, configurationAction: Action<in Task>): TaskProvider<Task> =
        delegate.register(name, configurationAction)

    override fun <T : Task> register(name: String, type: Class<T>, configurationAction: Action<in T>): TaskProvider<T> =
        delegate.register(name, type, configurationAction)

    override fun <T : Task> register(name: String, type: Class<T>): TaskProvider<T> =
        delegate.register(name, type)

    override fun <T : Task> register(name: String, type: Class<T>, vararg constructorArgs: Any?): TaskProvider<T> =
        delegate.register(name, type, *constructorArgs)

    override fun register(name: String): TaskProvider<Task> =
        delegate.register(name)

    override fun replace(name: String): Task =
        delegate.replace(name)

    override fun <T : Task> replace(name: String, type: Class<T>): T =
        delegate.replace(name, type)

    override fun iterator(): MutableIterator<Task> =
        delegate.iterator()

    override fun named(name: String): TaskProvider<Task> =
        delegate.named(name)

    override fun named(name: String, configurationAction: Action<in Task>): TaskProvider<Task> =
        delegate.named(name, configurationAction)

    override fun <S : Task> named(name: String, type: Class<S>): TaskProvider<S> =
        delegate.named(name, type)

    override fun <S : Task> named(name: String, type: Class<S>, configurationAction: Action<in S>): TaskProvider<S> =
        delegate.named(name, type, configurationAction)

    override fun getNamer(): Namer<Task> =
        delegate.namer

    override fun getRules(): MutableList<Rule> =
        delegate.rules

    override fun getCollectionSchema(): NamedDomainObjectCollectionSchema =
        delegate.collectionSchema

    override fun whenObjectRemoved(action: Action<in Task>): Action<in Task> =
        delegate.whenObjectRemoved(action)

    override fun whenObjectRemoved(action: Closure<Any>) =
        delegate.whenObjectRemoved(action)

    override fun findAll(spec: Closure<Any>): MutableSet<Task> =
        delegate.findAll(spec)

    override fun addLater(provider: Provider<out Task>) =
        delegate.addLater(provider)

    override fun containsAll(elements: Collection<Task>): Boolean =
        delegate.containsAll(elements)

    override fun isEmpty(): Boolean =
        delegate.isEmpty()

    override fun <U : Task> containerWithType(type: Class<U>): NamedDomainObjectContainer<U> =
        delegate.containerWithType(type)

    override fun getByName(name: String, configureClosure: Closure<Any>): Task =
        delegate.getByName(name, configureClosure)

    override fun getByName(name: String): Task =
        delegate.getByName(name)

    override fun getByName(name: String, configureAction: Action<in Task>): Task =
        delegate.getByName(name, configureAction)

    override fun configureEach(action: Action<in Task>) =
        delegate.configureEach(action)

    override fun <U : Task> maybeCreate(name: String, type: Class<U>): U =
        delegate.maybeCreate(name, type)

    override fun maybeCreate(name: String): Task =
        delegate.maybeCreate(name)

    override fun findByPath(path: String): Task? =
        delegate.findByPath(path)

    override fun <S : Task> withType(type: Class<S>): TaskCollection<S> =
        delegate.withType(type)

    override fun <S : Task> withType(type: Class<S>, configureAction: Action<in S>): DomainObjectCollection<S> =
        delegate.withType(type, configureAction)

    override fun <S : Task> withType(type: Class<S>, configureClosure: Closure<Any>): DomainObjectCollection<S> =
        delegate.withType(type, configureClosure)

    override fun findByName(name: String): Task? =
        delegate.findByName(name)

    override fun whenObjectAdded(action: Action<in Task>): Action<in Task> =
        delegate.whenObjectAdded(action)

    override fun whenObjectAdded(action: Closure<Any>) =
        delegate.whenObjectAdded(action)

    override fun retainAll(elements: Collection<Task>): Boolean =
        delegate.retainAll(elements)

    override fun getAsMap(): SortedMap<String, Task> =
        delegate.asMap

    override fun getNames(): SortedSet<String> =
        delegate.names

    override fun getAt(name: String): Task =
        delegate.getAt(name)

    override val size: Int
        get() = delegate.size

    override fun remove(element: Task): Boolean =
        delegate.remove(element)
}
