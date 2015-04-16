/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue
// TODO - report on the configuration that was actually changed
// TODO - warn about configurations resolved via a project dependency
// TODO - verify line number is included in deprecation message
// TODO - warn about changes to artifacts
// TODO - warn about changes to resolution strategy and other mutations
class UnsupportedConfigurationMutationTest extends AbstractIntegrationSpec {

    def "does not allow adding dependencies to a configuration that has been resolved"() {
        buildFile << """
            configurations { a }
            configurations.a.resolve()
            dependencies { a files("some.jar") }
        """
        when: fails()
        then: failure.assertHasCause("Cannot change dependencies of configuration ':a' after it has been resolved.")
    }

    def "does not allow adding artifacts to a configuration that has been resolved"() {
        buildFile << """
            configurations { a }
            configurations.a.resolve()
            artifacts { a file("some.jar") }
        """
        when: fails()
        then: failure.assertHasCause("Cannot change artifacts of configuration ':a' after it has been resolved.")
    }

    def "does not allow changing excludes on a configuration that has been resolved"() {
        buildFile << """
            configurations { a }
            configurations.a.resolve()
            configurations.a.exclude group: 'someGroup'
        """
        when: fails()
        then: failure.assertHasCause("Cannot change dependencies of configuration ':a' after it has been resolved.")
    }

    def "warns about changing conflict resolution on a configuration that has been resolved"() {
        buildFile << """
            configurations { a }
            configurations.a.resolve()
            configurations.a.resolutionStrategy.failOnVersionConflict()
        """
        executer.withDeprecationChecksDisabled()

        when: succeeds()
        then: output.contains("Changed strategy of configuration ':a' after it has been resolved. This behaviour has been deprecated and is scheduled to be removed in Gradle 3.0")
    }

    def "warns about changing forced versions on a configuration that has been resolved"() {
        buildFile << """
            configurations { a }
            configurations.a.resolve()
            configurations.a.resolutionStrategy.force "org.utils:api:1.3"
        """
        executer.withDeprecationChecksDisabled()

        when: succeeds()
        then: output.contains("Changed strategy of configuration ':a' after it has been resolved. This behaviour has been deprecated and is scheduled to be removed in Gradle 3.0")
    }

    def "warns about changing cache policy on a configuration that has been resolved"() {
        buildFile << """
            configurations { a }
            configurations.a.resolve()
            configurations.a.resolutionStrategy.cacheChangingModulesFor 0, "seconds"
        """
        executer.withDeprecationChecksDisabled()

        when: succeeds()
        then: output.contains("Changed strategy of configuration ':a' after it has been resolved. This behaviour has been deprecated and is scheduled to be removed in Gradle 3.0")
    }

    def "warns about changing resolution rules on a configuration that has been resolved"() {
        buildFile << """
            configurations { a }
            configurations.a.resolve()
            configurations.a.resolutionStrategy.eachDependency {}
        """
        executer.withDeprecationChecksDisabled()

        when: succeeds()
        then: output.contains("Changed strategy of configuration ':a' after it has been resolved. This behaviour has been deprecated and is scheduled to be removed in Gradle 3.0")
    }

    def "warns about changing substitution rules on a configuration that has been resolved"() {
        buildFile << """
            configurations { a }
            configurations.a.resolve()
            configurations.a.resolutionStrategy.dependencySubstitution.all {}
        """
        executer.withDeprecationChecksDisabled()

        when: succeeds()
        then: output.contains("Changed strategy of configuration ':a' after it has been resolved. This behaviour has been deprecated and is scheduled to be removed in Gradle 3.0")
    }

    def "warns about changing component selection rules on a configuration that has been resolved"() {
        buildFile << """
            configurations { a }
            configurations.a.resolve()
            configurations.a.resolutionStrategy.componentSelection.all {}
        """
        executer.withDeprecationChecksDisabled()

        when: succeeds()
        then: output.contains("Changed strategy of configuration ':a' after it has been resolved. This behaviour has been deprecated and is scheduled to be removed in Gradle 3.0")
    }

