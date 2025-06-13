/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.plugins.ide.eclipse

class EclipseTestSourcesIntegrationTest extends AbstractEclipseTestSourcesIntegrationTest {

    def "source directories from main source sets are not marked with test classpath attribute"() {
        buildFile << """
            plugins {
                id 'java'
                id 'eclipse'
            }
        """
        file('src/main/java').mkdirs()

        when:
        run 'eclipse'

        then:
        assertSourceDirectoryDoesNotHaveTestAttribute('src/main/java')
    }

    def "source directories from test source sets are marked with test classpath attribute"() {
        buildFile << """
            plugins {
                id 'java'
                id 'eclipse'
            }
        """
        file('src/test/java').mkdirs()

        when:
        run 'eclipse'

        then:
        assertSourceDirectoryHasTestAttribute('src/test/java')
    }

    def "source directories defined in custom source sets are marked with test classpath attribute if source set name contains 'test' substring"() {
        buildFile << """
            plugins {
                id 'java'
                id 'eclipse'
            }

            sourceSets {
                functionalTest
                integration
            }
        """
        file('src/functionalTest/java').mkdirs()
        file('src/integration/java').mkdirs()

        when:
        run 'eclipse'

        then:
        assertSourceDirectoryHasTestAttribute('src/functionalTest/java')
        assertSourceDirectoryDoesNotHaveTestAttribute('src/integration/java')
    }

    def "source directories defined in jvm test suites are marked with test classpath attribute"() {
        settingsFile << "rootProject.name = 'eclipse-jvm-test-suites-integration-test'"
        buildFile << """
            plugins {
                id 'eclipse'
                id 'jvm-test-suite'
            }

            testing {
                suites {
                    integration(JvmTestSuite) {
                        sources {
                            java {
                                srcDirs = ['src/integration/java']
                            }
                        }
                    }
                }
            }
        """
        file('src/integration/java').mkdirs()

        when:
        run 'eclipse'

        then:
        assertSourceDirectoryHasTestAttribute('src/integration/java')
    }

    def "can configure which source directories are marked with test classpath attribute"() {
        setup:
        buildFile << """
            plugins {
                id 'java'
                id 'eclipse'
            }

            eclipse {
                classpath {
                    testSourceSets = [sourceSets.main]
                }
            }
        """
        file('src/main/java').mkdirs()
        file('src/test/java').mkdirs()

        when:
        run 'eclipse'

        then:
        assertSourceDirectoryHasTestAttribute('src/main/java')
        assertSourceDirectoryDoesNotHaveTestAttribute('src/test/java')
    }
}
