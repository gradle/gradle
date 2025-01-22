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

package org.gradle.internal.serialize.codecs.core

import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.DependencyConstraintSet
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler
import org.gradle.api.artifacts.dsl.DependencyConstraintHandler
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.DependencyLockingHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.artifacts.type.ArtifactTypeContainer
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.project.IsolatedProject
import org.gradle.api.publish.Publication
import org.gradle.api.services.BuildService
import org.gradle.api.services.internal.BuildServiceProvider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskDependency
import org.gradle.internal.configuration.problems.DocumentationSection
import org.gradle.internal.event.AbstractBroadcastDispatch
import org.gradle.internal.event.ListenerBroadcast
import org.gradle.internal.flow.services.BuildWorkResultProvider
import org.gradle.internal.scripts.GradleScript
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.IsolateContext
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.codecs.BindingsBuilder
import org.gradle.internal.serialize.graph.logUnsupported
import org.gradle.internal.serialize.graph.unsupported
import org.gradle.internal.service.ServiceLookup
import java.io.FileDescriptor
import java.io.RandomAccessFile
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executor
import java.util.concurrent.ThreadFactory


fun BindingsBuilder.unsupportedTypes() {

    // Live JVM state
    bind(unsupported<ClassLoader>())
    bind(unsupported<Thread>())
    bind(unsupported<ThreadFactory>())
    bind(unsupported<Executor>())
    bind(unsupported<FileDescriptor>())
    bind(unsupported<RandomAccessFile>())
    bind(unsupported<Socket>())
    bind(unsupported<ServerSocket>())

    // Gradle Scripts
    bind(unsupported<GradleScript>(" Gradle script object references"))

    // Gradle Build Model
    bind(unsupported<Gradle>())
    bind(unsupported<Settings>())
    bind(unsupported<Project>())
    bind(unsupported<IsolatedProject>())
    bind(unsupported<TaskContainer>())
    bind(unsupported<TaskDependency>())
    bind(unsupported<SourceSetContainer>())
    bind(unsupported<SourceSet>())

    // Dependency Resolution Types
    bind(unsupported<ConfigurationContainer>())
    bind(unsupported<ResolutionStrategy>())
    bind(unsupported<ResolvableDependencies>())
    bind(unsupported<ResolutionResult>())
    bind(unsupported<DependencyConstraintSet>())
    bind(unsupported<RepositoryHandler>())
    bind(unsupported<DependencyHandler>())
    bind(unsupported<DependencyConstraintHandler>())
    bind(unsupported<ComponentMetadataHandler>())
    bind(unsupported<ArtifactTypeContainer>())
    bind(unsupported<DependencySet>())
    bind(unsupported<DependencyLockingHandler>())
    bind(unsupported<ArtifactView>())

    // Publishing types
    bind(unsupported<Publication>())

    // Event dispatching infrastructure types
    bind(unsupported<ListenerBroadcast<*>>())
    bind(unsupported<AbstractBroadcastDispatch<*>>())

    // Direct build service references
    // Build services must always be referenced via their providers.
    bind(unsupported<BuildService<*>>())

    // Gradle implementation types
    bind(unsupported<ServiceLookup>())
}


object UnsupportedFingerprintBuildServiceProviderCodec : Codec<BuildServiceProvider<*, *>> {
    override suspend fun WriteContext.encode(value: BuildServiceProvider<*, *>) {
        logUnsupported(
            "serialize",
            documentationSection = DocumentationSection.NotYetImplementedBuildServiceInFingerprint
        ) {
            text(" BuildServiceProvider of service ")
            reference(value.serviceDetails.implementationType)
            text(" with name ")
            reference(value.serviceDetails.name)
            text(" used at configuration time")
        }
    }

    override suspend fun ReadContext.decode(): BuildServiceProvider<*, *>? {
        logUnsupported("deserialize", BuildServiceProvider::class, documentationSection = DocumentationSection.NotYetImplementedBuildServiceInFingerprint)
        return null
    }
}


object UnsupportedFingerprintFlowProviders : Codec<BuildWorkResultProvider> {
    override suspend fun WriteContext.encode(value: BuildWorkResultProvider) {
        logUnsupported("serialize")
    }

    override suspend fun ReadContext.decode(): BuildWorkResultProvider? {
        logUnsupported("deserialize")
        return null
    }

    private
    fun IsolateContext.logUnsupported(action: String) {
        logUnsupported(action) {
            text(" ")
            reference(BuildWorkResultProvider::class)
            text(" used at configuration time")
        }
    }
}
