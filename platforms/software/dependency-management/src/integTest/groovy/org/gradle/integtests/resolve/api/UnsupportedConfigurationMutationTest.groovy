/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.resolve.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.extensions.FluidDependenciesResolveTest
import org.gradle.test.fixtures.maven.MavenFileRepository
import spock.lang.Issue

@FluidDependenciesResolveTest
class UnsupportedConfigurationMutationTest extends AbstractIntegrationSpec {

    def "does not allow adding dependencies to a configuration that has been resolved"() {
        buildFile << """
            configurations { a }
            configurations.a.resolve()
            dependencies { a files("some.jar") }
        """
        when: fails()
        then: failure.assertHasCause("Cannot change dependencies of dependency configuration ':a' after it has been resolved")
    }

    def "does not allow adding artifacts to a configuration that has been resolved"() {
        buildFile << """
            configurations { a }
            configurations.a.resolve()
            artifacts { a file("some.jar") }
        """
        when: fails()
        then: failure.assertHasCause("Cannot change artifacts of dependency configuration ':a' after it has been resolved")
    }

    def "does not allow changing excludes on a configuration that has been resolved"() {
        buildFile << """
            configurations { a }
            configurations.a.resolve()
            configurations.a.exclude group: 'someGroup'
        """
        when: fails()
        then: failure.assertHasCause("Cannot change dependencies of dependency configuration ':a' after it has been resolved")
    }

    def "does not allow changing conflict resolution on a configuration that has been resolved"() {
        buildFile << """
            configurations { a }
            configurations.a.resolve()
            configurations.a.resolutionStrategy.failOnVersionConflict()
        """

        when: fails()
        then: failure.assertHasCause("Cannot change resolution strategy of dependency configuration ':a' after it has been resolved")
    }

    def "does not allow changing forced versions on a configuration that has been resolved"() {
        buildFile << """
            configurations { a }
            configurations.a.resolve()
            configurations.a.resolutionStrategy.force "org.utils:api:1.3"
        """

        when: fails()
        then: failure.assertHasCause("Cannot change resolution strategy of dependency configuration ':a' after it has been resolved")
    }

    def "does not allow changing cache policy on a configuration that has been resolved"() {
        buildFile << """
            configurations { a }
            configurations.a.resolve()
            configurations.a.resolutionStrategy.cacheChangingModulesFor 0, "seconds"
        """


        when: fails()
        then: failure.assertHasCause("Cannot change resolution strategy of dependency configuration ':a' after it has been resolved")
    }

    def "does not allow changing resolution rules on a configuration that has been resolved"() {
        buildFile << """
            configurations { a }
            configurations.a.resolve()
            configurations.a.resolutionStrategy.eachDependency {}
        """


        when: fails()
        then: failure.assertHasCause("Cannot change resolution strategy of dependency configuration ':a' after it has been resolved")
    }

    def "does not allow changing substitution rules on a configuration that has been resolved"() {
        buildFile << """
            configurations { a }
            configurations.a.resolve()
            configurations.a.resolutionStrategy.dependencySubstitution.all {}
        """


        when: fails()
        then: failure.assertHasCause("Cannot change resolution strategy of dependency configuration ':a' after it has been resolved")
    }

    def "does not allow changing component selection rules on a configuration that has been resolved"() {
        buildFile << """
            configurations { a }
            configurations.a.resolve()
            configurations.a.resolutionStrategy.componentSelection.all {}
        """


        when: fails()
        then: failure.assertHasCause("Cannot change resolution strategy of dependency configuration ':a' after it has been resolved")
    }

