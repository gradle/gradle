/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.instantexecution

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencyConstraintSet
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.LenientConfiguration
import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler
import org.gradle.api.artifacts.dsl.ComponentModuleMetadataHandler
import org.gradle.api.artifacts.dsl.DependencyConstraintHandler
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.DependencyLockingHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.query.ArtifactResolutionQuery
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.type.ArtifactTypeContainer
import org.gradle.api.attributes.AttributeMatchingStrategy
import org.gradle.api.attributes.AttributesSchema
import org.gradle.api.attributes.CompatibilityRuleChain
import org.gradle.api.attributes.DisambiguationRuleChain
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.artifacts.DefaultDependencyConstraintSet
import org.gradle.api.internal.artifacts.DefaultDependencySet
import org.gradle.api.internal.artifacts.configurations.DefaultConfiguration
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.internal.artifacts.dsl.DefaultComponentMetadataHandler
import org.gradle.api.internal.artifacts.dsl.DefaultComponentModuleMetadataHandler
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyConstraintHandler
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyHandler
import org.gradle.api.internal.artifacts.ivyservice.ErrorHandlingConfigurationResolver.ErrorHandlingLenientConfiguration
import org.gradle.api.internal.artifacts.ivyservice.ErrorHandlingConfigurationResolver.ErrorHandlingResolvedConfiguration
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultResolutionStrategy
import org.gradle.api.internal.artifacts.query.DefaultArtifactResolutionQuery
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository
import org.gradle.api.internal.artifacts.type.DefaultArtifactTypeContainer
import org.gradle.api.internal.attributes.DefaultAttributeMatchingStrategy
import org.gradle.api.internal.attributes.DefaultAttributesSchema
import org.gradle.api.internal.attributes.DefaultCompatibilityRuleChain
import org.gradle.api.internal.attributes.DefaultDisambiguationRuleChain
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.internal.tasks.DefaultSourceSet
import org.gradle.api.internal.tasks.DefaultSourceSetContainer
import org.gradle.api.internal.tasks.DefaultTaskContainer
import org.gradle.api.invocation.Gradle
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.groovy.scripts.internal.DefaultScriptCompilationHandler.ScriptClassLoader
import org.gradle.initialization.DefaultSettings
import org.gradle.internal.locking.DefaultDependencyLockingHandler
import org.gradle.invocation.DefaultGradle
import spock.lang.Unroll

import java.util.concurrent.Executor
import java.util.concurrent.Executors.DefaultThreadFactory
import java.util.concurrent.Executors.FinalizableDelegatedExecutorService
import java.util.concurrent.ThreadFactory


class InstantExecutionUnsupportedTypesIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    @Unroll
    def "warns when task field references an object of type #baseType"() {
        buildFile << """
            plugins { id "java" }

            class SomeBean {
                private ${baseType.name} badReference
            }

            class SomeTask extends DefaultTask {
                private final ${baseType.name} badReference
                private final bean = new SomeBean()

                SomeTask() {
                    badReference = ${reference}
                    bean.badReference = ${reference}
                }

                @TaskAction
                void run() {
                    println "this.reference = " + badReference
                    println "bean.reference = " + bean.badReference
                }
            }

            task other
            task broken(type: SomeTask)
        """

        when:
        problems.withDoNotFailOnProblems()
        instantRun "broken"

        then:
        problems.assertResultHasProblems(result) {
            withUniqueProblems(
                "field 'badReference' from type 'SomeTask': cannot serialize object of type '${concreteType.name}', a subtype of '${baseType.name}', as these are not supported with instant execution.",
                "field 'badReference' from type 'SomeBean': cannot serialize object of type '${concreteType.name}', a subtype of '${baseType.name}', as these are not supported with instant execution."
            )
        }

        when:
        instantRun "broken"

        then:
        outputContains("this.reference = null")
        outputContains("bean.reference = null")

        where:
        concreteType                          | baseType                       | reference
        // Live JVM state
        ScriptClassLoader                     | ClassLoader                    | "getClass().classLoader"
        Thread                                | Thread                         | "Thread.currentThread()"
        DefaultThreadFactory                  | ThreadFactory                  | "java.util.concurrent.Executors.defaultThreadFactory()"
        FinalizableDelegatedExecutorService   | Executor                       | "java.util.concurrent.Executors.newSingleThreadExecutor().tap { shutdown() }"
        ByteArrayInputStream                  | InputStream                    | "new java.io.ByteArrayInputStream([] as byte[])"
        ByteArrayOutputStream                 | OutputStream                   | "new java.io.ByteArrayOutputStream()"
        FileDescriptor                        | FileDescriptor                 | "FileDescriptor.in"
        RandomAccessFile                      | RandomAccessFile               | "new RandomAccessFile(project.file('some').tap { text = '' }, 'r').tap { close() }"
        Socket                                | Socket                         | "new java.net.Socket()"
        ServerSocket                          | ServerSocket                   | "new java.net.ServerSocket(0).tap { close() }"
        // Gradle Build Model
        DefaultGradle                         | Gradle                         | "project.gradle"
        DefaultSettings                       | Settings                       | "project.gradle.settings"
        DefaultProject                        | Project                        | "project"
        DefaultTaskContainer                  | TaskContainer                  | "project.tasks"
        DefaultTask                           | Task                           | "project.tasks.other"
        DefaultSourceSetContainer             | SourceSetContainer             | "project.sourceSets"
        DefaultSourceSet                      | SourceSet                      | "project.sourceSets['main']"
        // Dependency Resolution Services
        DefaultConfigurationContainer         | ConfigurationContainer         | "project.configurations"
        DefaultConfiguration                  | Configuration                  | "project.configurations.maybeCreate('some')"
        DefaultResolutionStrategy             | ResolutionStrategy             | "project.configurations.maybeCreate('some').resolutionStrategy"
        ErrorHandlingResolvedConfiguration    | ResolvedConfiguration          | "project.configurations.maybeCreate('some').resolvedConfiguration"
        ErrorHandlingLenientConfiguration     | LenientConfiguration           | "project.configurations.maybeCreate('some').resolvedConfiguration.lenientConfiguration"
        DefaultDependencyConstraintSet        | DependencyConstraintSet        | "project.configurations.maybeCreate('some').dependencyConstraints"
        DefaultRepositoryHandler              | RepositoryHandler              | "project.repositories"
        DefaultMavenArtifactRepository        | ArtifactRepository             | "project.repositories.mavenCentral()"
        DefaultDependencyHandler              | DependencyHandler              | "project.dependencies"
        DefaultDependencyConstraintHandler    | DependencyConstraintHandler    | "project.dependencies.constraints"
        DefaultComponentMetadataHandler       | ComponentMetadataHandler       | "project.dependencies.components"
        DefaultComponentModuleMetadataHandler | ComponentModuleMetadataHandler | "project.dependencies.modules"
        DefaultAttributesSchema               | AttributesSchema               | "project.dependencies.attributesSchema"
        DefaultAttributeMatchingStrategy      | AttributeMatchingStrategy      | "project.dependencies.attributesSchema.attribute(Usage.USAGE_ATTRIBUTE)"
        DefaultCompatibilityRuleChain         | CompatibilityRuleChain         | "project.dependencies.attributesSchema.attribute(Usage.USAGE_ATTRIBUTE).compatibilityRules"
        DefaultDisambiguationRuleChain        | DisambiguationRuleChain        | "project.dependencies.attributesSchema.attribute(Usage.USAGE_ATTRIBUTE).disambiguationRules"
        DefaultArtifactResolutionQuery        | ArtifactResolutionQuery        | "project.dependencies.createArtifactResolutionQuery()"
        DefaultArtifactTypeContainer          | ArtifactTypeContainer          | "project.dependencies.artifactTypes"
        DefaultDependencySet                  | DependencySet                  | "project.configurations.maybeCreate('some').dependencies"
        DefaultExternalModuleDependency       | Dependency                     | "project.dependencies.create('junit:junit:4.12')"
        DefaultDependencyLockingHandler       | DependencyLockingHandler       | "project.dependencyLocking"
    }
}
