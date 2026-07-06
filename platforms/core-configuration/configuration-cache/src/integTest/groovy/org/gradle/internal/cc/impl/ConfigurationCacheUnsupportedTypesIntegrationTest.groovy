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

package org.gradle.internal.cc.impl

import org.gradle.api.DefaultTask
import org.gradle.api.JavaVersion
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
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact
import org.gradle.api.internal.artifacts.DefaultResolvedDependency
import org.gradle.api.internal.artifacts.configurations.DefaultConfiguration.ConfigurationResolvableDependencies
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer
import org.gradle.api.internal.artifacts.configurations.DefaultLegacyConfiguration
import org.gradle.api.internal.artifacts.configurations.DefaultResolvableConfiguration
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.internal.artifacts.dsl.DefaultComponentMetadataHandler
import org.gradle.api.internal.artifacts.dsl.DefaultComponentModuleMetadataHandler
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyConstraintHandler
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyHandler
import org.gradle.api.internal.artifacts.ivyservice.DefaultResolvedConfiguration
import org.gradle.api.internal.artifacts.ivyservice.ShortCircuitingResolutionExecutor.EmptyLenientConfiguration
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultResolutionStrategy
import org.gradle.api.internal.artifacts.query.DefaultArtifactResolutionQuery
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository
import org.gradle.api.internal.artifacts.resolver.DefaultResolutionOutputs.DefaultArtifactView
import org.gradle.api.internal.artifacts.type.DefaultArtifactTypeContainer
import org.gradle.api.internal.attributes.DefaultAttributeMatchingStrategy
import org.gradle.api.internal.attributes.DefaultAttributesSchema
import org.gradle.api.internal.attributes.DefaultCompatibilityRuleChain
import org.gradle.api.internal.attributes.DefaultDisambiguationRuleChain
import org.gradle.api.internal.file.DefaultSourceDirectorySet
import org.gradle.api.internal.project.DefaultIsolatedProject
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.DefaultSourceSet
import org.gradle.api.internal.tasks.DefaultSourceSetContainer
import org.gradle.api.internal.tasks.DefaultTaskContainer
import org.gradle.api.invocation.Gradle
import org.gradle.api.project.IsolatedProject
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.groovy.scripts.internal.DefaultScriptCompilationHandler.ScriptClassLoader
import org.gradle.initialization.DefaultSettings
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.locking.DefaultDependencyLockingHandler
import org.gradle.invocation.DefaultGradle
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.JdkVersionTestPreconditions

import spock.lang.Issue
import spock.lang.Shared

import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Exchanger
import java.util.concurrent.Executor
import java.util.concurrent.Executors.DefaultThreadFactory
import java.util.concurrent.Phaser
import java.util.concurrent.Semaphore
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock

class ConfigurationCacheUnsupportedTypesIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def configurationCache = newConfigurationCacheFixture()

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
            totalProblemsCount = 1
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
            totalProblemsCount = 1
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
            totalProblemsCount = 6
            withUniqueProblems(
                "Task `:broken` of type `SomeTask`: cannot deserialize object of type '${baseType.name}' as these are not supported with the configuration cache.",
                "Task `:broken` of type `SomeTask`: cannot serialize object of type '$concreteTypeName', a subtype of '${baseType.name}', as these are not supported with the configuration cache."
            )
            problemsWithStackTraceCount = 0
        }

        and:
        outputContains("this.reference = null")
        outputContains("bean.reference = null")
        outputContains("beanWithSameType.reference = null")

        when:
        configurationCacheRunLenient "broken"

        then:
        problems.assertResultHasProblems(result) {
            totalProblemsCount = 3
            withUniqueProblems(
                "Task `:broken` of type `SomeTask`: cannot deserialize object of type '${baseType.name}' as these are not supported with the configuration cache."
            )
            problemsWithStackTraceCount = 0
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
        executorServiceTypeOnCurrentJvm()     | Executor                       | "java.util.concurrent.Executors.newSingleThreadExecutor().tap { shutdown() }"
        // Concurrency primitives
        ReentrantLock                         | Lock                           | "new java.util.concurrent.locks.ReentrantLock()"
        ReentrantReadWriteLock                | ReadWriteLock                  | "new java.util.concurrent.locks.ReentrantReadWriteLock()"
        CountDownLatch                        | CountDownLatch                 | "new java.util.concurrent.CountDownLatch(1)"
        CyclicBarrier                         | CyclicBarrier                  | "new java.util.concurrent.CyclicBarrier(1)"
        Phaser                                | Phaser                         | "new java.util.concurrent.Phaser()"
        Semaphore                             | Semaphore                      | "new java.util.concurrent.Semaphore(1)"
        Exchanger                             | Exchanger                      | "new java.util.concurrent.Exchanger()"
        SynchronousQueue                      | SynchronousQueue               | "new java.util.concurrent.SynchronousQueue()"
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
        DefaultIsolatedProject                | IsolatedProject                | "project.isolated"
        DefaultTaskContainer                  | TaskContainer                  | "project.tasks"
        DefaultTask                           | Task                           | "project.tasks.other"
        DefaultSourceSetContainer             | SourceSetContainer             | "project.sourceSets"
        DefaultSourceSet                      | SourceSet                      | "project.sourceSets['main']"
        // Dependency Resolution Types
        DefaultConfigurationContainer         | ConfigurationContainer         | "project.configurations"
        DefaultResolutionStrategy             | ResolutionStrategy             | "project.configurations.maybeCreate('some').resolutionStrategy"
        DefaultResolvedConfiguration          | ResolvedConfiguration          | "project.configurations.maybeCreate('some').resolvedConfiguration"
        EmptyLenientConfiguration             | LenientConfiguration           | "project.configurations.maybeCreate('some').resolvedConfiguration.lenientConfiguration"
        ConfigurationResolvableDependencies   | ResolvableDependencies         | "project.configurations.maybeCreate('some').incoming"
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
        DefaultResolvedArtifact               | ResolvedArtifact               | "project.configurations.create(java.util.UUID.randomUUID().toString()).tap { project.dependencies.add(name, 'junit:junit:4.13') }.resolvedConfiguration.resolvedArtifacts.first()"
        DefaultArtifactView                   | ArtifactView                   | "project.configurations.maybeCreate('some').incoming.artifactView {}"

        // direct BuildService reference, build services must always be referenced via their providers
        'SomeBuildService'                    | BuildService                   | "project.gradle.sharedServices.registerIfAbsent('service', SomeBuildService) {}.get()"

        concreteTypeName = concreteType instanceof Class ? concreteType.name : concreteType
    }

    private static executorServiceTypeOnCurrentJvm() {
        def shortName = Jvm.current().javaVersion.isCompatibleWith(JavaVersion.VERSION_21) ? 'AutoShutdownDelegatedExecutorService' : 'FinalizableDelegatedExecutorService'
        return 'java.util.concurrent.Executors$' + shortName
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
                    ${creator}
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
        if (failsAtStore) {
            configurationCacheFails "broken"
        } else {
            configurationCacheRunLenient "broken"
        }

        then:
        if (failsAtStore) {
            // WideningCodec-driven check rejects the incompatible round-trip at store time.
            // See assertHasUnsupportedPropertyValueType for context on why the check uses
            // outputContains rather than assertHasCause / assertHasResolution.
            failure.assertOutputContains(
                "Cannot serialize value of type ${concreteType.name}_Decorated into field badField of SomeTask in task :broken of type SomeTask."
            )
            failure.assertOutputContains(
                "Values of this type are restored from the configuration cache as ${decodedTypeName}, which cannot be assigned to a field of type ${baseType.name}."
            )
        } else {
            // No WideningCodec fires for this type; the only signal is the read-time
            // type-assignment check that rejects the decoded value on load.
            problems.assertResultHasProblems(result) {
                totalProblemsCount = 3
                withUniqueProblems(
                    "Task `:broken` of type `SomeTask`: value '$deserializedValue' is not assignable to '${baseType.name}'"
                )
                problemsWithStackTraceCount = 0
            }
        }

        when:
        if (!failsAtStore) {
            configurationCacheRunLenient "broken"
        }

        and:
        if (!failsAtStore) {
            outputContains("this.reference = null")
            outputContains("bean.reference = null")
            outputContains("beanWithSameType.reference = null")
        }

        then:
        if (!failsAtStore) {
            problems.assertResultHasProblems(result) {
                totalProblemsCount = 3
                withUniqueProblems(
                    "Task `:broken` of type `SomeTask`: value '$deserializedValue' is not assignable to '${baseType.name}'"
                )
                problemsWithStackTraceCount = 0
            }
        }

        where:
        concreteType                   | baseType           | creator                                     | reference                                            | deserializedValue | failsAtStore | decodedTypeName                      | resolution
        DefaultLegacyConfiguration     | Configuration      | "project.configurations.create('some')"     | "project.configurations.getByName('some')"           | 'file collection' | true         | 'org.gradle.api.file.FileCollection' | 'Use a ConfigurableFileCollection instead, or change the captured type to FileCollection.'
        DefaultResolvableConfiguration | Configuration      | "project.configurations.resolvable('some')" | "project.configurations.getByName('some')"           | 'file collection' | true         | 'org.gradle.api.file.FileCollection' | 'Use a ConfigurableFileCollection instead, or change the captured type to FileCollection.'
        DefaultSourceDirectorySet      | SourceDirectorySet | ""                                          | "project.objects.sourceDirectorySet('some', 'more')" | 'file tree'       | true         | 'org.gradle.api.file.FileTree'       | 'Use a ConfigurableFileCollection or ConfigurableFileTree instead.'
    }

    def "tolerates incompatible roundtrip field of type #baseType in warn mode"() {
        buildFile << """
            plugins { id "java" }

            class SomeTask extends DefaultTask {
                private final ${baseType.name} badField

                SomeTask() {
                    ${creator}
                    badField = ${reference}
                }

                @TaskAction
                void run() {
                    println "this.reference = " + badField
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
            totalProblemsCount = 1
            withUniqueProblems(
                "Task `:broken` of type `SomeTask`: failed to serialize value of field 'badField' of task ':broken' of type 'SomeTask'"
            )
            problemsWithStackTraceCount = 0
        }

        and:
        outputContains("this.reference = null")

        where:
        baseType           | creator                                     | reference
        Configuration      | "project.configurations.create('some')"     | "project.configurations.getByName('some')"
        SourceDirectorySet | ""                                          | "project.objects.sourceDirectorySet('some', 'more')"
    }

    @Requires(JdkVersionTestPreconditions.Jdk14OrLater)
    def "reports when task field references a record containing type #baseType"() {
        file("buildSrc/src/main/java/JavaRecord.java") << """
            public record JavaRecord(${baseType.name} value, int filler) {}
        """
        file("buildSrc/build.gradle.kts") << """
            plugins {
                `java-library`
            }
        """
        buildFile << """
            class SomeBean {
                JavaRecord value
            }

            class SomeTask extends DefaultTask {
                private final SomeBean bean = new SomeBean()
                private final JavaRecord value

                SomeTask() {
                    value = new JavaRecord(${reference}, 101)
                    bean.value = new JavaRecord(${reference}, 202)
                }

                @TaskAction
                void run() {
                    println "this.reference = " + value
                    println "bean.reference = " + bean.value
                }
            }

            task broken(type: SomeTask)
        """

        when:
        configurationCacheRunLenient "broken"

        then:
        problems.assertResultHasProblems(result) {
            totalProblemsCount = 4
            withUniqueProblems(
                "Task `:broken` of type `SomeTask`: cannot deserialize object of type '${baseType.name}' as these are not supported with the configuration cache.",
                "Task `:broken` of type `SomeTask`: cannot serialize object of type '$concreteTypeName', a subtype of '${baseType.name}', as these are not supported with the configuration cache."
            )
            problemsWithStackTraceCount = 0
        }

        and:
        outputContains("this.reference = JavaRecord[value=null, filler=101]")
        outputContains("bean.reference = JavaRecord[value=null, filler=202]")

        when:
        configurationCacheRunLenient "broken"

        then:
        problems.assertResultHasProblems(result) {
            totalProblemsCount = 2
            withUniqueProblems(
                "Task `:broken` of type `SomeTask`: cannot deserialize object of type '${baseType.name}' as these are not supported with the configuration cache."
            )
            problemsWithStackTraceCount = 0
        }

        and:
        outputContains("this.reference = JavaRecord[value=null, filler=101]")
        outputContains("bean.reference = JavaRecord[value=null, filler=202]")

        where:
        concreteType                  | baseType               | reference
        // Live JVM state
        Thread                        | Thread                 | "Thread.currentThread()"
        ByteArrayInputStream          | InputStream            | "new java.io.ByteArrayInputStream([] as byte[])"
        Socket                        | Socket                 | "new java.net.Socket()"
        // Gradle Build Model
        DefaultGradle                 | Gradle                 | "project.gradle"
        // Dependency Resolution Types
        DefaultConfigurationContainer | ConfigurationContainer | "project.configurations"

        concreteTypeName = concreteType instanceof Class ? concreteType.name : concreteType
    }

    @Requires(JdkVersionTestPreconditions.Jdk14OrLater)
    def "reports when task field is declared with record containing type #baseType"() {
        file("buildSrc/src/main/java/JavaRecord.java") << """
            public record JavaRecord(${baseType.name} value, int filler) {}
        """
        file("buildSrc/build.gradle.kts") << """
            plugins {
                `java-library`
            }
        """
        buildFile << """
            class SomeBean {
                JavaRecord value
            }

            class SomeTask extends DefaultTask {
                private final SomeBean bean = new SomeBean()
                private final JavaRecord value

                SomeTask() {
                    ${creator}
                    value = new JavaRecord(${reference}, 101)
                    bean.value = new JavaRecord(${reference}, 202)
                }

                @TaskAction
                void run() {
                    println "this.reference = " + value
                    println "bean.reference = " + bean.value
                }
            }

            task broken(type: SomeTask)
        """

        when:
        configurationCacheRunLenient "broken"

        then:
        problems.assertResultHasProblems(result) {
            totalProblemsCount = 4
            withUniqueProblems(
                "Task `:broken` of type `SomeTask`: cannot serialize object of type '${concreteType.name}', a subtype of '${baseType.name}', as these are not supported with the configuration cache.",
                "Task `:broken` of type `SomeTask`: value '$deserializedValue' is not assignable to '${baseType.name}'"
            )
            problemsWithStackTraceCount = 0
        }

        when:
        configurationCacheRunLenient "broken"

        and:
        outputContains("this.reference = JavaRecord[value=null, filler=101]")
        outputContains("bean.reference = JavaRecord[value=null, filler=202]")

        then:
        problems.assertResultHasProblems(result) {
            totalProblemsCount = 2
            withUniqueProblems(
                "Task `:broken` of type `SomeTask`: value '$deserializedValue' is not assignable to '${baseType.name}'"
            )
            problemsWithStackTraceCount = 0
        }

        and:
        outputContains("this.reference = JavaRecord[value=null, filler=101]")
        outputContains("bean.reference = JavaRecord[value=null, filler=202]")

        where:
        concreteType                   | baseType           | creator                                     | reference                                            | deserializedValue
        DefaultLegacyConfiguration     | Configuration      | "project.configurations.create('some')"     | "project.configurations.getByName('some')"           | 'file collection'
        DefaultResolvableConfiguration | Configuration      | "project.configurations.resolvable('some')" | "project.configurations.getByName('some')"           | 'file collection'
        DefaultSourceDirectorySet      | SourceDirectorySet | ""                                          | "project.objects.sourceDirectorySet('some', 'more')" | 'file tree'
    }

    @Requires(JdkVersionTestPreconditions.KotlinSupportedJdk)
    @Issue("https://github.com/gradle/gradle/issues/16177")
    def "reports when Kotlin #delegateKind delegate wraps an unsupported type, when the type is implicit (#configSource)"() {
        given:
        file("buildSrc/settings.gradle.kts").text = ""
        file("buildSrc/build.gradle.kts").text = """
            plugins { `kotlin-dsl` }
            ${mavenCentralRepository(GradleDsl.KOTLIN)}
        """
        file("buildSrc/src/main/kotlin/BrokenTask.kt").text = """
            import org.gradle.api.DefaultTask
            import org.gradle.api.tasks.Internal
            import org.gradle.api.tasks.TaskAction
            import kotlin.properties.Delegates

            open class BrokenTask : DefaultTask() {
                $delegateDeclaration

                @TaskAction
                fun run() {
                    println("task executed")
                }
            }
        """.stripIndent()
        buildFile << """
            tasks.register("broken", BrokenTask) {
                println("configured classPath type: " + classPath.class.name)
            }
        """

        when:
        configurationCacheFails "broken"

        then: "CC detects the unsupported type inside the delegate with a clear cause"
        // WideningCodec-driven check reports both the user's declared property type
        // (Configuration) and the restored type (FileCollection), so the user can see
        // the mismatch between what they wrote and how it comes back from the cache.
        // See assertHasUnsupportedPropertyValueType for why the check uses
        // outputContains rather than assertHasCause / assertHasResolution.
        failure.assertOutputContains(
            "Cannot serialize $delegateLabel delegate for property 'classPath: Configuration' in task :broken of type BrokenTask."
        )
        failure.assertOutputContains(
            "Values of this type are restored from the configuration cache as org.gradle.api.file.FileCollection, " +
            "which cannot be assigned to a property of type org.gradle.api.artifacts.Configuration."
        )

        where:
        delegateKind | configSource | delegateLabel         | delegateDeclaration
        "lazy"       | "detached"   | "lazy"                | '@get:Internal val classPath by lazy { project.configurations.detachedConfiguration() }'
        "lazy"       | "created"    | "lazy"                | '@get:Internal val classPath by lazy { project.configurations.create("myConf") }'
        "observable" | "detached"   | "observable/vetoable" | '@get:Internal var classPath by Delegates.observable(project.configurations.detachedConfiguration()) { _, _, _ -> }'
        "observable" | "created"    | "observable/vetoable" | '@get:Internal var classPath by Delegates.observable(project.configurations.create("myConf")) { _, _, _ -> }'
        "vetoable"   | "detached"   | "observable/vetoable" | '@get:Internal var classPath by Delegates.vetoable(project.configurations.detachedConfiguration()) { _, _, _ -> true }'
        "vetoable"   | "created"    | "observable/vetoable" | '@get:Internal var classPath by Delegates.vetoable(project.configurations.create("myConf")) { _, _, _ -> true }'
    }

    @Requires(JdkVersionTestPreconditions.KotlinSupportedJdk)
    @Issue("https://github.com/gradle/gradle/issues/16177")
    def "Kotlin #delegateKind delegate with explicit FileCollection type works with configuration cache (#configSource)"() {
        given:
        file("buildSrc/settings.gradle.kts").text = ""
        file("buildSrc/build.gradle.kts").text = """
            plugins { `kotlin-dsl` }
            ${mavenCentralRepository(GradleDsl.KOTLIN)}
        """
        file("buildSrc/src/main/kotlin/WorkingTask.kt").text = """
            import org.gradle.api.DefaultTask
            import org.gradle.api.file.FileCollection
            import org.gradle.api.tasks.Internal
            import org.gradle.api.tasks.TaskAction
            import kotlin.properties.Delegates

            open class WorkingTask : DefaultTask() {
                $delegateDeclaration

                @TaskAction
                fun run() {
                    println("classPath files: " + classPath.files)
                }
            }
        """.stripIndent()
        buildFile << """
            tasks.register("working", WorkingTask) {
                println("configured classPath type: " + classPath.class.name)
            }
        """

        when: "first run stores to configuration cache"
        configurationCacheRun "working"

        then:
        outputContains("classPath files: []")

        when: "second run loads from cache and succeeds"
        configurationCacheRun "working"

        then: "no error because FileCollection checkcast succeeds"
        outputContains("classPath files: []")

        where:
        delegateKind | configSource   | delegateDeclaration
        "lazy"       | "detached"     | '@get:Internal val classPath: FileCollection by lazy { project.configurations.detachedConfiguration() }'
        "lazy"       | "created"      | '@get:Internal val classPath: FileCollection by lazy { project.configurations.create("myConf") }'
        "observable" | "detached"     | '@get:Internal var classPath: FileCollection by Delegates.observable(project.configurations.detachedConfiguration()) { _, _, _ -> }'
        "observable" | "created"      | '@get:Internal var classPath: FileCollection by Delegates.observable(project.configurations.create("myConf")) { _, _, _ -> }'
        "vetoable"   | "detached"     | '@get:Internal var classPath: FileCollection by Delegates.vetoable(project.configurations.detachedConfiguration()) { _, _, _ -> true }'
        "vetoable"   | "created"      | '@get:Internal var classPath: FileCollection by Delegates.vetoable(project.configurations.create("myConf")) { _, _, _ -> true }'
    }

    @Requires(JdkVersionTestPreconditions.KotlinSupportedJdk)
    @Issue("https://github.com/gradle/gradle/issues/16177")
    def "Kotlin #delegateKind fails sensibly with explicit Configuration type with configuration cache (#configSource)"() {
        given:
        file("buildSrc/settings.gradle.kts").text = ""
        file("buildSrc/build.gradle.kts").text = """
            plugins { `kotlin-dsl` }
            ${mavenCentralRepository(GradleDsl.KOTLIN)}
        """
        file("buildSrc/src/main/kotlin/FailingTask.kt").text = """
            import org.gradle.api.DefaultTask
            import org.gradle.api.artifacts.Configuration
            import org.gradle.api.file.FileCollection
            import org.gradle.api.tasks.Internal
            import org.gradle.api.tasks.TaskAction
            import kotlin.properties.Delegates

            open class FailingTask : DefaultTask() {
                $delegateDeclaration

                @TaskAction
                fun run() {
                    println("classPath files: " + classPath.files)
                }
            }
        """.stripIndent()
        buildFile << """
            tasks.register("failing", FailingTask) {
                println("configured classPath type: " + classPath.class.name)
            }
        """

        when:
        configurationCacheFails "failing"

        then:
        // WideningCodec-driven check reports both the user's declared property type
        // (Configuration) and the restored type (FileCollection), so the user can see
        // the mismatch between what they wrote and how it comes back from the cache.
        // See assertHasUnsupportedPropertyValueType for why the check uses
        // outputContains rather than assertHasCause / assertHasResolution.
        failure.assertOutputContains(
            "Cannot serialize $delegateLabel delegate for property 'classPath: Configuration' in task :failing of type FailingTask."
        )
        failure.assertOutputContains(
            "Values of this type are restored from the configuration cache as org.gradle.api.file.FileCollection, " +
            "which cannot be assigned to a property of type org.gradle.api.artifacts.Configuration."
        )

        where:
        delegateKind | configSource | delegateLabel         | delegateDeclaration
        "lazy"       | "detached"   | "lazy"                | '@get:Internal val classPath: Configuration by lazy { project.configurations.detachedConfiguration() }'
        "lazy"       | "created"    | "lazy"                | '@get:Internal val classPath: Configuration by lazy { project.configurations.create("myConf") }'
        "observable" | "detached"   | "observable/vetoable" | '@get:Internal var classPath: Configuration by Delegates.observable(project.configurations.detachedConfiguration()) { _, _, _ -> }'
        "observable" | "created"    | "observable/vetoable" | '@get:Internal var classPath: Configuration by Delegates.observable(project.configurations.create("myConf")) { _, _, _ -> }'
        "vetoable"   | "detached"   | "observable/vetoable" | '@get:Internal var classPath: Configuration by Delegates.vetoable(project.configurations.detachedConfiguration()) { _, _, _ -> true }'
        "vetoable"   | "created"    | "observable/vetoable" | '@get:Internal var classPath: Configuration by Delegates.vetoable(project.configurations.create("myConf")) { _, _, _ -> true }'
    }

    @Requires(JdkVersionTestPreconditions.KotlinSupportedJdk)
    def "warn mode tolerates Kotlin #delegateKind delegate with unsupported type (#configSource)"() {
        // Companion to "Kotlin #delegateKind fails sensibly with explicit Configuration type"
        // above. Verifies that in warn mode the delegate-site widening check emits a deferred
        // problem (with stack trace) and drops the value rather than hard-failing — preserving
        // the same `--configuration-cache-problems=warn` escape hatch the bean-field and
        // managed-property check sites already honor.
        given:
        file("buildSrc/settings.gradle.kts").text = ""
        file("buildSrc/build.gradle.kts").text = """
            plugins { `kotlin-dsl` }
            ${mavenCentralRepository(GradleDsl.KOTLIN)}
        """
        file("buildSrc/src/main/kotlin/LenientTask.kt").text = """
            import org.gradle.api.DefaultTask
            import org.gradle.api.artifacts.Configuration
            import org.gradle.api.tasks.Internal
            import org.gradle.api.tasks.TaskAction
            import kotlin.properties.Delegates

            open class LenientTask : DefaultTask() {
                $delegateDeclaration

                @TaskAction
                fun run() {
                    // Avoid reading classPath here: warn-mode drops the delegate field to null
                    // before tasks execute (the cold-store run already serves from the cached
                    // state), so reading the property would NPE on the load-side null delegate.
                    println("task ran with dropped delegate")
                }
            }
        """.stripIndent()
        buildFile << """
            tasks.register("lenient", LenientTask) {
                // Force the delegate at configuration time so the widening check has a value
                // to inspect; without this, an un-forced lazy would bypass the check entirely
                // (see "uninitialized Kotlin `by lazy` delegate ... bypasses the widening check").
                println("configured classPath type: " + classPath.class.name)
            }
        """

        when:
        configurationCacheRunLenient "lenient"

        then:
        problems.assertResultHasProblems(result) {
            totalProblemsCount = 1
            withUniqueProblems(
                "Task `:lenient` of type `LenientTask`: failed to serialize value of field 'classPath\$delegate' of task ':lenient' of type 'LenientTask'"
            )
            problemsWithStackTraceCount = 0
        }

        and: "the task body ran — the build was not interrupted at store"
        outputContains("task ran with dropped delegate")

        where:
        delegateKind | configSource | delegateDeclaration
        "lazy"       | "detached"   | '@get:Internal val classPath: Configuration by lazy { project.configurations.detachedConfiguration() }'
        "lazy"       | "created"    | '@get:Internal val classPath: Configuration by lazy { project.configurations.create("myConf") }'
        "observable" | "detached"   | '@get:Internal var classPath: Configuration by Delegates.observable(project.configurations.detachedConfiguration()) { _, _, _ -> }'
        "observable" | "created"    | '@get:Internal var classPath: Configuration by Delegates.observable(project.configurations.create("myConf")) { _, _, _ -> }'
        "vetoable"   | "detached"   | '@get:Internal var classPath: Configuration by Delegates.vetoable(project.configurations.detachedConfiguration()) { _, _, _ -> true }'
        "vetoable"   | "created"    | '@get:Internal var classPath: Configuration by Delegates.vetoable(project.configurations.create("myConf")) { _, _, _ -> true }'
    }

    @Requires(JdkVersionTestPreconditions.KotlinSupportedJdk)
    @Issue("https://github.com/gradle/gradle/issues/16177")
    def "Kotlin field declared with Lazy type is not treated as a by-delegate"() {
        // Regression test: classes like org.jetbrains.kotlin.gradle.plugin.SubpluginOption declare
        // regular fields whose type is Lazy<T> (without `by lazy`). Such fields must not be
        // misidentified as Kotlin compiled `$delegate` fields by the CC delegate-inspection logic.
        given:
        file("buildSrc/settings.gradle.kts").text = ""
        file("buildSrc/build.gradle.kts").text = """
            plugins { `kotlin-dsl` }
            ${mavenCentralRepository(GradleDsl.KOTLIN)}
        """
        file("buildSrc/src/main/kotlin/LazyFieldTask.kt").text = """
            import org.gradle.api.DefaultTask
            import org.gradle.api.tasks.Internal
            import org.gradle.api.tasks.TaskAction

            open class LazyFieldTask : DefaultTask() {
                @get:Internal
                val lazyValue: Lazy<String> = lazy { "computed" }

                @TaskAction
                fun run() {
                    println("lazyValue: " + lazyValue.value)
                }
            }
        """.stripIndent()
        buildFile << """
            tasks.register("doLazy", LazyFieldTask)
        """

        when: "first run stores to configuration cache"
        configurationCacheRun "doLazy"

        then:
        outputContains("lazyValue: computed")

        when: "second run loads from cache"
        configurationCacheRun "doLazy"

        then:
        outputContains("lazyValue: computed")
    }

    def "reports sensible error when task has @Internal Property of Configuration"() {
        buildFile << """
            abstract class BrokenPrintTask extends DefaultTask {
                @Internal
                abstract Property<Configuration> getConf()

                @TaskAction
                void run() {
                    println "Files: = " + conf.get().files
                }
            }

            configurations.create('myConf')

            tasks.register("printFiles", BrokenPrintTask) {
                conf.set(configurations.getByName('myConf'))
            }
        """

        when:
        configurationCacheFails "printFiles"

        then:
        assertHasUnsupportedPropertyValueType(Property, Configuration, "printFiles", "BrokenPrintTask")
    }

    def "reports sensible error when concrete task has concrete Property of Configuration"() {
        buildFile << """
            import javax.inject.Inject

            class ConcreteConfTask extends DefaultTask {
                @Internal
                final Property<Configuration> conf

                @Inject
                ConcreteConfTask(ObjectFactory objects) {
                    conf = objects.property(Configuration)
                }

                @TaskAction
                void run() {
                    println "Conf: " + conf.get().name
                }
            }

            configurations.create('myConf')

            tasks.register("concreteConf", ConcreteConfTask) {
                conf.set(configurations.getByName('myConf'))
            }
        """

        when:
        configurationCacheFails "concreteConf"

        then:
        assertHasUnsupportedPropertyValueType(Property, Configuration, "concreteConf", "ConcreteConfTask")
    }

    def "reports sensible error when task has @Internal Property of SourceDirectorySet"() {
        buildFile << """
            abstract class SdsTask extends DefaultTask {
                @Internal
                abstract Property<SourceDirectorySet> getSds()

                @TaskAction
                void run() {
                    println "SDS: " + sds.get().name
                }
            }

            tasks.register("sdsTask", SdsTask) {
                sds.set(objects.sourceDirectorySet('sources', 'source files'))
            }
        """

        when:
        configurationCacheFails "sdsTask"

        then:
        assertHasUnsupportedPropertyValueType(Property, SourceDirectorySet, "sdsTask", "SdsTask")
    }

    def "reports sensible error task indirectly holds a Property of unsupported type"() {
        buildFile << """
            abstract class ConfHolder {
                @Internal
                abstract Property<Configuration> getConf()
            }

            abstract class BeanHolderTask extends DefaultTask {
                @Nested
                abstract ConfHolder getHolder()

                @TaskAction
                void run() {
                    println "Conf: " + holder.conf.get().name
                }
            }

            configurations.create('myConf')

            tasks.register("beanHolder", BeanHolderTask) {
                holder.conf.set(configurations.getByName('myConf'))
            }
        """

        when:
        configurationCacheFails "beanHolder"

        then:
        assertHasUnsupportedPropertyValueType(Property, Configuration, "beanHolder", "BeanHolderTask")
    }

    def "reports sensible error when Property of unsupported type is captured without custom task"() {
        buildFile << """
            interface ConfHolder {
                Property<Configuration> getConf()
            }

            tasks.named("tasks") {
                def holder = objects.newInstance(ConfHolder)
                holder.conf.set(configurations.create('myConf'))

                doLast {
                    println "Conf: " + holder.conf.get().name
                }
            }
        """

        when:
        configurationCacheFails "tasks"

        then:
        assertHasUnsupportedPropertyValueType(Property, Configuration, "tasks", "TaskReportTask")
    }

    def "reports error only once when multiple properties of same unsupported type exist"() {
        buildFile << """
            abstract class MultiConfTask extends DefaultTask {
                @Internal
                abstract Property<Configuration> getFirst()

                @Input
                abstract Property<Configuration> getSecond()

                @InputFiles
                abstract Property<Configuration> getThird()

                @Classpath
                abstract Property<Configuration> getFourth()

                @TaskAction
                void run() {
                    println "First: " + first.get().name
                    println "Second: " + second.get().name
                    println "Third: " + third.get().name
                    println "Fourth: " + fourth.get().name
                }
            }

            configurations.create('confA')
            configurations.create('confB')
            configurations.create('confC')
            configurations.create('confD')

            tasks.register("multiConf", MultiConfTask) {
                first.set(configurations.getByName('confA'))
                second.set(configurations.getByName('confB'))
                third.set(configurations.getByName('confC'))
                fourth.set(configurations.getByName('confD'))
            }
        """

        when:
        configurationCacheFails "multiConf"

        then:
        assertHasUnsupportedPropertyValueType(Property, Configuration, "multiConf", "MultiConfTask")
    }

    def "unset #propertyKind of unsupported type does not cause an error (#description)"() {
        // An unset Property is stored to the cache as a missing marker, so the
        // widening-type check that PropertyCodec would fire on load is skipped.
        // Covers all four ways an unset Property of an unsupported type can enter
        // the store — abstract managed (never accessed), abstract managed (accessed
        // but never set), explicitly constructed via ObjectFactory (never set),
        // and explicitly constructed and read at execution time.
        buildFile << buildScript

        when:
        configurationCacheRun taskName

        then:
        configurationCache.assertStateStored()
        outputContains expectedOutput

        where:
        propertyKind      | description                                                     | taskName          | expectedOutput    | buildScript
        "Property"        | "abstract managed, never accessed"                              | "unsetConf"       | "task ran"        | unsetAbstractPropertyBuildScript()
        "Property"        | "abstract managed, accessed"                                    | "presenceConf"    | "Present: false"  | presenceAbstractPropertyBuildScript()
        "Property"        | "explicit objects.property, never set"                          | "explicitUnset"   | "Present: false"  | unsetExplicitPropertyBuildScript()
        "ListProperty"    | "abstract managed, never accessed"                              | "unsetListConf"   | "task ran"        | unsetAbstractListPropertyBuildScript()
        "SetProperty"     | "abstract managed, never accessed"                              | "unsetSetConf"    | "task ran"        | unsetAbstractSetPropertyBuildScript()
        "MapProperty"     | "abstract managed, unsupported key and value, never accessed"   | "unsetMapConf"    | "task ran"        | unsetAbstractMapPropertyBuildScript()
    }

    private static String unsetAbstractPropertyBuildScript() {
        """
            abstract class UnsetConfTask extends DefaultTask {
                @Internal
                abstract Property<Configuration> getConf()

                @TaskAction
                void run() { println "task ran" }
            }

            tasks.register("unsetConf", UnsetConfTask)
        """
    }

    private static String presenceAbstractPropertyBuildScript() {
        """
            abstract class PresenceConfTask extends DefaultTask {
                @Internal
                abstract Property<Configuration> getConf()

                @TaskAction
                void run() { println "Present: " + conf.isPresent() }
            }

            tasks.register("presenceConf", PresenceConfTask)
        """
    }

    private static String unsetExplicitPropertyBuildScript() {
        """
            import javax.inject.Inject

            abstract class ExplicitUnsetConfTask extends DefaultTask {
                @Internal
                final Property<Configuration> conf

                @Inject
                ExplicitUnsetConfTask(ObjectFactory objects) {
                    conf = objects.property(Configuration)
                }

                @TaskAction
                void run() { println "Present: " + conf.isPresent() }
            }

            tasks.register("explicitUnset", ExplicitUnsetConfTask)
        """
    }

    private static String unsetAbstractListPropertyBuildScript() {
        """
            abstract class UnsetListConfTask extends DefaultTask {
                @Internal
                abstract ListProperty<Configuration> getConfs()

                @TaskAction
                void run() { println "task ran" }
            }

            tasks.register("unsetListConf", UnsetListConfTask)
        """
    }

    private static String unsetAbstractSetPropertyBuildScript() {
        """
            abstract class UnsetSetConfTask extends DefaultTask {
                @Internal
                abstract SetProperty<Configuration> getConfs()

                @TaskAction
                void run() { println "task ran" }
            }

            tasks.register("unsetSetConf", UnsetSetConfTask)
        """
    }

    private static String unsetAbstractMapPropertyBuildScript() {
        """
            abstract class UnsetMapConfTask extends DefaultTask {
                @Internal
                abstract MapProperty<Configuration, SourceDirectorySet> getConfs()

                @TaskAction
                void run() { println "task ran" }
            }

            tasks.register("unsetMapConf", UnsetMapConfTask)
        """
    }

    def "following resolution advice fixes Property of #typeName"() {
        def buildScript = { String propertyDeclaration, String wiring ->
            """
            abstract class PrintFiles extends DefaultTask {
                ${propertyDeclaration}

                @TaskAction
                void run() {
                    println "task ran"
                }
            }

            ${sourceSetup}

            tasks.register("printFiles", PrintFiles) {
                ${wiring}
            }
            """
        }

        given: "a broken build using Property<$typeName>"
        buildFile << buildScript(
            "@Internal abstract Property<${typeName}> getInputFiles()",
            "inputFiles.set(${sourceExpression})"
        )

        when: "configuration cache store fails"
        configurationCacheFails "printFiles"

        then: "error identifies the problem and suggests a fix"
        assertHasUnsupportedPropertyValueType(Property, unsupportedType, "printFiles", "PrintFiles")

        when: "the property type is changed following the resolution advice"
        buildFile.text = buildScript(
            "@InputFiles abstract ConfigurableFileCollection getInputFiles()",
            "inputFiles.from(${sourceExpression})"
        )

        and: "configuration cache stores successfully"
        configurationCacheRun "printFiles"

        then:
        configurationCache.assertStateStored()

        when: "configuration cache loads successfully"
        configurationCacheRun "printFiles"

        then:
        configurationCache.assertStateLoaded()

        where:
        typeName             | unsupportedType    | sourceSetup                                                                                    | sourceExpression
        "Configuration"      | Configuration      | "configurations.create('myConf')"                                                              | "configurations.getByName('myConf')"
        "SourceDirectorySet" | SourceDirectorySet | "def sds = objects.sourceDirectorySet('sources', 'source files')\nsds.srcDir('src/main/java')" | "sds"
    }

    // region Collection Properties with unsupported element types
    def "reports sensible error when task has #propertyKind.simpleName of unsupported #typeSimpleName"() {
        buildFile << buildScript

        when:
        configurationCacheFails taskName

        then:
        assertHasUnsupportedPropertyValueType(propertyKind, unsupportedType, taskName, taskType, mapKeyOrValue)

        where:
        propertyKind | unsupportedType | typeSimpleName  | taskName         | taskType            | mapKeyOrValue | buildScript
        ListProperty | Configuration   | "Configuration" | "listConfTask"   | "ListConfTask"      | null          | listPropertyBuildScript()
        SetProperty  | Configuration   | "Configuration" | "setConfTask"    | "SetConfTask"       | null          | setPropertyBuildScript()
        MapProperty  | Configuration   | "Configuration" | "mapConfTask"    | "MapConfTask"       | "value"       | mapPropertyValueBuildScript()
        MapProperty  | Configuration   | "Configuration" | "mapKeyConfTask" | "MapKeyConfTask"    | "key"         | mapPropertyKeyBuildScript()
    }

    private static String listPropertyBuildScript() {
        """
            abstract class ListConfTask extends DefaultTask {
                @Internal
                abstract ListProperty<Configuration> getConfs()

                @TaskAction
                void run() { println "Confs: " + confs.getOrElse([])*.name }
            }

            configurations.create('myConf')

            tasks.register("listConfTask", ListConfTask) {
                confs.add(configurations.getByName('myConf'))
            }
        """
    }

    private static String setPropertyBuildScript() {
        """
            abstract class SetConfTask extends DefaultTask {
                @Internal
                abstract SetProperty<Configuration> getConfs()

                @TaskAction
                void run() { println "Confs: " + confs.getOrElse([])*.name }
            }

            configurations.create('myConf')

            tasks.register("setConfTask", SetConfTask) {
                confs.add(configurations.getByName('myConf'))
            }
        """
    }

    private static String mapPropertyValueBuildScript() {
        """
            abstract class MapConfTask extends DefaultTask {
                @Internal
                abstract MapProperty<String, Configuration> getConfs()

                @TaskAction
                void run() { println "Confs: " + confs.getOrElse([:]) }
            }

            configurations.create('myConf')

            tasks.register("mapConfTask", MapConfTask) {
                confs.put("main", configurations.getByName('myConf'))
            }
        """
    }

    private static String mapPropertyKeyBuildScript() {
        """
            abstract class MapKeyConfTask extends DefaultTask {
                @Internal
                abstract MapProperty<Configuration, String> getConfs()

                @TaskAction
                void run() { println "Confs: " + confs.getOrElse([:]) }
            }

            configurations.create('myConf')

            tasks.register("mapKeyConfTask", MapKeyConfTask) {
                confs.put(configurations.getByName('myConf'), "main")
            }
        """
    }
    // endregion Collection Properties with unsupported element types

    def "warn mode tolerates #description with unsupported value type"() {
        buildFile << buildScript

        when:
        configurationCacheRunLenient taskName

        then:
        problems.assertResultHasProblems(result) {
            totalProblemsCount = 1
            withUniqueProblems(uniqueProblem)
            problemsWithStackTraceCount = 0
        }

        and:
        outputContains expectedOutput

        where:
        description                              | taskName         | buildScript                          | uniqueProblem                                                                                                                                                   | expectedOutput
        "Property<Configuration>"                | "printFiles"     | propertyOfConfigurationBuildScript() | "Task `:printFiles` of type `PrintFiles`: failed to serialize value of field '__conf__' of task ':printFiles' of type 'PrintFiles'"                            | "Present: false"
        "Property<SourceDirectorySet>"           | "sdsTask"        | propertyOfSdsBuildScript()           | "Task `:sdsTask` of type `SdsTask`: failed to serialize value of field '__sds__' of task ':sdsTask' of type 'SdsTask'"                                         | "Present: false"
        "ListProperty<Configuration>"            | "listConfTask"   | listPropertyBuildScript()            | "Task `:listConfTask` of type `ListConfTask`: failed to serialize value of field '__confs__' of task ':listConfTask' of type 'ListConfTask'"                   | "Confs: []"
        "SetProperty<Configuration>"             | "setConfTask"    | setPropertyBuildScript()             | "Task `:setConfTask` of type `SetConfTask`: failed to serialize value of field '__confs__' of task ':setConfTask' of type 'SetConfTask'"                       | "Confs: []"
        "MapProperty<String, Configuration>"     | "mapConfTask"    | mapPropertyValueBuildScript()        | "Task `:mapConfTask` of type `MapConfTask`: failed to serialize value of field '__confs__' of task ':mapConfTask' of type 'MapConfTask'"                       | "Confs: [:]"
        "MapProperty<Configuration, String>"     | "mapKeyConfTask" | mapPropertyKeyBuildScript()          | "Task `:mapKeyConfTask` of type `MapKeyConfTask`: failed to serialize value of field '__confs__' of task ':mapKeyConfTask' of type 'MapKeyConfTask'"           | "Confs: [:]"
    }

    def "MapProperty with both key and value of unsupported types reports a problem for each"() {
        // Distinct key (Configuration) and value (SourceDirectorySet) types — the build
        // script exercises both code paths by construction. Since the widening
        // details are now folded into the problem StructuredMessage, the KEY-side
        // and VALUE-side problems render as distinct messages (each names its own
        // type), so both survive dedup — no longer a single deduped problem.
        //
        // The console-summary extractor cannot parse the multi-line problem
        // messages, so the assertion targets the raw output for each per-type
        // summary line — this gives a defence-in-depth against a regression that
        // drops one of the two checks.
        buildFile << """
            abstract class MapKeyValueConfTask extends DefaultTask {
                @Internal
                abstract MapProperty<Configuration, SourceDirectorySet> getConfs()

                @TaskAction
                void run() { println "Confs: " + confs.getOrElse([:]) }
            }

            configurations.create('keyConf')

            tasks.register("mapKeyValueConfTask", MapKeyValueConfTask) {
                confs.put(configurations.getByName('keyConf'), objects.sourceDirectorySet('sources', 'source files'))
            }
        """

        when:
        configurationCacheRunLenient "mapKeyValueConfTask"

        then: "each of the two widening checks emits a distinct problem"
        outputContains(
            "Cannot serialize MapProperty<Configuration> in task :mapKeyValueConfTask of type MapKeyValueConfTask."
        )
        outputContains(
            "Cannot serialize MapProperty<SourceDirectorySet> in task :mapKeyValueConfTask of type MapKeyValueConfTask."
        )

        and:
        outputContains "Confs: [:]"
    }

    private static String propertyOfConfigurationBuildScript() {
        """
            abstract class PrintFiles extends DefaultTask {
                @Internal
                abstract Property<Configuration> getConf()

                @TaskAction
                void run() { println "Present: " + conf.isPresent() }
            }

            configurations.create('myConf')

            tasks.register("printFiles", PrintFiles) {
                conf.set(configurations.getByName('myConf'))
            }
        """
    }

    private static String propertyOfSdsBuildScript() {
        """
            abstract class SdsTask extends DefaultTask {
                @Internal
                abstract Property<SourceDirectorySet> getSds()

                @TaskAction
                void run() { println "Present: " + sds.isPresent() }
            }

            tasks.register("sdsTask", SdsTask) {
                sds.set(objects.sourceDirectorySet('sources', 'source files'))
            }
        """
    }

    private void assertHasUnsupportedPropertyValueType(Class<?> propertyKind, Class<?> unsupportedType, String taskPath, String taskType, String mapKeyOrValue = null) {
        // With the widening problem's exception no longer attached to the deferred
        // PropertyProblem, the summary + details surface only through the CC
        // problem message (visible in the CC-problems summary section of the build
        // output and the HTML report), not through the failure's cause chain. Check
        // the console output for the folded-in summary and details lines.
        failure.assertOutputContains(
            "Cannot serialize ${propertyKind.simpleName}<${unsupportedType.simpleName}> in task :${taskPath} of type ${taskType}."
        )
        failure.assertOutputContains(
            "The value type of this property (${unsupportedType.name}) is not supported with the configuration cache: values of this type are restored from the configuration cache as"
        )
        if (MapProperty.isAssignableFrom(propertyKind)) {
            assert mapKeyOrValue in ['key', 'value']: "MapProperty assertions must declare whether the problem is with the key or the value"
        }
    }
}
