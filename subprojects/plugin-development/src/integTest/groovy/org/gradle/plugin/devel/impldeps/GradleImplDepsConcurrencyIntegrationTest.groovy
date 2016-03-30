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

package org.gradle.plugin.devel.impldeps

import org.gradle.test.fixtures.ConcurrentTestUtil
import org.junit.Rule

class GradleImplDepsConcurrencyIntegrationTest extends BaseGradleImplDepsIntegrationTest {

    @Rule
    final ConcurrentTestUtil concurrent = new ConcurrentTestUtil(35000)

    def "Gradle API and TestKit dependency can be resolved by concurrent Gradle builds"() {
        given:
        requireOwnGradleUserHomeDir()

        when:
        def outputs = []

        5.times { count ->
            concurrent.start {
                def projectDirName = file("project$count").name
                def projectBuildFile = file("$projectDirName/build.gradle")
                projectBuildFile << resolveGradleApiAndTestKitDependencies()

                def gradleHandle = executer.inDirectory(file("project$count")).withTasks('resolveDependencies').start()
                gradleHandle.waitForFinish()
                outputs << gradleHandle.standardOutput
            }
        }

        concurrent.finished()

        then:
        def apiGenerationOutputs = outputs.findAll { it =~ /$API_JAR_GENERATION_OUTPUT_REGEX/ }
        def testKitGenerationOutputs = outputs.findAll { it =~ /$TESTKIT_GENERATION_OUTPUT_REGEX/ }
        apiGenerationOutputs.size() == 1
        testKitGenerationOutputs.size() == 1
        assertApiGenerationOutput(apiGenerationOutputs[0])
        assertTestKitGenerationOutput(testKitGenerationOutputs[0])
    }

    def "Gradle API and TestKit dependency can be resolved and used by concurrent tasks within one build"() {
        given:
        requireOwnGradleUserHomeDir()

        def projectCount = 10
        (1..projectCount).each { count ->
            def subprojectBuildFile = file("sub$count/build.gradle")
            subprojectBuildFile << testableGroovyProject()
            file("sub$count/src/test/groovy/MyTest.groovy") << """
                class MyTest extends groovy.util.GroovyTestCase {

                    void testUsageOfGradleApiAndTestKitClasses() {
                        def classLoader = getClass().classLoader
                        classLoader.loadClass('org.gradle.api.Plugin')
                        classLoader.loadClass('org.gradle.testkit.runner.GradleRunner')
                    }
                }
            """
        }

        file('settings.gradle') << "include ${(1..projectCount).collect { "'sub$it'" }.join(',')}"

        when:
        args('--parallel')
        succeeds 'test'

        then:
        assertApiGenerationOutput(output)
        assertTestKitGenerationOutput(output)
    }

    def "Gradle API and TestKit dependency JAR files are the same when run by concurrent tasks within one build"() {
        given:
        requireOwnGradleUserHomeDir()

        def projectCount = 10
        (1..projectCount).each { count ->
            def subprojectBuildFile = file("sub$count/build.gradle")
            subprojectBuildFile << """
                configurations {
                    gradleImplDeps
                }

                dependencies {
                    gradleImplDeps gradleApi(), gradleTestKit()
                }

                task resolveDependencies {
                    doLast {
                        def files = configurations.gradleImplDeps.resolve()
                        file('deps.txt').text = files.collect {
                            java.security.MessageDigest.getInstance('MD5').digest(it.bytes).encodeHex().toString()
                        }.join(',')
                    }
                }
            """
        }

        file('settings.gradle') << "include ${(1..projectCount).collect { "'sub$it'" }.join(',')}"

        when:
        args('--parallel')
        succeeds 'resolveDependencies'

        then:
        assertApiGenerationOutput(output)
        assertTestKitGenerationOutput(output)

        and:
        def checksums = (1..projectCount).collect { count ->
            def projectDirName = file("sub$count").name
            file("$projectDirName/deps.txt").text.split(',') as Set
        }

        def singleChecksum = checksums.first()
        checksums.findAll { it == singleChecksum }.size() == projectCount
    }

    static String resolveGradleApiAndTestKitDependencies() {
        """
            configurations {
                gradleImplDeps
            }

            dependencies {
                gradleImplDeps gradleApi(), gradleTestKit()
            }

            task resolveDependencies {
                doLast {
                    configurations.gradleImplDeps.resolve()
                }
            }
        """
    }

    static void assertApiGenerationOutput(String output) {
        assertSingleGenerationOutput(output, API_JAR_GENERATION_OUTPUT_REGEX)
    }

    static void assertTestKitGenerationOutput(String output) {
        assertSingleGenerationOutput(output, TESTKIT_GENERATION_OUTPUT_REGEX)
    }
}