    @ToBeFixedForConfigurationCache(because = "task uses dependencies API")
    def "does not allow changing dependencies of a configuration that has been resolved for task dependencies"() {
        mavenRepo.module("org.utils", "extra", '1.5').publish()

        createDirs("api", "impl")
        settingsFile << "include 'api', 'impl'"
        buildFile << """
            allprojects {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                configurations {
                    compile
                    testCompile { extendsFrom compile }
                    'default' { extendsFrom compile }
                }
                configurations.all {
                    resolutionStrategy.assumeFluidDependencies()
                }
            }

            project(":api") {
                task addDependency {
                    doLast {
                        dependencies {
                            compile "org.utils:extra:1.5"
                        }
                    }
                }
            }

            project(":impl") {
                dependencies {
                    compile project(":api")
                }

                task addDependency {
                    doLast {
                        dependencies {
                            compile "org.utils:extra:1.5"
                        }
                    }
                }

                task modifyConfigDuringTaskExecution(dependsOn: [':impl:addDependency', configurations.compile]) {
                    doLast {
                        def files = configurations.compile.files
                        assert files*.name.sort() == ["api.jar", "extra-1.5.jar"]
                        assert files*.exists() == [ true, true ]
                    }
                }
                task modifyParentConfigDuringTaskExecution(dependsOn: [':impl:addDependency', configurations.testCompile]) {
                    doLast {
                        def files = configurations.testCompile.files
                        assert files*.name.sort() == ["api.jar", "extra-1.5.jar"]
                        assert files*.exists() == [ true, true ]
                    }
                }
                task modifyDependentConfigDuringTaskExecution(dependsOn: [':api:addDependency', configurations.compile]) {
                    doLast {
                        def files = configurations.compile.files
                        assert files*.name.sort() == ["api.jar"] // Late dependency is not honoured
                        assert files*.exists() == [ true ]
                    }
                }
            }
"""

        when:
        fails("impl:modifyConfigDuringTaskExecution")

        then:
        failure.assertHasCause("Cannot change dependencies of dependency configuration ':impl:compile' after it has been resolved.")

        when:
        fails("impl:modifyParentConfigDuringTaskExecution")

        then:
        failure.assertHasCause("Cannot change dependencies of dependency configuration ':impl:compile' after it has been included in dependency resolution.")

        when:
        fails("impl:modifyDependentConfigDuringTaskExecution")

        then:
        failure.assertHasCause("Cannot change dependencies of dependency configuration ':api:compile' after it has been included in dependency resolution.")
    }

    @ToBeFixedForConfigurationCache(because = "task uses dependencies API")
    def "does not allow changing artifacts of a configuration that has been resolved for task dependencies"() {
        mavenRepo.module("org.utils", "extra", '1.5').publish()

        createDirs("api", "impl")
        settingsFile << "include 'api', 'impl'"
        buildFile << """
            allprojects {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                configurations {
                    compile
                    testCompile { extendsFrom compile }
                    'default' { extendsFrom compile }
                }
                configurations.all {
                    resolutionStrategy.assumeFluidDependencies()
                }
            }

            project(":api") {
                task addArtifact {
                    doLast {
                        artifacts { compile file("some.jar") }
                    }
                }
            }

            project(":impl") {
                dependencies {
                    compile project(":api")
                }
                task addArtifact {
                    doLast {
                        artifacts { compile file("some.jar") }
                    }
                }

                task addArtifactToConfigDuringTaskExecution(dependsOn: [':impl:addArtifact', configurations.compile])
                task addArtifactToParentConfigDuringTaskExecution(dependsOn: [':impl:addArtifact', configurations.testCompile])
                task addArtifactToDependentConfigDuringTaskExecution(dependsOn: [':api:addArtifact', configurations.compile])
            }
"""

        when:
        fails("impl:addArtifactToConfigDuringTaskExecution")
        then:
        failure.assertHasCause("Cannot change artifacts of dependency configuration ':impl:compile' after it has been resolved.")

        when:
        fails("impl:addArtifactToParentConfigDuringTaskExecution")

        then:
        failure.assertHasCause("Cannot change artifacts of dependency configuration ':impl:compile' after it has been included in dependency resolution.")

        when:
        fails("impl:addArtifactToDependentConfigDuringTaskExecution")
        then:
        failure.assertHasCause("Cannot change artifacts of dependency configuration ':api:compile' after it has been included in dependency resolution.")
    }

