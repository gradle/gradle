/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec

class DependencyHandlerApiResolveIntegrationTest extends DaemonIntegrationSpec {
    public static final String GRADLE_TEST_KIT_JAR_BASE_NAME = 'gradle-test-kit-functional-'

    def setup() {
        buildFile << """
            configurations {
                libs
            }

            task resolveLibs(type: Copy) {
                ext.extractedDir = file('\$buildDir/libs')
                from configurations.libs
                into extractedDir
            }

            task check {
                dependsOn resolveLibs
            }
        """
    }

    def "testKit dependency API adds test-kit classes"() {
        when:
        buildFile << """
            dependencies {
                libs testKit()
            }

            check {
                doLast {
                    def jarFiles = resolveLibs.extractedDir.listFiles()
                    def testKitFunctionalJar = jarFiles.find { it.name.startsWith('$GRADLE_TEST_KIT_JAR_BASE_NAME') }
                    assert testKitFunctionalJar

                    def jar = new java.util.jar.JarFile(testKitFunctionalJar)
                    def jarFileEntries = jar.entries()
                    def classFiles = jarFileEntries.findAll { it.name.endsWith('.class') }

                    classFiles.each {
                        assert it.name.startsWith('org/gradle/testkit/functional')
                    }
                }
            }
        """

        then:
        succeeds 'check'
    }

    def "gradleApi dependency API does not include test-kit JAR"() {
        when:
        buildFile << """
            dependencies {
                libs gradleApi()
            }

            check {
                doLast {
                    def jarFiles = resolveLibs.extractedDir.listFiles()
                    def testKitFunctionalJar = jarFiles.find { it.name.startsWith('$GRADLE_TEST_KIT_JAR_BASE_NAME') }
                    assert !testKitFunctionalJar
                }
            }
        """

        then:
        succeeds 'check'
    }
}
