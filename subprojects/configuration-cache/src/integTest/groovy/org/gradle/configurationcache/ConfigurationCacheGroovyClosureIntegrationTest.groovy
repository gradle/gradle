/*
 * Copyright 2022 the original author or authors.
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

class ConfigurationCacheGroovyClosureIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    def "build fails when task action closure reads a project property"() {
        given:
        buildFile << """
            tasks.register("some") {
                doFirst {
                    println(name) // task property is ok
                    println(buildDir)
                }
            }
        """

        when:
        configurationCacheRun ":some"

        then:
        noExceptionThrown()

        when:
        configurationCacheFails ":some"

        then:
        failure.assertHasFileName("Build file '$buildFile'")
        failure.assertHasLineNumber(5)
        failure.assertHasFailure("Execution failed for task ':some'.") {
            it.assertHasCause("Cannot reference a Gradle script object from a Groovy closure as these are not supported with the configuration cache.")
        }
    }

    def "build fails when task action closure sets a project property"() {
        given:
        buildFile << """
            tasks.register("some") {
                doFirst {
                    description = "broken" // task property is ok
                    version = 1.2
                }
            }
        """

        when:
        configurationCacheRun ":some"

        then:
        noExceptionThrown()

        when:
        configurationCacheFails ":some"

        then:
        failure.assertHasFileName("Build file '$buildFile'")
        failure.assertHasLineNumber(5)
        failure.assertHasFailure("Execution failed for task ':some'.") {
            it.assertHasCause("Cannot reference a Gradle script object from a Groovy closure as these are not supported with the configuration cache.")
        }
    }

    def "build fails when task action closure invokes a project method"() {
        given:
        buildFile << """
            tasks.register("some") {
                doFirst {
                    println(file("broken"))
                }
            }
        """

        when:
        configurationCacheRun ":some"

        then:
        noExceptionThrown()

        when:
        configurationCacheFails ":some"

        then:
        failure.assertHasFileName("Build file '$buildFile'")
        failure.assertHasLineNumber(4)
        failure.assertHasFailure("Execution failed for task ':some'.") {
            it.assertHasCause("Cannot reference a Gradle script object from a Groovy closure as these are not supported with the configuration cache.")
        }
    }

    def "build fails when task action nested closure reads a project property"() {
        given:
        buildFile << """
            tasks.register("some") {
                doFirst {
                    def cl = {
                        println(name) // task property is ok
                        println(buildDir)
                    }
                    cl()
                }
            }
        """

        when:
        configurationCacheRun ":some"

        then:
        noExceptionThrown()

        when:
        configurationCacheFails ":some"

        then:
        failure.assertHasFileName("Build file '$buildFile'")
        failure.assertHasLineNumber(6)
        failure.assertHasFailure("Execution failed for task ':some'.") {
            it.assertHasCause("Cannot reference a Gradle script object from a Groovy closure as these are not supported with the configuration cache.")
        }
    }

    def "build fails when task onlyIf closure reads a project property"() {
        given:
        buildFile << """
            tasks.register("some") {
                onlyIf { t ->
                    println(t.name) // task property is ok
                    println(buildDir)
                    true
                }
                doFirst {
                }
            }
        """

        when:
        configurationCacheRun ":some"

        then:
        noExceptionThrown()

        when:
        configurationCacheFails ":some"

        then:
        failure.assertHasFileName("Build file '$buildFile'")
        failure.assertHasLineNumber(5)
        failure.assertHasFailure("Could not evaluate onlyIf predicate for task ':some'.") {
            // The cause is not reported
        }
    }
}
