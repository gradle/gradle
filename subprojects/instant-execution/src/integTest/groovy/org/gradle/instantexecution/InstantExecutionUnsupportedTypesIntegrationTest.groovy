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
                private ${baseType} badReference
            }

            class SomeTask extends DefaultTask {
                private final ${baseType} badReference
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
        instantRun "broken"

        then:
        outputContains("""
            2 instant execution problems were found, 2 of which seem unique:
              - field 'badReference' from type 'SomeTask': cannot serialize object of type '${concreteType}', a subtype of '${baseType}', as these are not supported with instant execution.
              - field 'badReference' from type 'SomeBean': cannot serialize object of type '${concreteType}', a subtype of '${baseType}', as these are not supported with instant execution.
            See the complete report at
        """.stripIndent())

        when:
        instantRun "broken"

        then:
        outputContains("this.reference = null")
        outputContains("bean.reference = null")

        where:
        concreteType                               | baseType                            | reference
        // Live JVM state
        ScriptClassLoader.name                     | ClassLoader.name                    | "getClass().classLoader"
        Thread.name                                | Thread.name                         | "Thread.currentThread()"
        DefaultThreadFactory.name                  | ThreadFactory.name                  | "java.util.concurrent.Executors.defaultThreadFactory()"
        FinalizableDelegatedExecutorService.name   | Executor.name                       | "java.util.concurrent.Executors.newSingleThreadExecutor().tap { shutdown() }"
        ByteArrayInputStream.name                  | InputStream.name                    | "new java.io.ByteArrayInputStream([] as byte[])"
        ByteArrayOutputStream.name                 | OutputStream.name                   | "new java.io.ByteArrayOutputStream()"
        FileDescriptor.name                        | FileDescriptor.name                 | "FileDescriptor.in"
        RandomAccessFile.name                      | RandomAccessFile.name               | "new RandomAccessFile(project.file('some').tap { text = '' }, 'r').tap { close() }"
        Socket.name                                | Socket.name                         | "new java.net.Socket()"
        ServerSocket.name                          | ServerSocket.name                   | "new java.net.ServerSocket(0).tap { close() }"
        // Gradle Build Model
        DefaultGradle.name                         | Gradle.name                         | "project.gradle"
        DefaultSettings.name                       | Settings.name                       | "project.gradle.settings"
        DefaultProject.name                        | Project.name                        | "project"
        DefaultTaskContainer.name                  | TaskContainer.name                  | "project.tasks"
        DefaultTask.name                           | Task.name                           | "project.tasks.other"
        DefaultSourceSetContainer.name             | SourceSetContainer.name             | "project.sourceSets"
        DefaultSourceSet.name                      | SourceSet.name                      | "project.sourceSets['main']"
        // Dependency Resolution Services
        DefaultConfigurationContainer.name         | ConfigurationContainer.name         | "project.configurations"
        DefaultResolutionStrategy.name             | ResolutionStrategy.name             | "project.configurations.maybeCreate('some').resolutionStrategy"
        ErrorHandlingResolvedConfiguration.name    | ResolvedConfiguration.name          | "project.configurations.maybeCreate('some').resolvedConfiguration"
        ErrorHandlingLenientConfiguration.name     | LenientConfiguration.name           | "project.configurations.maybeCreate('some').resolvedConfiguration.lenientConfiguration"
        DefaultDependencyConstraintSet.name        | DependencyConstraintSet.name        | "project.configurations.maybeCreate('some').dependencyConstraints"
        DefaultRepositoryHandler.name              | RepositoryHandler.name              | "project.repositories"
        DefaultMavenArtifactRepository.name        | ArtifactRepository.name             | "project.repositories.mavenCentral()"
        DefaultDependencyHandler.name              | DependencyHandler.name              | "project.dependencies"
        DefaultDependencyConstraintHandler.name    | DependencyConstraintHandler.name    | "project.dependencies.constraints"
        DefaultComponentMetadataHandler.name       | ComponentMetadataHandler.name       | "project.dependencies.components"
        DefaultComponentModuleMetadataHandler.name | ComponentModuleMetadataHandler.name | "project.dependencies.modules"
        DefaultAttributesSchema.name               | AttributesSchema.name               | "project.dependencies.attributesSchema"
        DefaultAttributeMatchingStrategy.name      | AttributeMatchingStrategy.name      | "project.dependencies.attributesSchema.attribute(Usage.USAGE_ATTRIBUTE)"
        DefaultCompatibilityRuleChain.name         | CompatibilityRuleChain.name         | "project.dependencies.attributesSchema.attribute(Usage.USAGE_ATTRIBUTE).compatibilityRules"
        DefaultDisambiguationRuleChain.name        | DisambiguationRuleChain.name        | "project.dependencies.attributesSchema.attribute(Usage.USAGE_ATTRIBUTE).disambiguationRules"
        DefaultArtifactResolutionQuery.name        | ArtifactResolutionQuery.name        | "project.dependencies.createArtifactResolutionQuery()"
        DefaultArtifactTypeContainer.name          | ArtifactTypeContainer.name          | "project.dependencies.artifactTypes"
        DefaultDependencySet.name                  | DependencySet.name                  | "project.configurations.maybeCreate('some').dependencies"
        DefaultExternalModuleDependency.name       | Dependency.name                     | "project.dependencies.create('junit:junit:4.12')"
        DefaultDependencyLockingHandler.name       | DependencyLockingHandler.name       | "project.dependencyLocking"
    }
}