    @Issue("GRADLE-3155")
    def "does not allow adding dependencies to a configuration whose child has been resolved"() {
        buildFile << """
            configurations {
                a
                b.extendsFrom a
                c.extendsFrom b
            }
            configurations.c.resolve()
            dependencies { a files("some.jar") }
        """



        when: fails()
        then: failure.assertHasCause("Cannot change dependencies of dependency configuration ':a' after it has been included in dependency resolution.")
    }

    @Issue("GRADLE-3155")
    def "does not allow adding artifacts to a configuration whose child has been resolved"() {
        buildFile << """
            configurations {
                a
                b.extendsFrom a
                c.extendsFrom b
            }
            configurations.c.resolve()
            artifacts { a file("some.jar") }
        """



        when: fails()
        then: failure.assertHasCause("Cannot change artifacts of dependency configuration ':a' after it has been included in dependency resolution.")
    }

    @Issue("GRADLE-3155")
    def "does not allow changing a configuration whose child has been resolved"() {
        buildFile << """
            configurations {
                a
                b.extendsFrom a
                c.extendsFrom b
            }
            configurations.c.resolve()
            configurations.a.exclude group: 'someGroup'
        """



        when: fails()
        then: failure.assertHasCause("Cannot change dependencies of dependency configuration ':a' after it has been included in dependency resolution.")
    }

    def "allows changing resolution strategy of a configuration whose child has been resolved"() {
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

    def "fails when configuration is resolved"() {
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



        when: fails()
        then: failure.assertHasCause("Cannot change dependencies of dependency configuration ':a' after it has been included in dependency resolution.")
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

    def "does not allow changing a dependency project's dependencies after included in resolution"() {
        createDirs("api", "impl")
        settingsFile << "include 'api', 'impl'"
        buildFile << """
            allprojects {
                configurations {
                    compile
                    'default' { extendsFrom compile }
                }
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


        when: fails()
        then: failure.assertHasCause("Cannot change dependencies of dependency configuration ':api:compile' after it has been included in dependency resolution.")
    }

    @Issue("GRADLE-3297")
    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "using offline flag does not emit deprecation warning when child configuration is explicitly resolved"() {
        def repo = new MavenFileRepository(file("repo"))
        repo.module('org.test', 'moduleA', '1.0').publish()

        buildFile << """
configurations {
    parentConfig
    childConfig.extendsFrom parentConfig
}
dependencies {
  // Parent must have at least 1 dependency to force resolution
  parentConfig "org.test:moduleA:1.0"
}
repositories {
    maven { url '$repo.uri' }
}

task resolveChildFirst {
    doLast {
        configurations.childConfig.resolve()
        configurations.parentConfig.resolve()
    }
}
        """

        when:
        executer.withArguments("--offline")

        then:
        succeeds("resolveChildFirst")
    }

    def "does not allow adding attributes to a configuration that has been resolved"() {
        buildFile << """
            configurations { a }
            configurations.a.resolve()
            configurations.a.attributes { attribute(Attribute.of('foo', String), 'bar') }
        """
        when: fails()
        then: failure.assertHasCause("Cannot change attributes of dependency configuration ':a' after it has been resolved")
    }

    def "cannot change the configuration role (#code) after it has been resolved"() {
        buildFile << """
            configurations { a }
            configurations.a.resolve()
            ${code}
        """
        when:
        fails()

        then:
        failure.assertHasCause("Cannot change usage of dependency configuration ':a' after it has been resolved")

        where:
        role                      | code
        'consume or publish only' | 'configurations.a.canBeResolved = false'
        'query or resolve only'   | 'configurations.a.canBeConsumed = false'
        'dependency scope'        | 'configurations.a.canBeResolved = false; configurations.a.canBeConsumed = false'

    }
}