    def "warns about changing a configuration that has been resolved for task dependencies"() {
        mavenRepo.module("org.utils", "extra", '1.5').publish()

        settingsFile << "include 'api', 'impl'"
        buildFile << """
            allprojects {
                apply plugin: "java"
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
            }

            project(":impl") {
                dependencies {
                    compile project(":api")
                }

                task addDependency << {
                    dependencies {
                        compile "org.utils:extra:1.5"
                    }
                }

                task modifyConfigDuringTaskExecution(dependsOn: [addDependency, configurations.compile]) << {
                    def files = configurations.compile.files
                    assert files*.name.sort() == ["api.jar", "extra-1.5.jar"]
                    assert files*.exists() == [ true, true ]
                }
                task modifyParentConfigDuringTaskExecution(dependsOn: [addDependency, configurations.testCompile]) << {
                    def files = configurations.testCompile.files
                    assert files*.name.sort() == ["api.jar", "extra-1.5.jar"]
                    assert files*.exists() == [ true, true ]
                }
            }
"""

        when:
        executer.withDeprecationChecksDisabled()
        succeeds("impl:modifyConfigDuringTaskExecution")

        then:
        output.contains("Changed dependencies of configuration ':impl:compile' after task dependencies have been resolved. This behaviour has been deprecated and is scheduled to be removed in Gradle 3.0")
        output.contains("Resolving configuration ':impl:compile' again after modification.")

        when:
        executer.withDeprecationChecksDisabled()
        succeeds("impl:modifyParentConfigDuringTaskExecution")

        then:
        output.contains("Changed dependencies of configuration ':impl:compile' after it has been included in dependency resolution. This behaviour has been deprecated and is scheduled to be removed in Gradle 3.0")
        output.contains("Changed dependencies of parent of configuration ':impl:testCompile' after task dependencies have been resolved. This behaviour has been deprecated and is scheduled to be removed in Gradle 3.0")
        output.contains("Resolving configuration ':impl:testCompile' again after modification.")
    }

    def "allows adding artifacts to a configuration that has been resolved for task dependencies"() {
        mavenRepo.module("org.utils", "extra", '1.5').publish()

        settingsFile << "include 'api', 'impl'"
        buildFile << """
            allprojects {
                apply plugin: "java"
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
            }

            project(":impl") {
                dependencies {
                    compile project(":api")
                }

                task addArtifact << {
                    artifacts { compile file("some.jar") }
                }

                task modifyArtifactDuringTaskExecution(dependsOn: [addArtifact, configurations.compile])
                task modifyParentArtifactDuringTaskExecution(dependsOn: [addArtifact, configurations.testCompile])
            }
"""

        // Will need to track observed state and modified state separately for content (dependencies) and artifacts
        expect:
        succeeds("impl:modifyArtifactDuringTaskExecution")
        succeeds("impl:modifyParentArtifactDuringTaskExecution")
    }

    @Issue("GRADLE-3155")
    def "warns about adding dependencies to a configuration whose child has been resolved"() {
        buildFile << """
            configurations {
                a
                b.extendsFrom a
                c.extendsFrom b
            }
            configurations.c.resolve()
            dependencies { a files("some.jar") }
        """
        executer.withDeprecationChecksDisabled()

        when: succeeds()
        then: output.contains("Changed dependencies of configuration ':a' after it has been included in dependency resolution. This behaviour has been deprecated and is scheduled to be removed in Gradle 3.0")
        and:  output.contains("Changed dependencies of parent of configuration ':c' after it has been resolved. This behaviour has been deprecated and is scheduled to be removed in Gradle 3.0")
    }

    @Issue("GRADLE-3155")
    def "warns about adding artifacts to a configuration whose child has been resolved"() {
        buildFile << """
            configurations {
                a
                b.extendsFrom a
                c.extendsFrom b
            }
            configurations.c.resolve()
            artifacts { a file("some.jar") }
        """
        executer.withDeprecationChecksDisabled()

        when: succeeds()
        then: output.contains("Changed artifacts of configuration ':a' after it has been included in dependency resolution. This behaviour has been deprecated and is scheduled to be removed in Gradle 3.0")
        and:  output.contains("Changed artifacts of parent of configuration ':c' after it has been resolved. This behaviour has been deprecated and is scheduled to be removed in Gradle 3.0")
    }

