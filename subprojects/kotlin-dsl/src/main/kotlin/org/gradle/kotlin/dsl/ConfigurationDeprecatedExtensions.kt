/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationPublications
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskDependency

import java.io.File


// TODO: Remove these methods entirely when we no longer support plugins that were built using these methods.


/**
 * See [Configuration.getBuildDependencies].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().buildDependencies"), level = DeprecationLevel.HIDDEN)
val <T : Configuration> NamedDomainObjectProvider<T>.buildDependencies: TaskDependency
    get() = get().buildDependencies


/**
 * See [Configuration.getSingleFile].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().singleFile"), level = DeprecationLevel.HIDDEN)
val <T : Configuration> NamedDomainObjectProvider<T>.singleFile: File
    get() = get().singleFile


/**
 * See [Configuration.getFiles].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().files"), level = DeprecationLevel.HIDDEN)
val <T : Configuration> NamedDomainObjectProvider<T>.files: Set<File>
    get() = get().files


/**
 * See [Configuration.contains].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().contains(element)"), level = DeprecationLevel.HIDDEN)
fun <T : Configuration> NamedDomainObjectProvider<T>.contains(element: File): Boolean =
    get().contains(element)


/**
 * See [Configuration.getAsPath].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().asPath"), level = DeprecationLevel.HIDDEN)
val <T : Configuration> NamedDomainObjectProvider<T>.asPath: String
    get() = get().asPath


/**
 * See [Configuration.plus].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().plus(collection)"), level = DeprecationLevel.HIDDEN)
operator fun <T : Configuration> NamedDomainObjectProvider<T>.plus(collection: FileCollection): FileCollection =
    get().plus(collection)


/**
 * See [Configuration.plus].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().plus(configuration)"), level = DeprecationLevel.HIDDEN)
operator fun <T : Configuration> FileCollection.plus(configuration: NamedDomainObjectProvider<T>): FileCollection =
    plus(configuration.get())


/**
 * See [Configuration.minus].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().minus(collection)"), level = DeprecationLevel.HIDDEN)
operator fun <T : Configuration> NamedDomainObjectProvider<T>.minus(collection: FileCollection): FileCollection =
    get().minus(collection)


/**
 * See [Configuration.minus].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().minus(configuration)"), level = DeprecationLevel.HIDDEN)
operator fun <T : Configuration> FileCollection.minus(configuration: NamedDomainObjectProvider<T>): FileCollection =
    minus(configuration.get())


/**
 * See [Configuration.filter].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().filter(filterSpec"), level = DeprecationLevel.HIDDEN)
fun <T : Configuration> NamedDomainObjectProvider<T>.filter(filterSpec: Spec<File>) =
    get().filter(filterSpec)


/**
 * See [Configuration.isEmpty].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().isEmpty"), level = DeprecationLevel.HIDDEN)
val <T : Configuration> NamedDomainObjectProvider<T>.isEmpty: Boolean
    get() = get().isEmpty


/**
 * See [Configuration.getAsFileTree].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().asFileTree"), level = DeprecationLevel.HIDDEN)
val <T : Configuration> NamedDomainObjectProvider<T>.asFileTree: FileTree
    get() = get().asFileTree


/**
 * See [Configuration.getResolutionStrategy].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().resolutionStrategy"), level = DeprecationLevel.HIDDEN)
val <T : Configuration> NamedDomainObjectProvider<T>.resolutionStrategy
    get() = get().resolutionStrategy


/**
 * See [Configuration.resolutionStrategy].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().resolutionStrategy"), level = DeprecationLevel.HIDDEN)
fun <T : Configuration> NamedDomainObjectProvider<T>.resolutionStrategy(action: ResolutionStrategy.() -> Unit) =
    get().resolutionStrategy(action)


/**
 * See [Configuration.getState].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().state"), level = DeprecationLevel.HIDDEN)
val <T : Configuration> NamedDomainObjectProvider<T>.state
    get() = get().state


/**
 * See [Configuration.isVisible].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().isVisible"), level = DeprecationLevel.HIDDEN)
var <T : Configuration> NamedDomainObjectProvider<T>.isVisible
    get() = get().isVisible
    set(value) {
        get().isVisible = value
    }


/**
 * See [Configuration.getExtendsFrom].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().extendsFrom"), level = DeprecationLevel.HIDDEN)
val <T : Configuration> NamedDomainObjectProvider<T>.extendsFrom: Set<Configuration>
    get() = get().extendsFrom


/**
 * See [Configuration.setExtendsFrom].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().setExtendsFrom(superConfigs)"), level = DeprecationLevel.HIDDEN)
fun <T : Configuration> NamedDomainObjectProvider<T>.setExtendsFrom(superConfigs: Iterable<Configuration>): Configuration =
    get().setExtendsFrom(superConfigs)


/**
 * See [Configuration.extendsFrom].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().extendsFrom(superConfigs)"), level = DeprecationLevel.HIDDEN)
fun <T : Configuration> NamedDomainObjectProvider<T>.extendsFrom(vararg superConfigs: Configuration) =
    get().extendsFrom(*superConfigs)


/**
 * See [Configuration.isTransitive].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().isTransitive"), level = DeprecationLevel.HIDDEN)
val <T : Configuration> NamedDomainObjectProvider<T>.isTransitive
    get() = get().isTransitive


/**
 * See [Configuration.setTransitive].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().setTransitive(t)"), level = DeprecationLevel.HIDDEN)
fun <T : Configuration> NamedDomainObjectProvider<T>.setTransitive(t: Boolean): Configuration =
    get().setTransitive(t)


/**
 * See [Configuration.getDescription].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().description"), level = DeprecationLevel.HIDDEN)
val <T : Configuration> NamedDomainObjectProvider<T>.description
    get() = get().description


/**
 * See [Configuration.setDescription].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().setDescription(description)"), level = DeprecationLevel.HIDDEN)
fun <T : Configuration> NamedDomainObjectProvider<T>.setDescription(description: String?): Configuration =
    get().setDescription(description)


/**
 * See [Configuration.getHierarchy].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().hierarchy"), level = DeprecationLevel.HIDDEN)
val <T : Configuration> NamedDomainObjectProvider<T>.hierarchy: Set<Configuration>
    get() = get().hierarchy


/**
 * See [Configuration.resolve].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().resolve()"), level = DeprecationLevel.HIDDEN)
fun <T : Configuration> NamedDomainObjectProvider<T>.resolve(): Set<File> =
    get().resolve()


/**
 * See [Configuration.files].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().files(dependencySpec)"), level = DeprecationLevel.HIDDEN)
fun <T : Configuration> NamedDomainObjectProvider<T>.files(dependencySpec: Spec<Dependency>): Set<File> =
    get().files(dependencySpec)


/**
 * See [Configuration.files].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().files(dependencies)"), level = DeprecationLevel.HIDDEN)
fun <T : Configuration> NamedDomainObjectProvider<T>.files(vararg dependencies: Dependency): Set<File> =
    get().files(*dependencies)


/**
 * See [Configuration.fileCollection].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().fileCollection(dependencySpec)"), level = DeprecationLevel.HIDDEN)
fun <T : Configuration> NamedDomainObjectProvider<T>.fileCollection(dependencySpec: Spec<Dependency>): FileCollection =
    get().fileCollection(dependencySpec)


/**
 * See [Configuration.fileCollection].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().fileCollection(dependencies)"), level = DeprecationLevel.HIDDEN)
fun <T : Configuration> NamedDomainObjectProvider<T>.fileCollection(vararg dependencies: Dependency): FileCollection =
    get().fileCollection(*dependencies)


/**
 * See [Configuration.getResolvedConfiguration].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().resolvedConfiguration"), level = DeprecationLevel.HIDDEN)
val <T : Configuration> NamedDomainObjectProvider<T>.resolvedConfiguration
    get() = get().resolvedConfiguration


/**
 * See [Configuration.getTaskDependencyFromProjectDependency].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().getTaskDependencyFromProjectDependency(useDependedOn, taskName)"), level = DeprecationLevel.HIDDEN)
fun <T : Configuration> NamedDomainObjectProvider<T>.getTaskDependencyFromProjectDependency(useDependedOn: Boolean, taskName: String): TaskDependency =
    get().getTaskDependencyFromProjectDependency(useDependedOn, taskName)


/**
 * See [Configuration.getDependencies].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().dependencies"), level = DeprecationLevel.HIDDEN)
val <T : Configuration> NamedDomainObjectProvider<T>.dependencies
    get() = get().dependencies


/**
 * See [Configuration.getAllDependencies].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().allDependencies"), level = DeprecationLevel.HIDDEN)
val <T : Configuration> NamedDomainObjectProvider<T>.allDependencies
    get() = get().allDependencies


/**
 * See [Configuration.getDependencyConstraints].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().dependencyConstraints"), level = DeprecationLevel.HIDDEN)
val <T : Configuration> NamedDomainObjectProvider<T>.dependencyConstraints
    get() = get().dependencyConstraints


/**
 * See [Configuration.getAllDependencyConstraints].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().allDependencyConstraints"), level = DeprecationLevel.HIDDEN)
val <T : Configuration> NamedDomainObjectProvider<T>.allDependencyConstraints
    get() = get().allDependencyConstraints


/**
 * See [Configuration.getArtifacts].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().artifacts"), level = DeprecationLevel.HIDDEN)
val <T : Configuration> NamedDomainObjectProvider<T>.artifacts
    get() = get().artifacts


/**
 * See [Configuration.getAllArtifacts].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().allArtifacts"), level = DeprecationLevel.HIDDEN)
val <T : Configuration> NamedDomainObjectProvider<T>.allArtifacts
    get() = get().allArtifacts


/**
 * See [Configuration.getExcludeRules].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().excludeRules"), level = DeprecationLevel.HIDDEN)
val <T : Configuration> NamedDomainObjectProvider<T>.excludeRules: Set<ExcludeRule>
    get() = get().excludeRules


/**
 * See [Configuration.exclude].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().exclude(excludeProperties)"), level = DeprecationLevel.HIDDEN)
fun <T : Configuration> NamedDomainObjectProvider<T>.exclude(excludeProperties: Map<String, String>): Configuration =
    get().exclude(excludeProperties)


/**
 * See [Configuration.exclude].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().exclude(excludeProperties)"), level = DeprecationLevel.HIDDEN)
fun <T : Configuration> NamedDomainObjectProvider<T>.exclude(vararg excludeProperties: Pair<String, String>): Configuration =
    get().exclude(excludeProperties.toMap())


/**
 * See [Configuration.defaultDependencies].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().defaultDependencies(action)"), level = DeprecationLevel.HIDDEN)
fun <T : Configuration> NamedDomainObjectProvider<T>.defaultDependencies(action: DependencySet.() -> Unit) =
    get().defaultDependencies(action)


/**
 * See [Configuration.withDependencies].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().withDependencies(action)"), level = DeprecationLevel.HIDDEN)
fun <T : Configuration> NamedDomainObjectProvider<T>.withDependencies(action: DependencySet.() -> Unit) =
    get().withDependencies(action)


/**
 * See [Configuration.all].
 */
