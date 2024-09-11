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
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ModuleDependencyCapabilitiesHandler
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.artifacts.MutableVersionConstraint
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.capabilities.Capability


/**
 * Facilitates the implementation of the [ClientModule] interface by delegation via subclassing.
 */
@Suppress("DEPRECATION")
@Deprecated("Will be removed in Gradle 9.0")
abstract class ClientModuleDelegate : org.gradle.api.artifacts.ClientModule {

    internal
    abstract val delegate: org.gradle.api.artifacts.ClientModule

    override fun getGroup(): String =
        /** Because this also implements [ModuleVersionSelector.getGroup] it must not return `null` */
        (delegate as Dependency).group ?: ""

    override fun addDependency(dependency: ModuleDependency) =
        delegate.addDependency(dependency)

    override fun getName(): String =
        delegate.name

    override fun getExcludeRules(): MutableSet<ExcludeRule> =
        delegate.excludeRules

    override fun addArtifact(artifact: DependencyArtifact): ModuleDependency =
        delegate.addArtifact(artifact)

    override fun artifact(configureClosure: Closure<Any>): DependencyArtifact =
        delegate.artifact(configureClosure)

    override fun artifact(configureAction: Action<in DependencyArtifact>): DependencyArtifact =
        delegate.artifact(configureAction)

    override fun getAttributes(): AttributeContainer =
        delegate.attributes

    override fun capabilities(configureAction: Action<in ModuleDependencyCapabilitiesHandler>): ModuleDependency =
        delegate.capabilities(configureAction)

    override fun version(configureAction: Action<in MutableVersionConstraint>) =
        delegate.version(configureAction)

    override fun getId(): String =
        delegate.id

    override fun getTargetConfiguration(): String? =
        delegate.targetConfiguration

    override fun copy(): org.gradle.api.artifacts.ClientModule =
        delegate.copy()

    override fun attributes(configureAction: Action<in AttributeContainer>): ModuleDependency =
        delegate.attributes(configureAction)

    override fun matchesStrictly(identifier: ModuleVersionIdentifier): Boolean =
        delegate.matchesStrictly(identifier)

    override fun isChanging(): Boolean =
        delegate.isChanging

    override fun getVersion(): String? =
        delegate.version

    override fun isTransitive(): Boolean =
        delegate.isTransitive

    override fun setTransitive(transitive: Boolean): ModuleDependency =
        delegate.setTransitive(transitive)

    @Deprecated("Deprecated in Java", ReplaceWith("this.equals(dependency)"))
    override fun contentEquals(dependency: Dependency): Boolean =
        delegate.contentEquals(dependency)

    override fun getRequestedCapabilities(): MutableList<Capability> =
        delegate.requestedCapabilities

    override fun getVersionConstraint(): VersionConstraint =
        delegate.versionConstraint

    override fun getModule(): ModuleIdentifier =
        delegate.module

    override fun getArtifacts(): MutableSet<DependencyArtifact> =
        delegate.artifacts

    override fun setTargetConfiguration(name: String?) {
        delegate.targetConfiguration = name
    }

    override fun setChanging(changing: Boolean): ExternalModuleDependency =
        delegate.setChanging(changing)

    override fun isForce(): Boolean =
        delegate.isForce

    override fun getDependencies(): MutableSet<ModuleDependency> =
        delegate.dependencies

    override fun because(reason: String?) =
        delegate.because(reason)

    override fun exclude(excludeProperties: Map<String, String>): ModuleDependency =
        delegate.exclude(excludeProperties)

    override fun getReason(): String? =
        delegate.reason

    override fun endorseStrictVersions() =
        delegate.endorseStrictVersions()

    override fun doNotEndorseStrictVersions() =
        delegate.doNotEndorseStrictVersions()

    override fun isEndorsingStrictVersions() =
        delegate.isEndorsingStrictVersions
}
