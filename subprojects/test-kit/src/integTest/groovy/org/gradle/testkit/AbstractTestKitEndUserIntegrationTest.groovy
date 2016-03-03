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

package org.gradle.testkit

import groovy.transform.PackageScope
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testkit.runner.GradleRunnerIntegrationTest
import org.gradle.testkit.runner.fixtures.annotations.NonCrossVersion
import org.gradle.testkit.runner.internal.TempTestKitDirProvider
import org.gradle.util.UsesNativeServices

@NonCrossVersion
@UsesNativeServices
abstract class AbstractTestKitEndUserIntegrationTest extends GradleRunnerIntegrationTest {

    def setup() {
        executer.requireGradleHome().withStackTraceChecksDisabled()
        executer.withEnvironmentVars(GRADLE_USER_HOME: executer.gradleUserHomeDir.absolutePath)
        buildFile << buildFileForGroovyProject()
    }

    @PackageScope
    TestFile writeTest(String content, String className = 'BuildLogicFunctionalTest') {
        file("src/test/groovy/org/gradle/test/${className}.groovy") << content
    }

    @PackageScope
    static String buildFileForGroovyProject() {
        """
            apply plugin: 'groovy'

            dependencies {
                compile localGroovy()
                testCompile('org.spockframework:spock-core:1.0-groovy-2.4') {
                    exclude module: 'groovy-all'
                }
            }

            repositories {
                mavenCentral()
            }

            test.testLogging.exceptionFormat = 'full'
        """
    }

    @PackageScope
    static String gradleTestKitDependency() {
        """
            dependencies {
                testCompile gradleTestKit()
            }
        """
    }

    @PackageScope
    static String gradleApiDependency() {
        """
            dependencies {
                compile gradleApi()
            }
        """
    }

    @PackageScope
    static String parallelTests() {
        """
            test {
                maxParallelForks = 3

                testLogging {
                    events 'started'
                }
            }
        """
    }

    @PackageScope
    static String buildLogicFunctionalTestCreatingGradleRunner() {
        """
            package org.gradle.test

            import org.gradle.testkit.runner.GradleRunner
            import spock.lang.Specification

            class BuildLogicFunctionalTest extends Specification {
                def "create GradleRunner"() {
                    expect:
                    GradleRunner.create().withProjectDir(new File("foo")).build()
                }
            }
        """
    }

    @PackageScope
    static String successfulSpockTest(String className) {
        """
            package org.gradle.test

            import org.gradle.testkit.runner.GradleRunner
            import static org.gradle.testkit.runner.TaskOutcome.*
            import org.junit.Rule
            import org.junit.rules.TemporaryFolder
            import spock.lang.Specification

            class $className extends Specification {
                @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()
                File buildFile

                def setup() {
                    buildFile = testProjectDir.newFile('build.gradle')
                }

                def "execute helloWorld task"() {
                    given:
                    buildFile << '''
                        task helloWorld {
                            doLast {
                                println 'Hello world!'
                            }
                        }
                    '''

                    when:
                    def result = GradleRunner.create()
                        .withProjectDir(testProjectDir.root)
                        .withArguments('helloWorld')

                        .withDebug($debug)
                        .build()

                    then:
                    noExceptionThrown()
                }
            }
        """
    }

    private DaemonLogsAnalyzer createDaemonLogAnalyzer() {
        File daemonBaseDir = new File(new TempTestKitDirProvider().getDir(), 'daemon')
        DaemonLogsAnalyzer.newAnalyzer(daemonBaseDir, executer.distribution.version.version)
    }

    @PackageScope
    void assertDaemonsAreStopping() {
        createDaemonLogAnalyzer().visible*.stops()
    }

    @PackageScope
    void killDaemons() {
        createDaemonLogAnalyzer().killAll()
    }
}