    @Issue("GRADLE-3155")
    def "warns about changing a configuration whose child has been resolved"() {
        buildFile << """
            configurations {
                a
                b.extendsFrom a
                c.extendsFrom b
            }
            configurations.c.resolve()
            configurations.a.exclude group: 'someGroup'
        """
        executer.withDeprecationChecksDisabled()

        when: succeeds()
        then: output.contains("Changed dependencies of configuration ':a' after it has been included in dependency resolution. This behaviour has been deprecated and is scheduled to be removed in Gradle 3.0")
        and:  output.contains("Changed dependencies of parent of configuration ':c' after it has been resolved. This behaviour has been deprecated and is scheduled to be removed in Gradle 3.0")
    }

    def "allows changing resolution stragegy of a configuration whose child has been resolved"() {
        buildFile << """
            configurations {
                a
                b.extendsFrom a
                c.extendsFrom b
            }
            configurations.c.resolve()
            configurations.a.resolutionStrategy.failOnVersionConflict()
            configurations.a.resolutionStrategy.force "org.utils:api:1.3"
            configurations.a.resolutionStrategy.forcedModules = [ "org.utils:api:1.4" ]
            configurations.a.resolutionStrategy.eachDependency {}
            configurations.a.resolutionStrategy.cacheDynamicVersionsFor 0, "seconds"
            configurations.a.resolutionStrategy.cacheChangingModulesFor 0, "seconds"
            configurations.a.resolutionStrategy.componentSelection.all {}
        """
        expect: succeeds()
    }

    def "warning is upgraded to an error when configuration is resolved"() {
        buildFile << """
            configurations {
                a
                b.extendsFrom a
            }
            configurations.b.resolve()
            configurations.a.exclude group: 'someGroup'
            configurations.a.resolve()
            configurations.a.exclude group: 'otherGroup'
        """
        executer.withDeprecationChecksDisabled()

        when: fails()
        then: output.contains("Changed dependencies of configuration ':a' after it has been included in dependency resolution. This behaviour has been deprecated and is scheduled to be removed in Gradle 3.0")
        and: output.contains("Changed dependencies of parent of configuration ':b' after it has been resolved. This behaviour has been deprecated and is scheduled to be removed in Gradle 3.0")
        and: failure.assertHasCause("Cannot change dependencies of configuration ':a' after it has been resolved.")
    }

    @Issue("GRADLE-3155")
    def "allows changing a configuration when the change does not affect a resolved child configuration"() {
        buildFile << """
            configurations {
                a
                b.extendsFrom a
                b.resolve()
                a.description = 'some conf'
            }
        """
        expect: succeeds()
    }

    @Issue("GRADLE-3155")
    def "allows changing a configuration that does not affect a resolved configuration"() {
        buildFile << """
            configurations {
                a
                b
                b.resolve()
            }
            dependencies { a "a:b:c" }
        """
        expect: succeeds()
    }

    def "allows changing a non-empty configuration that does not affect a resolved configuration"() {
        buildFile << """
            configurations {
                a
                b
            }
            dependencies { b files("some.jar") }
            configurations.b.resolve()
            dependencies { a "a:b:c" }
        """
        expect: succeeds()
    }

    def "warns about changing a dependency project's dependencies after included in resolution"() {
        settingsFile << "include 'api', 'impl'"
        buildFile << """
            allprojects {
                apply plugin: "java"
            }
            dependencies {
                compile project(":impl")
            }
            project(":impl") {
                dependencies {
                    compile project(":api")
                }
            }
            configurations.compile.resolve()
            project(":api") {
                dependencies {
                    compile files("some.jar")
                }
            }
"""
        executer.withDeprecationChecksDisabled()

        when: succeeds()
        then: output.contains("Changed dependencies of configuration ':api:compile' after it has been included in dependency resolution. This behaviour has been deprecated and is scheduled to be removed in Gradle 3.0")
    }
}
