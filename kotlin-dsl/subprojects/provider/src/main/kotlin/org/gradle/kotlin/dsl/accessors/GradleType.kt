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

package org.gradle.kotlin.dsl.accessors

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.ConfigurablePublishArtifact
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.dsl.ArtifactHandler
import org.gradle.api.artifacts.dsl.DependencyConstraintHandler
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.tasks.TaskProvider

import org.gradle.kotlin.dsl.support.bytecode.genericTypeOf
import org.gradle.kotlin.dsl.support.bytecode.internalName


internal
object GradleType {

    val artifactHandler = classOf<ArtifactHandler>()

    val configurablePublishArtifact = classOf<ConfigurablePublishArtifact>()

    val dependencyConstraintHandler = classOf<DependencyConstraintHandler>()

    val dependencyConstraint = classOf<DependencyConstraint>()

    val dependencyHandler = classOf<DependencyHandler>()

    val dependency = classOf<Dependency>()

    val externalModuleDependency = classOf<ExternalModuleDependency>()

    val namedDomainObjectContainer = classOf<NamedDomainObjectContainer<*>>()

    val configuration = classOf<Configuration>()

    val namedDomainObjectProvider = classOf<NamedDomainObjectProvider<*>>()

    val containerOfConfiguration = genericTypeOf(namedDomainObjectContainer, configuration)

    val providerOfConfiguration = genericTypeOf(namedDomainObjectProvider, configuration)

    val publishArtifact = classOf<PublishArtifact>()
}


internal
object GradleTypeName {

    val action = Action::class.internalName

    val artifactHandler = ArtifactHandler::class.internalName

    val externalModuleDependency = ExternalModuleDependency::class.internalName

    val dependencyHandler = DependencyHandler::class.internalName

    val dependencyConstraintHandler = DependencyConstraintHandler::class.internalName

    val extensionAware = ExtensionAware::class.internalName

    val extensionContainer = ExtensionContainer::class.internalName

    val namedDomainObjectProvider = NamedDomainObjectProvider::class.internalName

    val taskProvider = TaskProvider::class.internalName

    val namedWithTypeMethodDescriptor = "(Ljava/lang/String;Ljava/lang/Class;)L$namedDomainObjectProvider;"

    val namedTaskWithTypeMethodDescriptor = "(Ljava/lang/String;Ljava/lang/Class;)L$taskProvider;"
}
