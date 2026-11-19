/*
 * Copyright 2026 Gradle and contributors.
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

package org.gradle.scala.compile

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.ScalaCoverage
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import org.gradle.util.internal.VersionNumber
import org.junit.Assume
import spock.lang.Issue

import static org.gradle.scala.ScalaCompilationFixture.scalaDependency

abstract class AbstractScalaCompilerArgumentProvidersIntegrationTest extends MultiVersionIntegrationSpec {
    def setup() {
        buildFile << """
            plugins { id 'scala' }
            ${mavenCentralRepository()}
            dependencies {
                implementation '${scalaDependency(version)}'
            }

            abstract class CompilerArguments implements CommandLineArgumentProvider {
                @Input
                abstract ListProperty<String> getArguments()

                @Override
                Iterable<String> asArguments() {
                    arguments.get()
                }
            }
        """
        file('src/main/scala/Example.scala') << 'class Example'
    }

    protected String getJava8Target() {
        versionNumber.major >= 3 || versionNumber.minor >= 13 ? '-release:8' : '-target:jvm-1.8'
    }

    protected String registerArguments(String... arguments) {
        """
            def compilerArguments = objects.newInstance(CompilerArguments)
            compilerArguments.arguments.set(${arguments.collect { "'$it'" }})
            tasks.withType(ScalaCompile).configureEach {
                scalaCompileOptions.compilerArgumentProviders.add(compilerArguments)
            }
        """
    }
}

@Issue('https://github.com/gradle/gradle/issues/39899')
@TargetCoverage({ ScalaCoverage.SUPPORTED_BY_JDK })
class ScalaCompilerArgumentProvidersIntegrationTest extends AbstractScalaCompilerArgumentProvidersIntegrationTest {
    def 'contributions registered #order additional parameter assignment reach scalac'() {
        given:
        file('src/main/scala/Example.scala').text = '''
            class Example {
                def warning(): Unit = { "unused"; () }
            }
        '''
        def contribution = registerArguments(versionNumber.major >= 3 ? '-Werror' : '-Xfatal-warnings')
        def application = "tasks.withType(ScalaCompile).configureEach { scalaCompileOptions.additionalParameters = ['-feature'] }"
        buildFile << (order == 'before' ? contribution + application : application + contribution)

        when:
        fails 'compileScala'

        then:
        failure.assertHasCause('Compilation failed')
        (failure.output + failure.error).contains('pure expression')

        where:
        order << ['before', 'after']
    }

    def 'contributed target overrides automatic target selection during joint compilation'() {
        given:
        file('src/main/scala/Example.scala').text = 'class Example extends JavaBase'
        file('src/main/scala/JavaBase.java') << 'public class JavaBase {}'
        buildFile << registerArguments(java8Target)
        buildFile << """
            java.toolchain.languageVersion = JavaLanguageVersion.of(${Jvm.current().javaVersion.majorVersion})
            tasks.withType(ScalaCompile).configureEach {
                scalaCompileOptions.additionalParameters = ['-feature']
                // This invalid scalac option is valid for javac: providers must stay separate.
                def javaArguments = objects.newInstance(CompilerArguments)
                javaArguments.arguments.add('-parameters')
                options.compilerArgumentProviders.add(javaArguments)
            }
        """

        when:
        succeeds 'compileScala'

        then:
        executedAndNotSkipped ':compileScala'
        JavaVersion.forClass(scalaClassFile('Example.class').bytes) == JavaVersion.VERSION_1_8
        JavaVersion.forClass(scalaClassFile('JavaBase.class').bytes) == Jvm.current().javaVersion
    }

    def 'contributed arguments do not replace application arguments'() {
        given:
        buildFile << registerArguments('-feature')
        buildFile << """
            java.toolchain.languageVersion = JavaLanguageVersion.of(${Jvm.current().javaVersion.majorVersion})
            tasks.withType(ScalaCompile).configureEach {
                scalaCompileOptions.additionalParameters = ['${java8Target}']
            }
        """

        when:
        succeeds 'compileScala'

        then:
        JavaVersion.forClass(scalaClassFile('Example.class').bytes) == JavaVersion.VERSION_1_8
    }

    def 'unrelated tasks do not calculate compiler arguments'() {
        given:
        buildFile << '''
            abstract class UnusedArguments implements CommandLineArgumentProvider {
                @Override
                Iterable<String> asArguments() {
                    throw new IllegalStateException('arguments must not be queried')
                }
            }
            def unusedArguments = objects.newInstance(UnusedArguments)
            tasks.withType(ScalaCompile).configureEach {
                scalaCompileOptions.compilerArgumentProviders.add(unusedArguments)
            }
        '''

        expect:
        succeeds 'help'
    }

    def 'can register Scala compiler argument providers with Kotlin DSL'() {
        given:
        buildFile.delete()
        buildKotlinFile << """
            import org.gradle.process.CommandLineArgumentProvider

            plugins { scala }
            ${mavenCentralRepository(GradleDsl.KOTLIN)}
            dependencies {
                implementation("${scalaDependency(version)}")
            }
            abstract class CompilerArguments : CommandLineArgumentProvider {
                @get:Input
                abstract val arguments: ListProperty<String>

                override fun asArguments(): Iterable<String> = arguments.get()
            }
            val compilerArguments = objects.newInstance<CompilerArguments>()
            compilerArguments.arguments.add("${java8Target}")
            java.toolchain.languageVersion.set(JavaLanguageVersion.of(${Jvm.current().javaVersion.majorVersion}))
            tasks.withType<ScalaCompile>().configureEach {
                scalaCompileOptions.compilerArgumentProviders.add(compilerArguments)
                scalaCompileOptions.additionalParameters = listOf("-feature")
            }
        """

        when:
        succeeds 'compileScala'

        then:
        JavaVersion.forClass(scalaClassFile('Example.class').bytes) == JavaVersion.VERSION_1_8
    }

    def 'provider options work together with Scala compiler plugin artifacts'() {
        given:
        Assume.assumeTrue(versionNumber.major == 2 && versionNumber >= VersionNumber.parse('2.12.14'))
        file('src/main/scala/Example.scala').text = '''
            object Example {
                class A[X[_]]
                new A[Map[Int, _]]
            }
        '''
        buildFile << """
            dependencies {
                scalaCompilerPlugins 'org.typelevel:kind-projector_${version}:0.13.4'
            }
        """
        buildFile << registerArguments('-P:kind-projector:underscore-placeholders')
        buildFile << "tasks.withType(ScalaCompile).configureEach { scalaCompileOptions.additionalParameters = ['-feature'] }"

        expect:
        succeeds 'compileScala'
        scalaClassFile('Example.class').assertExists()
    }

    def 'argument provider failures fail compilation'() {
        given:
        buildFile << '''
            abstract class FailingArguments implements CommandLineArgumentProvider {
                @Override
                Iterable<String> asArguments() {
                    throw new IllegalStateException('cannot calculate scalac arguments')
                }
            }
            def failingArguments = objects.newInstance(FailingArguments)
            tasks.withType(ScalaCompile).configureEach {
                scalaCompileOptions.compilerArgumentProviders.add(failingArguments)
            }
        '''

        when:
        fails 'compileScala'

        then:
        failure.assertHasCause('cannot calculate scalac arguments')
    }
}

@Issue('https://github.com/gradle/gradle/issues/39899')
@TargetCoverage({ ScalaCoverage.SCALA_2.findAll { !it.startsWith('2.13.') } })
class ScalaCompilerArgumentProvidersLegacyIntegrationTest extends AbstractScalaCompilerArgumentProvidersIntegrationTest implements JavaToolchainFixture {
    def setup() {
        def jdk = AvailableJavaHomes.jdk8
        Assume.assumeNotNull(jdk)
        executer.beforeExecute {
            withInstallations(jdk)
        }
        configureJavaPluginToolchainVersion(jdk)
    }

    def 'contributions survive #order application assignment with a legacy Scala compiler'() {
        given:
        file('src/main/scala/Example.scala').text = '''
            class Example {
                def warning(): Unit = { "unused"; () }
            }
        '''
        def contribution = registerArguments('-Xfatal-warnings')
        def application = "tasks.withType(ScalaCompile).configureEach { scalaCompileOptions.additionalParameters = ['-feature'] }"
        buildFile << (order == 'before' ? contribution + application : application + contribution)

        when:
        fails 'compileScala'

        then:
        failure.assertHasCause('Compilation failed')
        (failure.output + failure.error).contains('pure expression')

        where:
        order << ['before', 'after']
    }
}

@Issue('https://github.com/gradle/gradle/issues/39899')
@TargetCoverage({ ScalaCoverage.LATEST_IN_MAJOR })
class ScalaCompilerArgumentProvidersCachingIntegrationTest extends AbstractScalaCompilerArgumentProvidersIntegrationTest implements DirectoryBuildCacheFixture {
    private static final String ENCODING_ARGUMENTS = '''
        abstract class EncodingArguments implements CommandLineArgumentProvider {
            @InputFile
            @PathSensitive(PathSensitivity.RELATIVE)
            abstract RegularFileProperty getEncodingFile()

            @Override
            Iterable<String> asArguments() {
                ['-encoding', encodingFile.get().asFile.text.trim()]
            }
        }
    '''

    def 'provider scalar inputs invalidate compilation and build cache entries'() {
        given:
        buildFile << """
            def compilerArguments = objects.newInstance(CompilerArguments)
            compilerArguments.arguments.add(providers.gradleProperty('scalaTarget').map { '-release:' + it })
            tasks.withType(ScalaCompile).configureEach {
                scalaCompileOptions.compilerArgumentProviders.add(compilerArguments)
            }
        """

        when:
        succeeds 'compileScala', '-PscalaTarget=8', '--build-cache'

        then:
        executedAndNotSkipped ':compileScala'
        JavaVersion.forClass(scalaClassFile('Example.class').bytes) == JavaVersion.VERSION_1_8

        when:
        succeeds 'compileScala', '-PscalaTarget=8', '--build-cache'

        then:
        skipped ':compileScala'

        when:
        succeeds 'compileScala', '-PscalaTarget=11', '--build-cache'

        then:
        executedAndNotSkipped ':compileScala'
        JavaVersion.forClass(scalaClassFile('Example.class').bytes) == JavaVersion.VERSION_11

        when:
        succeeds 'clean', 'compileScala', '-PscalaTarget=8', '--build-cache'

        then:
        result.assertTaskSkipped ':compileScala'
        outputContains(':compileScala FROM-CACHE')
        JavaVersion.forClass(scalaClassFile('Example.class').bytes) == JavaVersion.VERSION_1_8
    }

    @Requires(value = TestExecutionPreconditions.NotConfigCached, reason = 'handles configuration cache explicitly')
    def 'configuration cache reuses argument providers during actual compilation'() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile << registerArguments('-release:8')

        when:
        succeeds 'compileScala', '--configuration-cache', '--configuration-cache-problems=fail', '--no-build-cache'

        then:
        configurationCache.assertStateStored()
        JavaVersion.forClass(scalaClassFile('Example.class').bytes) == JavaVersion.VERSION_1_8

        when:
        file('build').deleteDir()
        succeeds 'compileScala', '--configuration-cache', '--configuration-cache-problems=fail', '--no-build-cache'

        then:
        configurationCache.assertStateLoaded()
        executedAndNotSkipped ':compileScala'
        JavaVersion.forClass(scalaClassFile('Example.class').bytes) == JavaVersion.VERSION_1_8
    }

    def 'provider file inputs carry producer dependencies and invalidate compilation'() {
        given:
        file('src/main/scala/Example.scala').text = 'class Example { val text = "\u00e4" }'
        buildFile << ENCODING_ARGUMENTS
        buildFile << '''
            abstract class WriteEncoding extends DefaultTask {
                @Input
                abstract Property<String> getEncoding()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void write() {
                    def target = outputFile.get().asFile
                    target.parentFile.mkdirs()
                    target.text = encoding.get()
                }
            }
            def writeEncoding = tasks.register('writeEncoding', WriteEncoding) {
                encoding.set(providers.gradleProperty('encoding').orElse('UTF-8'))
                outputFile.set(layout.buildDirectory.file('encoding.txt'))
            }
            def encodingArguments = objects.newInstance(EncodingArguments)
            encodingArguments.encodingFile.set(writeEncoding.flatMap { it.outputFile })
            tasks.withType(ScalaCompile).configureEach {
                scalaCompileOptions.compilerArgumentProviders.add(encodingArguments)
            }
        '''

        when:
        succeeds 'compileScala', '--no-build-cache'

        then:
        executedAndNotSkipped ':writeEncoding', ':compileScala'
        def originalBytes = scalaClassFile('Example.class').bytes.toList()

        when:
        succeeds 'compileScala', '--no-build-cache'

        then:
        skipped ':writeEncoding', ':compileScala'

        when:
        succeeds 'compileScala', '-Pencoding=ISO-8859-1', '--no-build-cache'

        then:
        executedAndNotSkipped ':writeEncoding', ':compileScala'
        scalaClassFile('Example.class').bytes.toList() != originalBytes
    }

    @Requires(value = TestExecutionPreconditions.NotConfigCached, reason = 'handles configuration cache explicitly')
    def 'derived compiler argument view can be wired to another task with configuration cache'() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile << registerArguments('-feature')
        buildFile << '''
            abstract class WriteArguments extends DefaultTask {
                @Input
                abstract ListProperty<String> getArguments()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void write() {
                    def target = outputFile.get().asFile
                    target.parentFile.mkdirs()
                    target.text = arguments.get().join(System.lineSeparator())
                }
            }
            def compileOptions = tasks.named('compileScala', ScalaCompile).get().scalaCompileOptions
            compileOptions.additionalParameters = ['-deprecation']
            tasks.register('writeArguments', WriteArguments) {
                arguments.set(compileOptions.allCompilerArgs)
                outputFile.set(layout.buildDirectory.file('scalac-arguments.txt'))
            }
        '''

        when:
        succeeds 'writeArguments', '--configuration-cache', '--configuration-cache-problems=fail', '--no-build-cache'

        then:
        configurationCache.assertStateStored()
        file('build/scalac-arguments.txt').readLines() == ['-deprecation', '-feature']

        when:
        file('build').deleteDir()
        succeeds 'writeArguments', '--configuration-cache', '--configuration-cache-problems=fail', '--no-build-cache'

        then:
        configurationCache.assertStateLoaded()
        executedAndNotSkipped ':writeArguments'
        file('build/scalac-arguments.txt').readLines() == ['-deprecation', '-feature']
    }

    def 'normalized provider file inputs allow build cache reuse after relocation'() {
        given:
        settingsFile << "rootProject.name = 'argument-providers'"
        file('config/encoding.txt') << 'UTF-8'
        buildFile << ENCODING_ARGUMENTS
        buildFile << '''
            def encodingArguments = objects.newInstance(EncodingArguments)
            encodingArguments.encodingFile.set(layout.projectDirectory.file('config/encoding.txt'))
            tasks.withType(ScalaCompile).configureEach {
                scalaCompileOptions.compilerArgumentProviders.add(encodingArguments)
            }
        '''

        when:
        succeeds 'compileScala', '--build-cache'

        then:
        executedAndNotSkipped ':compileScala'

        when:
        def relocated = file('relocated')
        buildFile.copyTo(relocated.file('build.gradle'))
        settingsFile.copyTo(relocated.file('settings.gradle'))
        file('src').copyTo(relocated.file('src'))
        file('config').copyTo(relocated.file('config'))
        executer.inDirectory(relocated)
        succeeds 'compileScala', '--build-cache'

        then:
        outputContains(':compileScala FROM-CACHE')
        relocated.file('build/classes/scala/main/Example.class').assertExists()
    }
}
