/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.testkit.runner.enduser

import groovy.io.FileType
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.testkit.runner.fixtures.NoDebug
import org.gradle.testkit.runner.internal.DefaultGradleRunner
import org.gradle.util.internal.GFileUtils
import org.intellij.lang.annotations.Language

/**
 * Miscellaneous usage scenarios that don't have more specific homes.
 */
@Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = NOT_EMBEDDED_REASON)
class GradleRunnerMiscEndUserIntegrationTest extends BaseTestKitEndUserIntegrationTest implements TestKitDependencyBlock {

    def setup() {
        buildFile << """
            apply plugin: 'groovy'

            ${mavenCentralRepository()}

            testing {
                suites {
                    test {
                        useSpock()
                        dependencies {
                            implementation localGroovy()
                        }
                    }
                }
            }

            test {
                testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        """
    }

    @NoDebug
    def "fails appropriately if runner is loaded from a JAR that is not part of the distribution and no explicit version set"() {
        when:
        executer.withStackTraceChecksDisabled()
        def jarsDir = file('jars').createDir()

        new File(distribution.gradleHomeDir, 'lib').eachFileRecurse(FileType.FILES) { f ->
            if (f.name.contains("gradle-test-kit")
                || f.name.contains("commons-io")
                || f.name.contains("guava")
                || f.name.contains("gradle-base-services")
                || f.name.contains("gradle-stdlib-java-extensions")
                || f.name.contains("gradle-file-temp")
                || f.name.contains("gradle-tooling-api")
                || f.name.contains("gradle-core")
                || f.name.contains("gradle-build-process-services")
            ) {
                GFileUtils.copyFile(f, new File(jarsDir, f.name))
            }
        }

        def testKitJar = jarsDir.listFiles().find { it.name.contains "test-kit" }
        buildFile << """
            dependencies {
                testImplementation fileTree(dir: 'jars', include: '*.jar')
            }
        """

        groovyTestSourceFile("""
            import org.gradle.testkit.runner.GradleRunner
            import spock.lang.Specification

            class Test extends Specification {
                def "create GradleRunner"() {
                    expect:
                    GradleRunner.create().withProjectDir(new File("foo")).build()
                }
            }
        """)

        then:
        fails 'build'
        failure.output.contains "Could not find a Gradle installation to use based on the location of the GradleRunner class: $testKitJar.canonicalPath. Please specify a Gradle runtime to use via GradleRunner.withGradleVersion() or similar."
    }

    def "can use GradleRunner to test"() {
        when:
        buildFile << gradleTestKitDependency()
        file("src/test/groovy/Test.groovy") << successfulSpockTest('Test')

        then:
        succeeds 'build'
    }

    def "can use GradleRunner to test concurrently"() {
        when:
        buildFile << gradleTestKitDependency() << """
            test {
                maxParallelForks = 3

                testLogging {
                    events 'started'
                }
            }
        """

        def testClassNames = (1..10).collect { "Test$it" }
        testClassNames.each { testClassName ->
            file("src/test/groovy/${testClassName}.groovy") << successfulSpockTest(testClassName)
        }

        then:
        succeeds 'test'

        testClassNames.each { testClassName ->
            result.assertOutputContains("${testClassName} > execute helloWorld task STARTED")
        }
    }

    def "can control debug mode through system property"() {
        when:
        buildFile << gradleTestKitDependency() << """
            test {
                systemProperty '$DefaultGradleRunner.DEBUG_SYS_PROP', '$debug'
            }
        """

        groovyTestSourceFile("""
            import org.gradle.testkit.runner.GradleRunner
            import spock.lang.Specification

            class Test extends Specification {
                def "default debug value is derived from system property"() {
                    expect:
                    GradleRunner.create().debug == $debug
                }
            }
        """)

        then:
        succeeds 'test'
    }

    static String successfulSpockTest(String className) {
        @Language("groovy")
        def spockTest = """
            import org.gradle.testkit.runner.GradleRunner
            import java.nio.file.Files
            import java.nio.file.Path
            import static org.gradle.testkit.runner.TaskOutcome.*
            import spock.lang.Specification

            class $className extends Specification {
                File testProjectDir = Files.createTempDirectory("GradleRunnerMiscEndUserIntegrationTest").toFile()
                File buildFile
                File settingsFile

                def setup() {
                    testProjectDir.deleteOnExit()
                }

                def "execute helloWorld task"() {
                    settingsFile = new File(testProjectDir, 'settings.gradle')
                    buildFile = new File(testProjectDir, 'build.gradle')
                    given:
                    settingsFile << "rootProject.name = 'hello-world'"
                    buildFile << '''
                        task helloWorld {
                            doLast {
                                println 'Hello world!'
                            }
                        }
                    '''

                    when:
                    def result = GradleRunner.create()
                        .withProjectDir(testProjectDir)
                        .withArguments('helloWorld')
                        .withDebug($debug)
                        .build()

                    then:
                    noExceptionThrown()
                }
            }
        """
        spockTest
    }

}
