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

package org.gradle.configurationcache

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencyConstraintSet
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.LenientConfiguration
import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler
import org.gradle.api.artifacts.dsl.ComponentModuleMetadataHandler
import org.gradle.api.artifacts.dsl.DependencyConstraintHandler
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.DependencyLockingHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.query.ArtifactResolutionQuery
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.result.ArtifactResolutionResult
import org.gradle.api.artifacts.result.ArtifactResult
import org.gradle.api.artifacts.result.ComponentArtifactsResult
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.artifacts.result.UnresolvedComponentResult
import org.gradle.api.artifacts.type.ArtifactTypeContainer
import org.gradle.api.attributes.AttributeMatchingStrategy
import org.gradle.api.attributes.AttributesSchema
import org.gradle.api.attributes.CompatibilityRuleChain
import org.gradle.api.attributes.DisambiguationRuleChain
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.artifacts.DefaultDependencyConstraintSet
import org.gradle.api.internal.artifacts.DefaultDependencySet
import org.gradle.api.internal.artifacts.DefaultResolvedDependency
import org.gradle.api.internal.artifacts.PreResolvedResolvableArtifact
import org.gradle.api.internal.artifacts.configurations.DefaultConfiguration.ConfigurationResolvableDependencies
import org.gradle.api.internal.artifacts.configurations.DefaultConfiguration.ConfigurationResolvableDependencies.ConfigurationArtifactView
import org.gradle.api.internal.artifacts.configurations.DefaultConfiguration.ConfigurationResolvableDependencies.LenientResolutionResult
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer
import org.gradle.api.internal.artifacts.configurations.DefaultLegacyConfiguration
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
import org.gradle.api.internal.artifacts.result.DefaultArtifactResolutionResult
import org.gradle.api.internal.artifacts.result.DefaultComponentArtifactsResult
import org.gradle.api.internal.artifacts.result.DefaultResolvedArtifactResult
import org.gradle.api.internal.artifacts.result.DefaultUnresolvedComponentResult
import org.gradle.api.internal.artifacts.type.DefaultArtifactTypeContainer
import org.gradle.api.internal.attributes.DefaultAttributeMatchingStrategy
import org.gradle.api.internal.attributes.DefaultAttributesSchema
import org.gradle.api.internal.attributes.DefaultCompatibilityRuleChain
import org.gradle.api.internal.attributes.DefaultDisambiguationRuleChain
import org.gradle.api.internal.file.DefaultSourceDirectorySet
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.DefaultSourceSet
import org.gradle.api.internal.tasks.DefaultSourceSetContainer
import org.gradle.api.internal.tasks.DefaultTaskContainer
import org.gradle.api.invocation.Gradle
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.groovy.scripts.internal.DefaultScriptCompilationHandler.ScriptClassLoader
import org.gradle.initialization.DefaultSettings
import org.gradle.internal.locking.DefaultDependencyLockingHandler
import org.gradle.invocation.DefaultGradle
import spock.lang.Shared

import java.util.concurrent.Executor
import java.util.concurrent.Executors.DefaultThreadFactory
import java.util.concurrent.Executors.FinalizableDelegatedExecutorService
import java.util.concurrent.ThreadFactory

class ConfigurationCacheUnsupportedTypesIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    @Shared
    private def disallowedServiceTypesAtExecution = [Project, ProjectInternal, Gradle, GradleInternal]

    def "reports in task when injected service of #serviceType accessed at execution time"() {
        given:
        buildFile << """
            abstract class Foo extends DefaultTask {

                @Inject
                public abstract ${serviceType.name} getInjected()

                @TaskAction
                void action() {
                    println(getInjected())
                }
            }

            tasks.register('foo', Foo)
        """

        when:
        configurationCacheRunLenient "foo"

        then:
        problems.assertResultHasProblems(result) {
            withTotalProblemsCount(1)
            withUniqueProblems(
                "Build file 'build.gradle': line 9: accessing non-serializable type '${serviceType.name}'"
            )
        }

        where:
        serviceType << disallowedServiceTypesAtExecution
    }

    def "reports in plugin when service of #serviceType accessed at execution time"() {
        given:
        buildFile << """
            abstract class MyPlugin implements Plugin<Project> {

                @Inject
                public abstract ${serviceType.name} getInjected()

                void apply(Project target) {
                    registerTask(this, target)
                }

                private void registerTask(MyPlugin plugin, Project project) {
                    project.tasks.register("foo") {
                        doFirst {
                            println(plugin.getInjected())
                        }
                    }
                }
            }

            apply plugin: MyPlugin
        """

        when:
        configurationCacheRunLenient "foo"

        then:
        problems.assertResultHasProblems(result) {
            withTotalProblemsCount(1)
            withUniqueProblems(
                "Build file 'build.gradle': line 14: accessing non-serializable type '${serviceType.name}'"
            )
        }

        where:
        serviceType << disallowedServiceTypesAtExecution
    }


    def "reports when task field references an object of type #baseType"() {
        buildFile << """
            plugins { id "java" }

            abstract class SomeBuildService implements $BuildService.name<${BuildServiceParameters.name}.None> {
            }

            class SomeBean {
                private ${baseType.name} badReference
            }

            class SomeTask extends DefaultTask {
                private final ${baseType.name} badReference
                private final bean = new SomeBean()
                private final beanWithSameType = new SomeBean()

                SomeTask() {
                    badReference = ${reference}
                    bean.badReference = ${reference}
                    beanWithSameType.badReference = ${reference}
                }

                @TaskAction
                void run() {
                    println "this.reference = " + badReference?.toString()
                    println "bean.reference = " + bean.badReference?.toString()
                    println "beanWithSameType.reference = " + beanWithSameType.badReference?.toString()
                }
            }

            ${mavenCentralRepository()}

            task other
            task broken(type: SomeTask)
        """

        when:
        configurationCacheRunLenient "broken"

        then:
        problems.assertResultHasProblems(result) {
            withTotalProblemsCount(6)
            withUniqueProblems(
                "Task `:broken` of type `SomeTask`: cannot deserialize object of type '${baseType.name}' as these are not supported with the configuration cache.",
                "Task `:broken` of type `SomeTask`: cannot serialize object of type '$concreteTypeName', a subtype of '${baseType.name}', as these are not supported with the configuration cache."
            )
            withProblemsWithStackTraceCount(0)
        }

        and:
        outputContains("this.reference = null")
        outputContains("bean.reference = null")
        outputContains("beanWithSameType.reference = null")

        when:
        configurationCacheRunLenient "broken"

        then:
        problems.assertResultHasProblems(result) {
            withTotalProblemsCount(3)
            withUniqueProblems(
                "Task `:broken` of type `SomeTask`: cannot deserialize object of type '${baseType.name}' as these are not supported with the configuration cache."
            )
            withProblemsWithStackTraceCount(0)
        }

        and:
        outputContains("this.reference = null")
        outputContains("bean.reference = null")
        outputContains("beanWithSameType.reference = null")

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
        // Dependency Resolution Types
        DefaultConfigurationContainer         | ConfigurationContainer         | "project.configurations"
        DefaultResolutionStrategy             | ResolutionStrategy             | "project.configurations.maybeCreate('some').resolutionStrategy"
        ErrorHandlingResolvedConfiguration    | ResolvedConfiguration          | "project.configurations.maybeCreate('some').resolvedConfiguration"
        ErrorHandlingLenientConfiguration     | LenientConfiguration           | "project.configurations.maybeCreate('some').resolvedConfiguration.lenientConfiguration"
        ConfigurationResolvableDependencies   | ResolvableDependencies         | "project.configurations.maybeCreate('some').incoming"
        LenientResolutionResult               | ResolutionResult               | "project.configurations.maybeCreate('some').incoming.resolutionResult"
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
        DefaultExternalModuleDependency       | Dependency                     | "project.dependencies.create('junit:junit:4.13')"
        DefaultDependencyLockingHandler       | DependencyLockingHandler       | "project.dependencyLocking"
        DefaultResolvedDependency             | ResolvedDependency             | "project.configurations.create(java.util.UUID.randomUUID().toString()).tap { project.dependencies.add(name, 'junit:junit:4.13') }.resolvedConfiguration.firstLevelModuleDependencies.first()"
        PreResolvedResolvableArtifact         | ResolvedArtifact               | "project.configurations.create(java.util.UUID.randomUUID().toString()).tap { project.dependencies.add(name, 'junit:junit:4.13') }.resolvedConfiguration.resolvedArtifacts.first()"
        ConfigurationArtifactView             | ArtifactView                   | "project.configurations.maybeCreate('some').incoming.artifactView {}"
        DefaultArtifactResolutionResult       | ArtifactResolutionResult       | "project.dependencies.createArtifactResolutionQuery().forModule('junit', 'junit', '4.13').withArtifacts(JvmLibrary).execute()"
        DefaultComponentArtifactsResult       | ComponentArtifactsResult       | "project.dependencies.createArtifactResolutionQuery().forModule('junit', 'junit', '4.13').withArtifacts(JvmLibrary).execute().components.first()"
        DefaultUnresolvedComponentResult      | UnresolvedComponentResult      | "project.dependencies.createArtifactResolutionQuery().forModule('junit', 'junit', '100').withArtifacts(JvmLibrary).execute().components.first()"
        DefaultResolvedArtifactResult         | ArtifactResult                 | "project.dependencies.createArtifactResolutionQuery().forModule('junit', 'junit', '4.13').withArtifacts(JvmLibrary, SourcesArtifact).execute().components.first().getArtifacts(SourcesArtifact).first()"

        // direct BuildService reference, build services must always be referenced via their providers
        'SomeBuildService'                    | BuildService                   | "project.gradle.sharedServices.registerIfAbsent('service', SomeBuildService) {}.get()"

        concreteTypeName = concreteType instanceof Class ? concreteType.name : concreteType
    }

    def "reports when task field is declared with type #baseType"() {
        buildFile << """
            plugins { id "java" }

            class SomeBean {
                private ${baseType.name} badField
            }

            class SomeTask extends DefaultTask {
                private final ${baseType.name} badField
                private final bean = new SomeBean()
                private final beanWithSameType = new SomeBean()

                SomeTask() {
                    badField = ${reference}
                    bean.badField = ${reference}
                    beanWithSameType.badField = ${reference}
                }

                @TaskAction
                void run() {
                    println "this.reference = " + badField
                    println "bean.reference = " + bean.badField
                    println "beanWithSameType.reference = " + beanWithSameType.badField
                }
            }

            ${mavenCentralRepository()}

            task other
            task broken(type: SomeTask)
        """

        when:
        configurationCacheRunLenient "broken"

        then:
        problems.assertResultHasProblems(result) {
            withTotalProblemsCount(9)
            withUniqueProblems(
                "Task `:broken` of type `SomeTask`: cannot deserialize object of type '${baseType.name}' as these are not supported with the configuration cache.",
                "Task `:broken` of type `SomeTask`: cannot serialize object of type '${concreteType.name}', a subtype of '${baseType.name}', as these are not supported with the configuration cache.",
                "Task `:broken` of type `SomeTask`: value '$deserializedValue' is not assignable to '${baseType.name}'"
            )
            withProblemsWithStackTraceCount(0)
        }

        when:
        configurationCacheRunLenient "broken"

        and:
        outputContains("this.reference = null")
        outputContains("bean.reference = null")
        outputContains("beanWithSameType.reference = null")

        then:
        problems.assertResultHasProblems(result) {
            withTotalProblemsCount(6)
            withUniqueProblems(
                "Task `:broken` of type `SomeTask`: cannot deserialize object of type '${baseType.name}' as these are not supported with the configuration cache.",
                "Task `:broken` of type `SomeTask`: value '$deserializedValue' is not assignable to '${baseType.name}'"
            )
            withProblemsWithStackTraceCount(0)
        }

        and:
        outputContains("this.reference = null")
        outputContains("bean.reference = null")
        outputContains("beanWithSameType.reference = null")

        where:
        concreteType               | baseType           | reference                                            | deserializedValue
        DefaultLegacyConfiguration | Configuration      | "project.configurations.maybeCreate('some')"         | 'file collection'
        DefaultSourceDirectorySet  | SourceDirectorySet | "project.objects.sourceDirectorySet('some', 'more')" | 'file tree'
    }
}
