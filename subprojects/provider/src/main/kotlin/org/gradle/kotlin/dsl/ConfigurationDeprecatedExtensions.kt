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
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskDependency

import java.io.File


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().buildDependencies"))
val <T : Configuration> NamedDomainObjectProvider<T>.buildDependencies: TaskDependency
    get() = get().buildDependencies


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().singleFile"))
val <T : Configuration> NamedDomainObjectProvider<T>.singleFile: File
    get() = get().singleFile


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().files"))
val <T : Configuration> NamedDomainObjectProvider<T>.files: Set<File>
    get() = get().files


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().contains(element)"))
fun <T : Configuration> NamedDomainObjectProvider<T>.contains(element: File): Boolean =
    get().contains(element)


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().asPath"))
val <T : Configuration> NamedDomainObjectProvider<T>.asPath: String
    get() = get().asPath


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().plus(collection)"))
operator fun <T : Configuration> NamedDomainObjectProvider<T>.plus(collection: FileCollection): FileCollection =
    get().plus(collection)


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().plus(configuration)"))
operator fun <T : Configuration> FileCollection.plus(configuration: NamedDomainObjectProvider<T>): FileCollection =
    plus(configuration.get())


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().minus(collection)"))
operator fun <T : Configuration> NamedDomainObjectProvider<T>.minus(collection: FileCollection): FileCollection =
    get().minus(collection)


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().minus(configuration)"))
operator fun <T : Configuration> FileCollection.minus(configuration: NamedDomainObjectProvider<T>): FileCollection =
    minus(configuration.get())


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().filter(filterSpec"))
fun <T : Configuration> NamedDomainObjectProvider<T>.filter(filterSpec: Spec<File>) =
    get().filter(filterSpec)


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().isEmpty"))
val <T : Configuration> NamedDomainObjectProvider<T>.isEmpty: Boolean
    get() = get().isEmpty


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().asFileTree"))
val <T : Configuration> NamedDomainObjectProvider<T>.asFileTree: FileTree
    get() = get().asFileTree


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().resolutionStrategy"))
val <T : Configuration> NamedDomainObjectProvider<T>.resolutionStrategy
    get() = get().resolutionStrategy


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().state"))
val <T : Configuration> NamedDomainObjectProvider<T>.state
    get() = get().state


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().isVisible"))
var <T : Configuration> NamedDomainObjectProvider<T>.isVisible
    get() = get().isVisible
    set(value) {
        get().isVisible = value
    }


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().extendsFrom"))
val <T : Configuration> NamedDomainObjectProvider<T>.extendsFrom: Set<Configuration>
    get() = get().extendsFrom


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().setExtendsFrom(superConfigs)"))
fun <T : Configuration> NamedDomainObjectProvider<T>.setExtendsFrom(superConfigs: Iterable<Configuration>): Configuration =
    get().setExtendsFrom(superConfigs)


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().extendsFrom(superConfigs)"))
fun <T : Configuration> NamedDomainObjectProvider<T>.extendsFrom(vararg superConfigs: Configuration) =
    get().extendsFrom(*superConfigs)


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().isTransitive"))
val <T : Configuration> NamedDomainObjectProvider<T>.isTransitive
    get() = get().isTransitive


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().setTransitive(t)"))
fun <T : Configuration> NamedDomainObjectProvider<T>.setTransitive(t: Boolean): Configuration =
    get().setTransitive(t)


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().description"))
val <T : Configuration> NamedDomainObjectProvider<T>.description
    get() = get().description


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().setDescription(description)"))
fun <T : Configuration> NamedDomainObjectProvider<T>.setDescription(description: String?): Configuration =
    get().setDescription(description)


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().hierarchy"))
val <T : Configuration> NamedDomainObjectProvider<T>.hierarchy: Set<Configuration>
    get() = get().hierarchy


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().resolve()"))
fun <T : Configuration> NamedDomainObjectProvider<T>.resolve(): Set<File> =
    get().resolve()


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().files(dependencySpec)"))
fun <T : Configuration> NamedDomainObjectProvider<T>.files(dependencySpec: Spec<Dependency>): Set<File> =
    get().files(dependencySpec)


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().files(dependencies)"))
fun <T : Configuration> NamedDomainObjectProvider<T>.files(vararg dependencies: Dependency): Set<File> =
    get().files(*dependencies)


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().fileCollection(dependencySpec)"))
fun <T : Configuration> NamedDomainObjectProvider<T>.fileCollection(dependencySpec: Spec<Dependency>): FileCollection =
    get().fileCollection(dependencySpec)


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().fileCollection(dependencies)"))
fun <T : Configuration> NamedDomainObjectProvider<T>.fileCollection(vararg dependencies: Dependency): FileCollection =
    get().fileCollection(*dependencies)


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().resolvedConfiguration"))
val <T : Configuration> NamedDomainObjectProvider<T>.resolvedConfiguration
    get() = get().resolvedConfiguration


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().uploadTaskName"))
val <T : Configuration> NamedDomainObjectProvider<T>.uploadTaskName
    get() = get().uploadTaskName


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().getTaskDependencyFromProjectDependency(useDependedOn, taskName)"))
fun <T : Configuration> NamedDomainObjectProvider<T>.getTaskDependencyFromProjectDependency(useDependedOn: Boolean, taskName: String): TaskDependency =
    get().getTaskDependencyFromProjectDependency(useDependedOn, taskName)


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().dependencies"))
val <T : Configuration> NamedDomainObjectProvider<T>.dependencies
    get() = get().dependencies


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().allDependencies"))
val <T : Configuration> NamedDomainObjectProvider<T>.allDependencies
    get() = get().allDependencies


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().dependencyConstraints"))
val <T : Configuration> NamedDomainObjectProvider<T>.dependencyConstraints
    get() = get().dependencyConstraints


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().allDependencyConstraints"))
val <T : Configuration> NamedDomainObjectProvider<T>.allDependencyConstraints
    get() = get().allDependencyConstraints


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().artifacts"))
val <T : Configuration> NamedDomainObjectProvider<T>.artifacts
    get() = get().artifacts


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().allArtifacts"))
val <T : Configuration> NamedDomainObjectProvider<T>.allArtifacts
    get() = get().allArtifacts


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().excludeRules"))
val <T : Configuration> NamedDomainObjectProvider<T>.excludeRules: Set<ExcludeRule>
    get() = get().excludeRules


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().exclude(excludeProperties)"))
fun <T : Configuration> NamedDomainObjectProvider<T>.exclude(excludeProperties: Map<String, String>): Configuration =
    get().exclude(excludeProperties)


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().exclude(excludeProperties)"))
fun <T : Configuration> NamedDomainObjectProvider<T>.exclude(vararg excludeProperties: Pair<String, String>): Configuration =
    get().exclude(excludeProperties.toMap())


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().defaultDependencies(action)"))
fun <T : Configuration> NamedDomainObjectProvider<T>.defaultDependencies(action: DependencySet.() -> Unit) =
    get().defaultDependencies(action)


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().withDependencies(action)"))
fun <T : Configuration> NamedDomainObjectProvider<T>.withDependencies(action: DependencySet.() -> Unit) =
    get().defaultDependencies(action)


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().all"))
val <T : Configuration> NamedDomainObjectProvider<T>.all: Set<Configuration>
    get() = get().all


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().incoming"))
val <T : Configuration> NamedDomainObjectProvider<T>.incoming
    get() = get().incoming


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().outgoing"))
val <T : Configuration> NamedDomainObjectProvider<T>.outgoing
    get() = get().outgoing


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().outgoing(action)"))
fun <T : Configuration> NamedDomainObjectProvider<T>.outgoing(action: ConfigurationPublications.() -> Unit) =
    get().outgoing(action)


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().copy()"))
fun <T : Configuration> NamedDomainObjectProvider<T>.copy() =
    get().copy()


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().copyRecursive()"))
fun <T : Configuration> NamedDomainObjectProvider<T>.copyRecursive() =
    get().copyRecursive()


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().copy(dependencySpec)"))
fun <T : Configuration> NamedDomainObjectProvider<T>.copy(dependencySpec: Spec<Dependency>) =
    get().copy(dependencySpec)


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().copyRecursive(dependencySpec)"))
fun <T : Configuration> NamedDomainObjectProvider<T>.copyRecursive(dependencySpec: Spec<Dependency>) =
    get().copyRecursive(dependencySpec)


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().isCanBeConsumed"))
var <T : Configuration> NamedDomainObjectProvider<T>.isCanBeConsumed
    get() = get().isCanBeConsumed
    set(value) {
        get().isCanBeConsumed = value
    }


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().isCanBeResolved"))
var <T : Configuration> NamedDomainObjectProvider<T>.isCanBeResolved
    get() = get().isCanBeResolved
    set(value) {
        get().isCanBeResolved = value
    }


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().attributes"))
val <T : Configuration> NamedDomainObjectProvider<T>.attributes
    get() = get().attributes


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().attributes(action)"))
fun <T : Configuration> NamedDomainObjectProvider<T>.attributes(action: AttributeContainer.() -> Unit) =
    get().attributes(action)


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().addToAntBuilder(builder, nodeName, type)"))
fun <T : Configuration> NamedDomainObjectProvider<T>.addToAntBuilder(builder: Any, nodeName: String, type: FileCollection.AntType): Unit =
    get().addToAntBuilder(builder, nodeName, type)


@Deprecated(deprecationMessage, replaceWith = ReplaceWith("get().addToAntBuilder(builder, nodeName)"))
fun <T : Configuration> NamedDomainObjectProvider<T>.addToAntBuilder(builder: Any, nodeName: String): Any =
    get().addToAntBuilder(builder, nodeName)


private
const val deprecationMessage = "Scheduled to be removed in Gradle 6.0"