@Suppress("DEPRECATION")
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().all"), level = DeprecationLevel.HIDDEN)
val <T : Configuration> NamedDomainObjectProvider<T>.all: Set<Configuration>
    get() = get().all


/**
 * See [Configuration.getIncoming].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().incoming"), level = DeprecationLevel.HIDDEN)
val <T : Configuration> NamedDomainObjectProvider<T>.incoming
    get() = get().incoming


/**
 * See [Configuration.getOutgoing].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().outgoing"), level = DeprecationLevel.HIDDEN)
val <T : Configuration> NamedDomainObjectProvider<T>.outgoing
    get() = get().outgoing


/**
 * See [Configuration.outgoing].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().outgoing(action)"), level = DeprecationLevel.HIDDEN)
fun <T : Configuration> NamedDomainObjectProvider<T>.outgoing(action: ConfigurationPublications.() -> Unit) =
    get().outgoing(action)


/**
 * See [Configuration.copy].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().copy()"), level = DeprecationLevel.HIDDEN)
fun <T : Configuration> NamedDomainObjectProvider<T>.copy() =
    get().copy()


/**
 * See [Configuration.copyRecursive].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().copyRecursive()"), level = DeprecationLevel.HIDDEN)
fun <T : Configuration> NamedDomainObjectProvider<T>.copyRecursive() =
    get().copyRecursive()


/**
 * See [Configuration.copy].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().copy(dependencySpec)"), level = DeprecationLevel.HIDDEN)
fun <T : Configuration> NamedDomainObjectProvider<T>.copy(dependencySpec: Spec<Dependency>) =
    get().copy(dependencySpec)


/**
 * See [Configuration.copyRecursive].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().copyRecursive(dependencySpec)"), level = DeprecationLevel.HIDDEN)
fun <T : Configuration> NamedDomainObjectProvider<T>.copyRecursive(dependencySpec: Spec<Dependency>) =
    get().copyRecursive(dependencySpec)


/**
 * See [Configuration.isCanBeConsumed].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().isCanBeConsumed"), level = DeprecationLevel.HIDDEN)
var <T : Configuration> NamedDomainObjectProvider<T>.isCanBeConsumed
    get() = get().isCanBeConsumed
    set(value) {
        get().isCanBeConsumed = value
    }


/**
 * See [Configuration.isCanBeResolved].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().isCanBeResolved"), level = DeprecationLevel.HIDDEN)
var <T : Configuration> NamedDomainObjectProvider<T>.isCanBeResolved
    get() = get().isCanBeResolved
    set(value) {
        get().isCanBeResolved = value
    }


/**
 * See [Configuration.getAttributes].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().attributes"), level = DeprecationLevel.HIDDEN)
val <T : Configuration> NamedDomainObjectProvider<T>.attributes
    get() = get().attributes


/**
 * See [Configuration.attributes].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().attributes(action)"), level = DeprecationLevel.HIDDEN)
fun <T : Configuration> NamedDomainObjectProvider<T>.attributes(action: AttributeContainer.() -> Unit) =
    get().attributes(action)


/**
 * See [Configuration.addToAntBuilder].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().addToAntBuilder(builder, nodeName, type)"), level = DeprecationLevel.HIDDEN)
fun <T : Configuration> NamedDomainObjectProvider<T>.addToAntBuilder(builder: Any, nodeName: String, type: FileCollection.AntType): Unit =
    get().addToAntBuilder(builder, nodeName, type)


/**
 * See [Configuration.addToAntBuilder].
 */
@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().addToAntBuilder(builder, nodeName)"), level = DeprecationLevel.HIDDEN)
fun <T : Configuration> NamedDomainObjectProvider<T>.addToAntBuilder(builder: Any, nodeName: String): Any =
    get().addToAntBuilder(builder, nodeName)


private
const val deprecationMessage = "Scheduled to be removed in Gradle 9.0"
