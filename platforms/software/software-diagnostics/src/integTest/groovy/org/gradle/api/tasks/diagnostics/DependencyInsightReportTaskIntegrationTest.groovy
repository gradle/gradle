/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.tasks.diagnostics


import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.integtests.resolve.locking.LockfileFixture
import org.gradle.util.GradleVersion
import spock.lang.Issue

import static org.gradle.integtests.fixtures.SuggestionsMessages.repositoryHint

class DependencyInsightReportTaskIntegrationTest extends AbstractIntegrationSpec {
    def jvmVersion = JavaVersion.current().majorVersion

    def setup() {
        executer.requireOwnGradleUserHomeDir()
        settingsFile << """
            rootProject.name = 'insight-test'
        """
        new ResolveTestFixture(buildFile).addDefaultVariantDerivationStrategy()
    }

    def "requires use of configuration flag if Java plugin isn't applied"() {
        given:
        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:top:1.0'
            }
        """

        when:
        def failure = runAndFail("dependencyInsight", "--dependency", "unknown")

        then:
        failure.assertHasCause("Dependency insight report cannot be generated because the input configuration was not specified.")
    }

    def "indicates that requested dependency cannot be found for default configuration"() {
        given:
        mavenRepo.module("org", "leaf1").publish()
        mavenRepo.module("org", "leaf2").publish()
        mavenRepo.module("org", "middle").dependsOnModules("leaf1", "leaf2").publish()
        mavenRepo.module("org", "top").dependsOnModules("middle", "leaf2").publish()

        buildFile << """
            apply plugin: 'java'

            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:top:1.0'
            }
        """

        when:
        run "dependencyInsight", "--dependency", "unknown"

        then:
        outputContains """
No dependencies matching given input were found in configuration ':compileClasspath'
"""
    }

    def "indicates that requested dependency cannot be found for custom configuration"() {
        given:
        mavenRepo.module("org", "leaf1").publish()
        mavenRepo.module("org", "leaf2").publish()
        mavenRepo.module("org", "middle").dependsOnModules("leaf1", "leaf2").publish()
        mavenRepo.module("org", "top").dependsOnModules("middle", "leaf2").publish()

        buildFile << """
            apply plugin: 'java'

            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:top:1.0'
            }
        """

        when:
        run "dependencyInsight", "--dependency", "unknown", "--configuration", "conf"

        then:
        outputContains """
No dependencies matching given input were found in configuration ':conf'
"""
    }

    def "shows basic single tree with repeated dependency"() {
        given:
        mavenRepo.module("org", "leaf1").publish()
        mavenRepo.module("org", "leaf2").publish()

        mavenRepo.module("org", "middle").dependsOnModules("leaf1", "leaf2").publish()

        mavenRepo.module("org", "top").dependsOnModules("middle", "leaf2").publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:top:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
                showingAllVariants = false
                setDependencySpec { it.requested.module == 'leaf2' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        outputContains """
org:leaf2:1.0
  Variant runtime:
    | Attribute Name             | Provided     | Requested |
    |----------------------------|--------------|-----------|
    | org.gradle.category        | library      |           |
    | org.gradle.libraryelements | jar          |           |
    | org.gradle.status          | release      |           |
    | org.gradle.usage           | java-runtime |           |

org:leaf2:1.0
+--- org:middle:1.0
|    \\--- org:top:1.0
|         \\--- conf
\\--- org:top:1.0 (*)
"""
    }

    def "basic dependency insight with conflicting versions"() {
        given:
        mavenRepo.module("org", "leaf1").publish()
        mavenRepo.module("org", "leaf2").publish()
        mavenRepo.module("org", "leaf2", "1.5").publish()
        mavenRepo.module("org", "leaf2", "2.5").publish()
        mavenRepo.module("org", "leaf3").publish()
        mavenRepo.module("org", "leaf4").publish()

        mavenRepo.module("org", "middle1").dependsOnModules('leaf1', 'leaf2').publish()
        mavenRepo.module("org", "middle2").dependsOnModules('leaf3', 'leaf4').publish()
        mavenRepo.module("org", "middle3").dependsOnModules('leaf2').publish()

        mavenRepo.module("org", "toplevel").dependsOnModules("middle1", "middle2").publish()

        mavenRepo.module("org", "toplevel2").dependsOn("org", "leaf2", "1.5").publish()
        mavenRepo.module("org", "toplevel3").dependsOn("org", "leaf2", "2.5").publish()

        mavenRepo.module("org", "toplevel4").dependsOnModules("middle3").publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }

            configurations {
                conf
            }
            dependencies {
                conf 'org:toplevel:1.0', 'org:toplevel2:1.0', 'org:toplevel3:1.0', 'org:toplevel4:1.0'
            }
        """

        when:
        run "dependencyInsight", "--dependency", "leaf2", "--configuration", "conf"

        then:
        outputContains """
Task :dependencyInsight
org:leaf2:2.5
  Variant runtime:
    | Attribute Name             | Provided     | Requested |
    |----------------------------|--------------|-----------|
    | org.gradle.category        | library      |           |
    | org.gradle.libraryelements | jar          |           |
    | org.gradle.status          | release      |           |
    | org.gradle.usage           | java-runtime |           |
   Selection reasons:
      - By conflict resolution: between versions 2.5, 1.5 and 1.0

org:leaf2:2.5
\\--- org:toplevel3:1.0
     \\--- conf

org:leaf2:1.0 -> 2.5
+--- org:middle1:1.0
|    \\--- org:toplevel:1.0
|         \\--- conf
\\--- org:middle3:1.0
     \\--- org:toplevel4:1.0
          \\--- conf

org:leaf2:1.5 -> 2.5
\\--- org:toplevel2:1.0
     \\--- conf
"""
    }

    def "can limit the report to one path to each dependency"() {
        given:
        mavenRepo.with {
            def leaf = module('org', 'leaf').publish()
            def a = module('org', 'a').dependsOn(leaf).publish()
            def b = module('org', 'b').dependsOn(a).publish()
            def c = module('org', 'c').dependsOn(a).publish()
            def d = module('org', 'd').dependsOn(leaf).publish()
        }
        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }

            configurations {
                conf
            }
            dependencies {
                conf 'org:b:1.0'
                conf 'org:c:1.0'
                conf 'org:d:1.0'
            }
        """

        when:
        run "dependencyInsight", "--dependency", "leaf", "--configuration", "conf"

        then:
        outputContains """
org:leaf:1.0
  Variant runtime:
    | Attribute Name             | Provided     | Requested |
    |----------------------------|--------------|-----------|
    | org.gradle.category        | library      |           |
    | org.gradle.libraryelements | jar          |           |
    | org.gradle.status          | release      |           |
    | org.gradle.usage           | java-runtime |           |

org:leaf:1.0
+--- org:a:1.0
|    +--- org:b:1.0
|    |    \\--- conf
|    \\--- org:c:1.0
|         \\--- conf
\\--- org:d:1.0
     \\--- conf
"""

        when:
        run "dependencyInsight", "--dependency", "leaf", "--configuration", "conf", "--single-path"

        then:
        outputContains """
org:leaf:1.0
  Variant runtime:
    | Attribute Name             | Provided     | Requested |
    |----------------------------|--------------|-----------|
    | org.gradle.category        | library      |           |
    | org.gradle.libraryelements | jar          |           |
    | org.gradle.status          | release      |           |
    | org.gradle.usage           | java-runtime |           |

org:leaf:1.0
\\--- org:a:1.0
     \\--- org:b:1.0
          \\--- conf
"""
    }

    @Issue("https://github.com/gradle/gradle/issues/24356")
    def "displays information about conflicting modules when failOnVersionConflict is used"() {
        given:
        mavenRepo.module("org", "leaf1").publish()
        mavenRepo.module("org", "leaf2").publish()
        mavenRepo.module("org", "leaf2", "1.5").publish()
        mavenRepo.module("org", "leaf2", "2.5").publish()
        mavenRepo.module("org", "leaf3").publish()
        mavenRepo.module("org", "leaf4").publish()

        mavenRepo.module("org", "middle1").dependsOnModules('leaf1', 'leaf2').publish()
        mavenRepo.module("org", "middle2").dependsOnModules('leaf3', 'leaf4').publish()
        mavenRepo.module("org", "middle3").dependsOnModules('leaf2').publish()

        mavenRepo.module("org", "toplevel").dependsOnModules("middle1", "middle2").publish()

        mavenRepo.module("org", "toplevel2").dependsOn("org", "leaf2", "1.5").publish()
        mavenRepo.module("org", "toplevel3").dependsOn("org", "leaf2", "2.5").publish()

        mavenRepo.module("org", "toplevel4").dependsOnModules("middle3").publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }

            configurations {
                conf {
                    resolutionStrategy.failOnVersionConflict()
                }
            }
            dependencies {
                conf 'org:toplevel:1.0', 'org:toplevel2:1.0', 'org:toplevel3:1.0', 'org:toplevel4:1.0'
            }
        """

        when:
        run "dependencyInsight", "--dependency", "leaf2", "--configuration", "conf"

        then:
        outputContains """Dependency resolution failed because of conflict on the following module:
   - org:leaf2 between versions 2.5, 1.5 and 1.0

org:leaf2:2.5
  Variant runtime:
    | Attribute Name             | Provided     | Requested |
    |----------------------------|--------------|-----------|
    | org.gradle.category        | library      |           |
    | org.gradle.libraryelements | jar          |           |
    | org.gradle.status          | release      |           |
    | org.gradle.usage           | java-runtime |           |
   Selection reasons:
      - By conflict resolution: between versions 2.5, 1.5 and 1.0

org:leaf2:2.5
\\--- org:toplevel3:1.0
     \\--- conf

org:leaf2:1.0 -> 2.5
+--- org:middle1:1.0
|    \\--- org:toplevel:1.0
|         \\--- conf
\\--- org:middle3:1.0
     \\--- org:toplevel4:1.0
          \\--- conf

org:leaf2:1.5 -> 2.5
\\--- org:toplevel2:1.0
     \\--- conf
"""
    }

    @Issue("https://github.com/gradle/gradle/issues/24356")
    def "displays information about conflicting modules when failOnVersionConflict is used and afterResolve is used"() {
        given:
        mavenRepo.module("org", "leaf1").publish()
        mavenRepo.module("org", "leaf2").publish()
        mavenRepo.module("org", "leaf2", "1.5").publish()
        mavenRepo.module("org", "leaf2", "2.5").publish()
        mavenRepo.module("org", "leaf3").publish()
        mavenRepo.module("org", "leaf4").publish()

        mavenRepo.module("org", "middle1").dependsOnModules('leaf1', 'leaf2').publish()
        mavenRepo.module("org", "middle2").dependsOnModules('leaf3', 'leaf4').publish()
        mavenRepo.module("org", "middle3").dependsOnModules('leaf2').publish()

        mavenRepo.module("org", "toplevel").dependsOnModules("middle1", "middle2").publish()

        mavenRepo.module("org", "toplevel2").dependsOn("org", "leaf2", "1.5").publish()
        mavenRepo.module("org", "toplevel3").dependsOn("org", "leaf2", "2.5").publish()

        mavenRepo.module("org", "toplevel4").dependsOnModules("middle3").publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }

            configurations {
                conf {
                    resolutionStrategy.failOnVersionConflict()
                    incoming.afterResolve {
                        // If executed, the below will cause the resolution failure on version conflict to be thrown, breaking dependency insight
                        it.artifacts.artifacts
                    }
                }
            }
            dependencies {
                conf 'org:toplevel:1.0', 'org:toplevel2:1.0', 'org:toplevel3:1.0', 'org:toplevel4:1.0'
            }
        """

        when:
        run "dependencyInsight", "--dependency", "leaf2", "--configuration", "conf"

        then:
        outputContains """Dependency resolution failed because of conflict on the following module:
   - org:leaf2 between versions 2.5, 1.5 and 1.0

org:leaf2:2.5
  Variant runtime:
    | Attribute Name             | Provided     | Requested |
    |----------------------------|--------------|-----------|
    | org.gradle.category        | library      |           |
    | org.gradle.libraryelements | jar          |           |
    | org.gradle.status          | release      |           |
    | org.gradle.usage           | java-runtime |           |
   Selection reasons:
      - By conflict resolution: between versions 2.5, 1.5 and 1.0

org:leaf2:2.5
\\--- org:toplevel3:1.0
     \\--- conf

org:leaf2:1.0 -> 2.5
+--- org:middle1:1.0
|    \\--- org:toplevel:1.0
|         \\--- conf
\\--- org:middle3:1.0
     \\--- org:toplevel4:1.0
          \\--- conf

org:leaf2:1.5 -> 2.5
\\--- org:toplevel2:1.0
     \\--- conf
"""
    }

    def "displays a dependency insight report even if locks are out of date"() {
        def lockfileFixture = new LockfileFixture(testDirectory: testDirectory)
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'foo', '1.1').publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
}

repositories {
    maven {
        name = 'repo'
        url = '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf 'org:foo:1.+'
}
"""

        lockfileFixture.createLockfile('lockedConf', ['org:bar:1.0'])

        when:
        succeeds 'dependencyInsight', '--configuration', 'lockedConf', '--dependency', 'foo'

        then:
        outputContains """org:foo:1.1 FAILED
   Selection reasons:
      - By constraint: Dependency locking
   Failures:
      - Dependency lock state out of date:
          - Resolved 'org:foo:1.1' which is not part of the dependency lock state

org:foo:1.1 FAILED
\\--- lockedConf

org:foo:1.+ -> 1.1
\\--- lockedConf
"""
    }

    def "displays a dependency insight report even if locks are out of date because of new constraint"() {
        def lockfileFixture = new LockfileFixture(testDirectory: testDirectory)
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'foo', '1.1').publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
}

repositories {
    maven {
        name = 'repo'
        url = '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    constraints {
        lockedConf('org:foo:1.1')
    }
    lockedConf 'org:foo:1.+'
}
"""

        lockfileFixture.createLockfile('lockedConf', ['org:foo:1.0'])

        when:
        succeeds 'dependencyInsight', '--configuration', 'lockedConf', '--dependency', 'foo'

        then:
        outputContains """
org:foo:1.0 FAILED
   Selection reasons:
      - By constraint: Dependency version enforced by Dependency Locking
   Failures:
      - Could not resolve org:foo:{strictly 1.0}.
          - Cannot find a version of 'org:foo' that satisfies the version constraints:
               Dependency path ':insight-test:unspecified' --> 'org:foo:1.+'
               Constraint path ':insight-test:unspecified' --> 'org:foo:1.1'
               Constraint path ':insight-test:unspecified' --> 'org:foo:{strictly 1.0}' because of the following reason: Dependency version enforced by Dependency Locking

org:foo:{strictly 1.0} -> 1.0 FAILED
\\--- lockedConf

org:foo:1.1 (by constraint) FAILED
   Failures:
      - Could not resolve org:foo:1.1. (already reported)

org:foo:1.1 FAILED
\\--- lockedConf

org:foo:1.+ FAILED
   Failures:
      - Could not resolve org:foo:1.+. (already reported)

org:foo:1.+ FAILED
\\--- lockedConf
"""
    }

    def "shows forced version and substitution equivalent to force"() {
        given:
        mavenRepo.module("org", "leaf", "1.0").publish()
        mavenRepo.module("org", "leaf", "2.0").publish()

        mavenRepo.module("org", "foo", "1.0").dependsOn('org', 'leaf', '1.0').publish()
        mavenRepo.module("org", "bar", "1.0").dependsOn('org', 'leaf', '2.0').publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                conf
                forced {
                    extendsFrom conf
                }
                substituted {
                    extendsFrom conf
                }
            }
            configurations.forced.resolutionStrategy.force 'org:leaf:1.0'
            configurations.substituted.resolutionStrategy.dependencySubstitution {
                substitute module('org:leaf') using module('org:leaf:1.0')
            }
            dependencies {
                conf 'org:foo:1.0', 'org:bar:1.0'
            }
        """

        when:
        run "dependencyInsight", "--configuration", "forced", "--dependency", "leaf"

        then:
        outputContains """
org:leaf:1.0 (forced)
  Variant runtime:
    | Attribute Name             | Provided     | Requested |
    |----------------------------|--------------|-----------|
    | org.gradle.category        | library      |           |
    | org.gradle.libraryelements | jar          |           |
    | org.gradle.status          | release      |           |
    | org.gradle.usage           | java-runtime |           |

org:leaf:1.0
\\--- org:foo:1.0
     \\--- forced

org:leaf:2.0 -> 1.0
\\--- org:bar:1.0
     \\--- forced
"""

        when:
        run "dependencyInsight", "--configuration", "substituted", "--dependency", "leaf"

        then:
        outputContains """
org:leaf:1.0 (selected by rule)
  Variant runtime:
    | Attribute Name             | Provided     | Requested |
    |----------------------------|--------------|-----------|
    | org.gradle.category        | library      |           |
    | org.gradle.libraryelements | jar          |           |
    | org.gradle.status          | release      |           |
    | org.gradle.usage           | java-runtime |           |

org:leaf:1.0
\\--- org:foo:1.0
     \\--- substituted

org:leaf:2.0 -> 1.0
\\--- org:bar:1.0
     \\--- substituted
"""
    }

    def "shows multiple outgoing dependencies"() {
        given:
        ivyRepo.module("org", "leaf", "1.0").publish()
        ivyRepo.module("org", "middle", "1.0")
            .dependsOn("org", "leaf", "1.0")
            .dependsOn("org", "leaf", "[1.0,2.0]")
            .dependsOn("org", "leaf", "latest.integration")
            .publish()
        ivyRepo.module("org", "top", "1.0")
            .dependsOn("org", "middle", "1.0")
            .dependsOn("org", "middle", "[1.0,2.0]")
            .dependsOn("org", "middle", "latest.integration")
            .publish()

        buildFile << """
            repositories {
                ivy { url = "${ivyRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:top:1.0'
                conf 'org:top:[1.0,2.0]'
                conf 'org:top:latest.integration'
            }
            task insight(type: DependencyInsightReportTask) {
                showingAllVariants = false
                setDependencySpec { it.requested.module == 'leaf' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        outputContains """
org:leaf:1.0
  Variant default:
    | Attribute Name    | Provided    | Requested |
    |-------------------|-------------|-----------|
    | org.gradle.status | integration |           |

org:leaf:1.0
\\--- org:middle:1.0
     \\--- org:top:1.0
          \\--- conf

org:leaf:[1.0,2.0] -> 1.0
\\--- org:middle:1.0
     \\--- org:top:1.0
          \\--- conf

org:leaf:latest.integration -> 1.0
\\--- org:middle:1.0
     \\--- org:top:1.0
          \\--- conf
"""
    }

    def "shows version selected by multiple rules"() {
        given:
        mavenRepo.module("org", "bar", "2.0").publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                conf {
                    resolutionStrategy {
                        eachDependency { it.useVersion('1.0') }
                        eachDependency { it.useVersion('2.0'); it.because("RULE 2") }
                        dependencySubstitution {
                            substitute module('org:foo') because "SUBSTITUTION 1" using module('org:foo:3.0')
                            substitute module('org:foo') because "SUBSTITUTION 2" using module('org:bar:2.0')
                            all {
                                it.useTarget('org:bar:2.0', "SUBSTITUTION 3")
                            }
                        }
                        force('org:foo:1.1')
                    }
                }
            }
            dependencies {
                conf 'org:foo:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
                showingAllVariants = false
                configuration = configurations.conf
                setDependencySpec { true }
            }
        """

        when:
        run "insight"

        then:
        outputContains """
org:bar:2.0
  Variant runtime:
    | Attribute Name             | Provided     | Requested |
    |----------------------------|--------------|-----------|
    | org.gradle.category        | library      |           |
    | org.gradle.libraryelements | jar          |           |
    | org.gradle.status          | release      |           |
    | org.gradle.usage           | java-runtime |           |
   Selection reasons:
      - Forced
      - Selected by rule
      - Selected by rule: RULE 2
      - Selected by rule: SUBSTITUTION 1
      - Selected by rule: SUBSTITUTION 2
      - Selected by rule: SUBSTITUTION 3

org:foo:1.0 -> org:bar:2.0
\\--- conf
"""
    }

    def "shows version and reason when chosen by dependency resolve rule"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "foo", "2.0").publish()
        mavenRepo.module("org", "bar", "1.0").publish()
        mavenRepo.module("org.test", "bar", "2.0").publish()
        mavenRepo.module("org", "baz", "1.0").publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                conf {
                    resolutionStrategy.eachDependency {
                        switch (it.requested.name) {
                           case 'foo':
                              it.because('because I am in control').useVersion('2.0')
                              break
                           case 'bar':
                              it.because('why not?').useTarget('org.test:bar:2.0')
                              break
                           default:
                              useVersion(it.requested.version)
                        }
                    }
                }
            }
            dependencies {
                conf 'org:foo:1.0'
                conf 'org:bar:1.0'
                conf 'org:baz:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
                showingAllVariants = false
                configuration = configurations.conf
                setDependencySpec { true }
            }
        """

        when:
        run "insight"

        then:
        outputContains """
org.test:bar:2.0
  Variant runtime:
    | Attribute Name             | Provided     | Requested |
    |----------------------------|--------------|-----------|
    | org.gradle.category        | library      |           |
    | org.gradle.libraryelements | jar          |           |
    | org.gradle.status          | release      |           |
    | org.gradle.usage           | java-runtime |           |
   Selection reasons:
      - Selected by rule: why not?

org:bar:1.0 -> org.test:bar:2.0
\\--- conf

org:baz:1.0 (selected by rule)
  Variant runtime:
    | Attribute Name             | Provided     | Requested |
    |----------------------------|--------------|-----------|
    | org.gradle.category        | library      |           |
    | org.gradle.libraryelements | jar          |           |
    | org.gradle.status          | release      |           |
    | org.gradle.usage           | java-runtime |           |

org:baz:1.0
\\--- conf

org:foo:2.0
  Variant runtime:
    | Attribute Name             | Provided     | Requested |
    |----------------------------|--------------|-----------|
    | org.gradle.category        | library      |           |
    | org.gradle.libraryelements | jar          |           |
    | org.gradle.status          | release      |           |
    | org.gradle.usage           | java-runtime |           |
   Selection reasons:
      - Selected by rule: because I am in control

org:foo:1.0 -> 2.0
\\--- conf
"""
    }


    def "shows version and reason with dependency substitution"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "bar", "1.0").publish()
        mavenRepo.module("org", "baz", "2.0").publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
               conf {
                  resolutionStrategy.dependencySubstitution {
                     substitute module('org:foo') because 'foo superseded by bar' using module('org:bar:1.0')
                     substitute module('org:baz') using module('org:baz:2.0')
                  }
               }
            }
            dependencies {
                conf 'org:foo:1.0'
                conf 'org:baz:1.1'
            }
            task insight(type: DependencyInsightReportTask) {
                showingAllVariants = false
                configuration = configurations.conf
                setDependencySpec { true }
            }
        """

        when:
        run "insight"

        then:
        outputContains """
org:baz:2.0 (selected by rule)
  Variant runtime:
    | Attribute Name             | Provided     | Requested |
    |----------------------------|--------------|-----------|
    | org.gradle.category        | library      |           |
    | org.gradle.libraryelements | jar          |           |
    | org.gradle.status          | release      |           |
    | org.gradle.usage           | java-runtime |           |

org:baz:1.1 -> 2.0
\\--- conf

org:bar:1.0
  Variant runtime:
    | Attribute Name             | Provided     | Requested |
    |----------------------------|--------------|-----------|
    | org.gradle.category        | library      |           |
    | org.gradle.libraryelements | jar          |           |
    | org.gradle.status          | release      |           |
    | org.gradle.usage           | java-runtime |           |
   Selection reasons:
      - Selected by rule: foo superseded by bar

org:foo:1.0 -> org:bar:1.0
\\--- conf
"""
    }

    def "shows substituted modules"() {
        given:
        mavenRepo.module("org", "new-leaf", "77").publish()

        mavenRepo.module("org", "foo", "2.0").dependsOn('org', 'leaf', '1.0').publish()
        mavenRepo.module("org", "bar", "1.0").dependsOn('org', 'leaf', '2.0').publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                conf {
                    resolutionStrategy.dependencySubstitution {
                        substitute module('org:leaf') using module('org:new-leaf:77')
                        substitute module('org:foo') using module('org:foo:2.0')
                    }
                }
            }
            dependencies {
                conf 'org:foo:1.0', 'org:bar:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
                showingAllVariants = false
                configuration = configurations.conf
                setDependencySpec { it.requested.module == 'leaf' }
            }
        """

        when:
        run "insight"

        then:
        outputContains """
org:new-leaf:77 (selected by rule)
  Variant runtime:
    | Attribute Name             | Provided     | Requested |
    |----------------------------|--------------|-----------|
    | org.gradle.category        | library      |           |
    | org.gradle.libraryelements | jar          |           |
    | org.gradle.status          | release      |           |
    | org.gradle.usage           | java-runtime |           |

org:leaf:1.0 -> org:new-leaf:77
\\--- org:foo:2.0
     \\--- conf (requested org:foo:1.0)

org:leaf:2.0 -> org:new-leaf:77
\\--- org:bar:1.0
     \\--- conf
"""
    }

    def "shows substituted modules with a custom description"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "foo", "2.0").publish()
        mavenRepo.module("org", "bar", "1.0").publish()
        mavenRepo.module("org", "bar", "2.0").publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                conf {
                    resolutionStrategy.dependencySubstitution {
                        substitute module('org:foo:1.0') because('I want to') using module('org:foo:2.0')
                        substitute module('org:bar:1.0') because('I am not sure I want to explain') using module('org:bar:2.0')
                    }
                }
            }
            dependencies {
                conf 'org:foo:1.0', 'org:bar:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
                showingAllVariants = false
                configuration = configurations.conf
                setDependencySpec { true }
            }
        """

        when:
        run "insight"

        then:
        outputContains """org:bar:2.0
  Variant runtime:
    | Attribute Name             | Provided     | Requested |
    |----------------------------|--------------|-----------|
    | org.gradle.category        | library      |           |
    | org.gradle.libraryelements | jar          |           |
    | org.gradle.status          | release      |           |
    | org.gradle.usage           | java-runtime |           |
   Selection reasons:
      - Selected by rule: I am not sure I want to explain

org:bar:1.0 -> 2.0
\\--- conf

org:foo:2.0
  Variant runtime:
    | Attribute Name             | Provided     | Requested |
    |----------------------------|--------------|-----------|
    | org.gradle.category        | library      |           |
    | org.gradle.libraryelements | jar          |           |
    | org.gradle.status          | release      |           |
    | org.gradle.usage           | java-runtime |           |
   Selection reasons:
      - Selected by rule: I want to

org:foo:1.0 -> 2.0
\\--- conf
"""
    }

    def "shows version resolved from dynamic selectors"() {
        given:
        ivyRepo.module("org", "leaf", "1.6").publish()
        ivyRepo.module("org", "top", "1.0")
            .dependsOn("org", "leaf", "[1.5,1.9]")
            .dependsOn("org", "leaf", "latest.integration")
            .dependsOn("org", "leaf", "1.+")
            .publish()

        buildFile << """
            repositories {
                ivy { url = "${ivyRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:top:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
                showingAllVariants = false
                setDependencySpec { it.requested.module == 'leaf' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        outputContains """
org:leaf:1.6
  Variant default:
    | Attribute Name    | Provided    | Requested |
    |-------------------|-------------|-----------|
    | org.gradle.status | integration |           |

org:leaf:1.+ -> 1.6
\\--- org:top:1.0
     \\--- conf

org:leaf:[1.5,1.9] -> 1.6
\\--- org:top:1.0
     \\--- conf

org:leaf:latest.integration -> 1.6
\\--- org:top:1.0
     \\--- conf
"""
    }

    def "forced version matches the conflict resolution"() {
        given:
        mavenRepo.module("org", "leaf", "1.0").publish()
        mavenRepo.module("org", "leaf", "2.0").publish()

        mavenRepo.module("org", "foo", "1.0").dependsOn('org', 'leaf', '1.0').publish()
        mavenRepo.module("org", "bar", "1.0").dependsOn('org', 'leaf', '2.0').publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            configurations.conf.resolutionStrategy.force 'org:leaf:2.0'
            dependencies {
                conf 'org:foo:1.0', 'org:bar:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
                showingAllVariants = false
                configuration = configurations.conf
                setDependencySpec { it.requested.module == 'leaf' }
            }
        """

        when:
        run "insight"

        then:
        outputContains """
org:leaf:2.0 (forced)
  Variant runtime:
    | Attribute Name             | Provided     | Requested |
    |----------------------------|--------------|-----------|
    | org.gradle.category        | library      |           |
    | org.gradle.libraryelements | jar          |           |
    | org.gradle.status          | release      |           |
    | org.gradle.usage           | java-runtime |           |

org:leaf:2.0
\\--- org:bar:1.0
     \\--- conf

org:leaf:1.0 -> 2.0
\\--- org:foo:1.0
     \\--- conf
"""
    }

    def "forced version does not match anything in the graph"() {
        given:
        mavenRepo.module("org", "leaf", "1.0").publish()
        mavenRepo.module("org", "leaf", "2.0").publish()
        mavenRepo.module("org", "leaf", "1.5").publish()

        mavenRepo.module("org", "foo", "1.0").dependsOn('org', 'leaf', '1.0').publish()
        mavenRepo.module("org", "bar", "1.0").dependsOn('org', 'leaf', '2.0').publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            configurations.conf.resolutionStrategy.force 'org:leaf:1.5'
            dependencies {
                conf 'org:foo:1.0', 'org:bar:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
                showingAllVariants = false
                configuration = configurations.conf
                setDependencySpec { it.requested.module == 'leaf' }
            }
        """

        when:
        run "insight"

        then:
        outputContains """
org:leaf:1.5 (forced)
  Variant runtime:
    | Attribute Name             | Provided     | Requested |
    |----------------------------|--------------|-----------|
    | org.gradle.category        | library      |           |
    | org.gradle.libraryelements | jar          |           |
    | org.gradle.status          | release      |           |
    | org.gradle.usage           | java-runtime |           |

org:leaf:1.0 -> 1.5
\\--- org:foo:1.0
     \\--- conf

org:leaf:2.0 -> 1.5
\\--- org:bar:1.0
     \\--- conf
"""
    }

    def "forced version combined with constraint"() {
        given:
        mavenRepo.module("org", "leaf", "2.0").publish()
        mavenRepo.module("org", "foo", "1.0").dependsOn('org', 'leaf', '1.0').publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            configurations.conf.resolutionStrategy.force 'org:leaf:2.0'
            dependencies {
                conf 'org:foo:1.0'
                constraints {
                    conf('org:leaf:1.4')
                }
            }
            task insight(type: DependencyInsightReportTask) {
                showingAllVariants = false
                configuration = configurations.conf
                setDependencySpec { it.requested.module == 'leaf' }
            }
        """

        when:
        run "insight"

        then:
        outputContains """
org:leaf:2.0
  Variant runtime:
    | Attribute Name             | Provided     | Requested |
    |----------------------------|--------------|-----------|
    | org.gradle.category        | library      |           |
    | org.gradle.libraryelements | jar          |           |
    | org.gradle.status          | release      |           |
    | org.gradle.usage           | java-runtime |           |
   Selection reasons:
      - Forced
      - By constraint

org:leaf:1.0 -> 2.0
\\--- org:foo:1.0
     \\--- conf

org:leaf:1.4 -> 2.0
\\--- conf
"""
    }

    def "shows decent failure when inputs missing"() {
        given:
        buildFile << """
            task insight(type: DependencyInsightReportTask) {
                showingAllVariants = false
                setDependencySpec { it.requested.module == 'leaf2' }
            }
        """

        when:
        def failure = runAndFail("insight")

        then:
        failure.assertHasCause("Dependency insight report cannot be generated because the input configuration was not specified.")
    }

    def "informs that there are no dependencies"() {
        given:
        buildFile << """
            configurations {
                conf
            }
            task insight(type: DependencyInsightReportTask) {
                showingAllVariants = false
                setDependencySpec { it.requested.module == 'whatever' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        outputContains("No dependencies matching given input were found")
    }

    def "informs that nothing matches the input dependency"() {
        given:
        mavenRepo.module("org", "top").publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:top:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
                showingAllVariants = false
                setDependencySpec { it.requested.module == 'foo.unknown' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        outputContains("No dependencies matching given input were found")
    }

    def "marks modules that can't be resolved as 'FAILED'"() {
        given:
        mavenRepo.module("org", "top").dependsOnModules("middle").publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:top:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
                showingAllVariants = false
                setDependencySpec { it.requested.module == 'middle' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        outputContains """
org:middle:1.0 FAILED
\\--- org:top:1.0
     \\--- conf
"""
    }

    def "marks modules that can't be resolved after forcing a different version as 'FAILED'"() {
        given:
        mavenRepo.module("org", "top").dependsOn("org", "middle", "1.0").publish()
        mavenRepo.module("org", "middle", "1.0").publish()
        def middleModule2 = mavenRepo.module("org", "middle", "2.0")

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                conf {
                    resolutionStrategy {
                        force "org:middle:2.0"
                    }
                }
            }
            dependencies {
                conf 'org:top:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
                showingAllVariants = false
                setDependencySpec { it.requested.module == 'middle' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        outputContains """org:middle:2.0 (forced) FAILED
   Failures:
      - Could not find org:middle:2.0.
        Searched in the following locations:
          - ${middleModule2.pomFile.displayUri}
        ${repositoryHint("Maven POM")}

org:middle:1.0 -> 2.0 FAILED
\\--- org:top:1.0
     \\--- conf
"""
    }

    def "marks modules that can't be resolved after conflict resolution as 'FAILED'"() {
        given:
        mavenRepo.module("org", "top").dependsOn("org", "middle", "1.0").publish()
        mavenRepo.module("org", "middle", "1.0").publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:top:1.0'
                conf 'org:middle:2.0'
            }
            task insight(type: DependencyInsightReportTask) {
                showingAllVariants = false
                setDependencySpec { it.requested.module == 'middle' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        outputContains """
org:middle:2.0 FAILED
\\--- conf

org:middle:1.0 -> 2.0 FAILED
\\--- org:top:1.0
     \\--- conf
"""
    }

    def "marks modules that can't be resolved after substitution as 'FAILED'"() {
        given:
        mavenRepo.module("org", "top").dependsOn("org", "middle", "1.0").publish()
        def middleModule = mavenRepo.module("org", "middle", "1.0").publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                conf {
                    resolutionStrategy.dependencySubstitution {
                        substitute module("org:middle") using module("org:middle:2.0+")
                    }
                }
            }
            dependencies {
                conf 'org:top:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
                showingAllVariants = false
                setDependencySpec { it.requested.module == 'middle' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        outputContains """
org:middle:2.0+ (selected by rule) FAILED
   Failures:
      - Could not find any version that matches org:middle:2.0+.
        Versions that do not match: 1.0
        Searched in the following locations:
          - ${middleModule.rootMetaData.file.displayUri}

org:middle:1.0 -> 2.0+ FAILED
\\--- org:top:1.0
     \\--- conf
"""
    }

    def "shows multiple failed outgoing dependencies"() {
        given:
        ivyRepo.module("org", "top", "1.0")
            .dependsOn("org", "leaf", "1.0")
            .dependsOn("org", "leaf", "[1.5,2.0]")
            .dependsOn("org", "leaf", "1.6+")
            .publish()
        def leafModule = ivyRepo.module("org", "leaf", "1.0")

        buildFile << """
            repositories {
                ivy { url = "${ivyRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:top:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
                showingAllVariants = false
                setDependencySpec { it.requested.module == 'leaf' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        outputContains """
org:leaf:1.0 FAILED
   Failures:
      - Could not find org:leaf:1.0.
        Searched in the following locations:
          - ${leafModule.ivyFile.displayUri}
        ${repositoryHint("ivy.xml")}

org:leaf:1.0 FAILED
\\--- org:top:1.0
     \\--- conf

org:leaf:1.6+ FAILED
   Failures:
      - Could not find any matches for org:leaf:1.6+ as no versions of org:leaf are available.

org:leaf:1.6+ FAILED
\\--- org:top:1.0
     \\--- conf

org:leaf:[1.5,2.0] FAILED
   Failures:
      - Could not find any matches for org:leaf:[1.5,2.0] as no versions of org:leaf are available.
        Searched in the following locations:
          - ${leafModule.moduleDir.parentFile.displayUriForDir}

org:leaf:[1.5,2.0] FAILED
\\--- org:top:1.0
     \\--- conf
"""
    }

    void "marks project dependencies that cannot be resolved as 'FAILED'"() {
        given:
        createDirs("A", "B", "C")
        settingsFile << "include 'A', 'B', 'C'; rootProject.name='root'"

        buildFile << """
            configurations.create('conf')
            dependencies {
              conf project(':A')
              conf project(':B')
            }

            project(':B') {
                configurations.create('default')
                dependencies.add("default", project(':C'))
            }
        """

        when:
        run "dependencyInsight", "--configuration", "conf", "--dependency", ":A"

        then:
        outputContains """
project :A FAILED
   Failures:
      - Could not resolve project :A.
        Creating consumable variants is explained in more detail at https://docs.gradle.org/${GradleVersion.current().version}/userguide/declaring_dependencies.html#sec:resolvable-consumable-configs.
          - Unable to find a matching variant of project :A:
              - No variants exist.

project :A FAILED
\\--- conf
"""

        when:
        run "dependencyInsight", "--configuration", "conf", "--dependency", ":C"

        then:
        outputContains """
project :C FAILED
   Failures:
      - Could not resolve project :C.
        Creating consumable variants is explained in more detail at https://docs.gradle.org/${GradleVersion.current().version}/userguide/declaring_dependencies.html#sec:resolvable-consumable-configs.
          - Unable to find a matching variant of project :C:
              - No variants exist.

project :C FAILED
\\--- project :B
     \\--- conf
"""

    }

    def "deals with dependency cycles"() {
        given:
        mavenRepo.module("org", "leaf1").dependsOnModules("leaf2").publish()
        mavenRepo.module("org", "leaf2").dependsOnModules("leaf1").publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:leaf1:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
                showingAllVariants = false
                setDependencySpec { it.requested.module == 'leaf2' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        outputContains """
org:leaf2:1.0
  Variant runtime:
    | Attribute Name             | Provided     | Requested |
    |----------------------------|--------------|-----------|
    | org.gradle.category        | library      |           |
    | org.gradle.libraryelements | jar          |           |
    | org.gradle.status          | release      |           |
    | org.gradle.usage           | java-runtime |           |

org:leaf2:1.0
\\--- org:leaf1:1.0
     +--- conf
     \\--- org:leaf2:1.0 (*)
"""
    }

    def "deals with dependency cycle to root"() {
        given:
        createDirs("impl")
        settingsFile << "include 'impl'; rootProject.name='root'"

        buildFile << """
            allprojects {
                apply plugin: 'java-library'
                group = 'org.foo'
                version = '1.0'
            }
            base {
                archivesName = 'root'
            }
            dependencies {
                implementation project(":impl")
            }
            project(":impl") {
                dependencies {
                    implementation project(":")
                }
            }
            task insight(type: DependencyInsightReportTask) {
                showingAllVariants = false
                setDependencySpec { true }
                configuration = configurations.runtimeClasspath
            }
        """

        when:
        run "insight"

        then:
        outputContains """
root project :
  Variant runtimeClasspath:
    | Attribute Name                 | Provided     | Requested    |
    |--------------------------------|--------------|--------------|
    | org.gradle.category            | library      | library      |
    | org.gradle.dependency.bundling | external     | external     |
    | org.gradle.jvm.environment     | standard-jvm | standard-jvm |
    | org.gradle.jvm.version         | ${jvmVersion.padRight("java-runtime".length())} | ${jvmVersion.padRight("java-runtime".length())} |
    | org.gradle.libraryelements     | jar          | jar          |
    | org.gradle.usage               | java-runtime | java-runtime |
  Variant runtimeElements:
    | Attribute Name                 | Provided     | Requested    |
    |--------------------------------|--------------|--------------|
    | org.gradle.category            | library      | library      |
    | org.gradle.dependency.bundling | external     | external     |
    | org.gradle.jvm.version         | ${jvmVersion.padRight("java-runtime".length())} | ${jvmVersion.padRight("java-runtime".length())} |
    | org.gradle.libraryelements     | jar          | jar          |
    | org.gradle.usage               | java-runtime | java-runtime |
    | org.gradle.jvm.environment     |              | standard-jvm |

root project :
\\--- project :impl
     \\--- root project : (*)
"""
    }

    def "selects a module component dependency with a given name"() {
        given:
        mavenRepo.module("org", "leaf1").dependsOnModules("leaf2").publish()
        mavenRepo.module("org", "leaf2").dependsOnModules("leaf3").publish()
        mavenRepo.module("org", "leaf3").publish()

        createDirs("impl")
        settingsFile << "include 'impl'; rootProject.name='root'"

        buildFile << """
            allprojects {
                apply plugin: 'java-library'
                group = 'org.foo'
                version = '1.0-SNAPSHOT'
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
            }
            dependencies {
                implementation project(':impl')
            }
            project(':impl') {
                dependencies {
                    implementation 'org:leaf1:1.0'
                }
            }
            task insight(type: DependencyInsightReportTask) {
                showingAllVariants = false
                setDependencySpec { it.requested instanceof ModuleComponentSelector && it.requested.module == 'leaf2' }
                configuration = configurations.runtimeClasspath
            }
        """

        when:
        run "insight"

        then:
        outputContains """
org:leaf2:1.0
  Variant runtime:
    | Attribute Name                 | Provided     | Requested    |
    |--------------------------------|--------------|--------------|
    | org.gradle.status              | release      |              |
    | org.gradle.category            | library      | library      |
    | org.gradle.libraryelements     | jar          | jar          |
    | org.gradle.usage               | java-runtime | java-runtime |
    | org.gradle.dependency.bundling |              | external     |
    | org.gradle.jvm.environment     |              | standard-jvm |
    | org.gradle.jvm.version         |              | ${jvmVersion.padRight("java-runtime".length())} |

org:leaf2:1.0
\\--- org:leaf1:1.0
     \\--- project :impl
          \\--- runtimeClasspath
"""
    }

    def "selects a project component dependency with a given project path"() {
        given:
        mavenRepo.module("org", "leaf1").dependsOnModules("leaf2").publish()
        mavenRepo.module("org", "leaf2").dependsOnModules("leaf3").publish()
        mavenRepo.module("org", "leaf3").publish()

        createDirs("impl")
        settingsFile << "include 'impl'; rootProject.name='root'"

        buildFile << """
            allprojects {
                apply plugin: 'java-library'
                group = 'org.foo'
                version = '1.0-SNAPSHOT'
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
            }
            dependencies {
                implementation project(':impl')
            }
            project(':impl') {
                dependencies {
                    implementation 'org:leaf1:1.0'
                }
            }
            task insight(type: DependencyInsightReportTask) {
                showingAllVariants = false
                setDependencySpec { it.requested instanceof ProjectComponentSelector && it.requested.projectPath == ':impl' }
                configuration = configurations.compileClasspath
            }
        """

        when:
        run "insight"

        then:
        outputContains """
project :impl
  Variant apiElements:
    | Attribute Name                 | Provided | Requested    |
    |--------------------------------|----------|--------------|
    | org.gradle.category            | library  | library      |
    | org.gradle.dependency.bundling | external | external     |
    | org.gradle.jvm.version         | ${jvmVersion.padRight("java-api".length())} | ${jvmVersion.padRight("standard-jvm".length())} |
    | org.gradle.libraryelements     | jar      | classes      |
    | org.gradle.usage               | java-api | java-api     |
    | org.gradle.jvm.environment     |          | standard-jvm |

project :impl
\\--- compileClasspath
"""
    }

    def "selects a module component dependency with a given name with dependency command line option"() {
        given:
        mavenRepo.module("org", "leaf1").dependsOnModules("leaf2").publish()
        mavenRepo.module("org", "leaf2").dependsOnModules("leaf3").publish()
        mavenRepo.module("org", "leaf3").publish()
        mavenRepo.module("org", "leaf4").publish()

        createDirs("api", "impl")
        settingsFile << "include 'api', 'impl'; rootProject.name='root'"

        buildFile << """
            allprojects {
                apply plugin: 'java-library'
                group = 'org.foo'
                version = '1.0-SNAPSHOT'
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
            }
            dependencies {
                api project(':impl')
            }
            project(':api') {
                dependencies {
                    api 'org:leaf1:1.0'
                }
            }
            project(':impl') {
                dependencies {
                    api project(':api')
                    api 'org:leaf4:1.0'
                }
            }
        """

        when:
        run "dependencyInsight", "--dependency", "leaf4"

        then:
        outputContains """
org:leaf4:1.0
  Variant compile:
    | Attribute Name                 | Provided | Requested    |
    |--------------------------------|----------|--------------|
    | org.gradle.status              | release  |              |
    | org.gradle.category            | library  | library      |
    | org.gradle.libraryelements     | jar      | classes      |
    | org.gradle.usage               | java-api | java-api     |
    | org.gradle.dependency.bundling |          | external     |
    | org.gradle.jvm.environment     |          | standard-jvm |
    | org.gradle.jvm.version         |          | ${jvmVersion.padRight("standard-jvm".length())} |

org:leaf4:1.0
\\--- project :impl
     \\--- compileClasspath
"""
    }

    def "selects both api and implementation dependencies with dependency command line option"() {
        given:
        mavenRepo.module("org", "leaf1").publish()
        mavenRepo.module("org", "leaf2").publish()

        buildFile << """
                apply plugin: 'java-library'
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
                dependencies {
                    api 'org:leaf1:1.0'
                    implementation 'org:leaf2:1.0'
                }
        """

        when:
        run "dependencyInsight", "--dependency", "leaf1"

        then:
        outputContains """
org:leaf1:1.0
  Variant compile:
    | Attribute Name                 | Provided | Requested    |
    |--------------------------------|----------|--------------|
    | org.gradle.status              | release  |              |
    | org.gradle.category            | library  | library      |
    | org.gradle.libraryelements     | jar      | classes      |
    | org.gradle.usage               | java-api | java-api     |
    | org.gradle.dependency.bundling |          | external     |
    | org.gradle.jvm.environment     |          | standard-jvm |
    | org.gradle.jvm.version         |          | ${jvmVersion.padRight("standard-jvm".length())} |

org:leaf1:1.0
\\--- compileClasspath
"""

        when:
        run "dependencyInsight", "--dependency", "leaf2"

        then:
        outputContains """
org:leaf2:1.0
  Variant compile:
    | Attribute Name                 | Provided | Requested    |
    |--------------------------------|----------|--------------|
    | org.gradle.status              | release  |              |
    | org.gradle.category            | library  | library      |
    | org.gradle.libraryelements     | jar      | classes      |
    | org.gradle.usage               | java-api | java-api     |
    | org.gradle.dependency.bundling |          | external     |
    | org.gradle.jvm.environment     |          | standard-jvm |
    | org.gradle.jvm.version         |          | ${jvmVersion.padRight("standard-jvm".length())} |

org:leaf2:1.0
\\--- compileClasspath
"""
    }

    def "selects a project component dependency with a given name with dependency command line option"() {
        given:
        mavenRepo.module("org", "leaf1").dependsOnModules("leaf2").publish()
        mavenRepo.module("org", "leaf2").dependsOnModules("leaf3").publish()
        mavenRepo.module("org", "leaf3").publish()
        mavenRepo.module("org", "leaf4").publish()

        createDirs("api", "impl", "some", "some/deeply", "some/deeply/nested")
        settingsFile << "include 'api', 'impl', 'some:deeply:nested'; rootProject.name='root'"

        buildFile << """
            allprojects {
                apply plugin: 'java-library'
                group = 'org.foo'
                version = '1.0-SNAPSHOT'
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
            }
            dependencies {
                api project(':impl')
                api project(':some:deeply:nested')
            }
            project(':api') {
                dependencies {
                    api 'org:leaf1:1.0'
                }
            }
            project(':impl') {
                dependencies {
                    api project(':api')
                    api 'org:leaf4:1.0'
                }
            }
            project(':some:deeply:nested') {
                dependencies {
                    api 'org:leaf3:1.0'
                }
            }
        """

        when:
        run "dependencyInsight", "--dependency", ":api"

        then:
        outputContains """
project :api
  Variant apiElements:
    | Attribute Name                 | Provided | Requested    |
    |--------------------------------|----------|--------------|
    | org.gradle.category            | library  | library      |
    | org.gradle.dependency.bundling | external | external     |
    | org.gradle.jvm.version         | ${jvmVersion.padRight("java-api".length())} | ${jvmVersion.padRight("standard-jvm".length())} |
    | org.gradle.libraryelements     | jar      | classes      |
    | org.gradle.usage               | java-api | java-api     |
    | org.gradle.jvm.environment     |          | standard-jvm |

project :api
\\--- project :impl
     \\--- compileClasspath
"""

        when:
        run "dependencyInsight", "--dependency", ":nested"

        then:
        outputContains """
project :some:deeply:nested
  Variant apiElements:
    | Attribute Name                 | Provided | Requested    |
    |--------------------------------|----------|--------------|
    | org.gradle.category            | library  | library      |
    | org.gradle.dependency.bundling | external | external     |
    | org.gradle.jvm.version         | ${jvmVersion.padRight("java-api".length())} | ${jvmVersion.padRight("standard-jvm".length())} |
    | org.gradle.libraryelements     | jar      | classes      |
    | org.gradle.usage               | java-api | java-api     |
    | org.gradle.jvm.environment     |          | standard-jvm |

project :some:deeply:nested
\\--- compileClasspath
"""

        when:
        run "dependencyInsight", "--dependency", ":some:deeply:nested"

        then:
        outputContains """
project :some:deeply:nested
  Variant apiElements:
    | Attribute Name                 | Provided | Requested    |
    |--------------------------------|----------|--------------|
    | org.gradle.category            | library  | library      |
    | org.gradle.dependency.bundling | external | external     |
    | org.gradle.jvm.version         | ${jvmVersion.padRight("java-api".length())} | ${jvmVersion.padRight("standard-jvm".length())} |
    | org.gradle.libraryelements     | jar      | classes      |
    | org.gradle.usage               | java-api | java-api     |
    | org.gradle.jvm.environment     |          | standard-jvm |

project :some:deeply:nested
\\--- compileClasspath
"""
    }

    def "renders tree with a mix of project and external dependencies"() {
        given:
        mavenRepo.module("org", "leaf1").dependsOnModules("leaf2").publish()
        mavenRepo.module("org", "leaf2").dependsOnModules("leaf3").publish()
        mavenRepo.module("org", "leaf3").publish()

        createDirs("api", "impl")
        settingsFile << "include 'api', 'impl'; rootProject.name='root'"

        buildFile << """
            allprojects {
                apply plugin: 'java-library'
                group = 'org.foo'
                version = '1.0-SNAPSHOT'
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
            }
            dependencies {
                api project(':impl')
            }
            project(':api') {
                dependencies {
                    api 'org:leaf2:1.0'
                }
            }
            project(':impl') {
                dependencies {
                    api project(':api')
                    api 'org:leaf1:1.0'
                }
            }
        """

        when:
        run "dependencyInsight", "--dependency", "leaf3"

        then:
        outputContains """
org:leaf3:1.0
  Variant compile:
    | Attribute Name                 | Provided | Requested    |
    |--------------------------------|----------|--------------|
    | org.gradle.status              | release  |              |
    | org.gradle.category            | library  | library      |
    | org.gradle.libraryelements     | jar      | classes      |
    | org.gradle.usage               | java-api | java-api     |
    | org.gradle.dependency.bundling |          | external     |
    | org.gradle.jvm.environment     |          | standard-jvm |
    | org.gradle.jvm.version         |          | ${jvmVersion.padRight("standard-jvm".length())} |

org:leaf3:1.0
\\--- org:leaf2:1.0
     +--- project :api
     |    \\--- project :impl
     |         \\--- compileClasspath
     \\--- org:leaf1:1.0
          \\--- project :impl (*)

"""
    }

    void "fails a configuration is not resolvable"() {
        mavenRepo.module("foo", "foo", '1.0').publish()
        mavenRepo.module("foo", "bar", '2.0').publish()

        buildFile << """
            repositories {
               maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                api.canBeConsumed = false
                api.canBeResolved = false
                compile.extendsFrom api
            }
            dependencies {
                api 'foo:foo:1.0'
                compile 'foo:bar:2.0'
            }
        """

        when:
        fails "dependencyInsight", "--dependency", "foo", "--configuration", "api"

        then:
        failure.assertHasCause("Resolving dependency configuration 'api' is not allowed as it is defined as 'canBeResolved=false'.")
        failure.assertHasFailures(1)

        when:
        run "dependencyInsight", "--dependency", "foo", "--configuration", "compile"

        then:
        result.groupedOutput.task(":dependencyInsight").output.contains("""foo:bar:2.0
  Variant runtime:
    | Attribute Name             | Provided     | Requested |
    |----------------------------|--------------|-----------|
    | org.gradle.category        | library      |           |
    | org.gradle.libraryelements | jar          |           |
    | org.gradle.status          | release      |           |
    | org.gradle.usage           | java-runtime |           |

foo:bar:2.0
\\--- compile

foo:foo:1.0
  Variant runtime:
    | Attribute Name             | Provided     | Requested |
    |----------------------------|--------------|-----------|
    | org.gradle.category        | library      |           |
    | org.gradle.libraryelements | jar          |           |
    | org.gradle.status          | release      |           |
    | org.gradle.usage           | java-runtime |           |

foo:foo:1.0
\\--- compile""")
    }

    def "renders dependency constraint differently"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "foo", "1.1").publish()
        mavenRepo.module("org", "foo", "1.2").publish()
        mavenRepo.module("org", "foo", "1.3").publish()
        mavenRepo.module("org", "foo", "1.4").publish()
        mavenRepo.module("org", "foo", "1.5").publish()
        mavenRepo.module("org", "foo", "2.0").publish()

        buildFile << """
            apply plugin: 'java-library'

            repositories {
               maven { url = "${mavenRepo.uri}" }
            }

            dependencies {
                implementation 'org:foo' // no version
                constraints {
                    implementation('org:foo') {
                         version { $version }
                    }
                }
            }
        """

        when:
        run "dependencyInsight", "--dependency", "foo"

        then:
        outputContains """org:foo:$selected
  Variant compile:
    | Attribute Name                 | Provided | Requested    |
    |--------------------------------|----------|--------------|
    | org.gradle.status              | release  |              |
    | org.gradle.category            | library  | library      |
    | org.gradle.libraryelements     | jar      | classes      |
    | org.gradle.usage               | java-api | java-api     |
    | org.gradle.dependency.bundling |          | external     |
    | org.gradle.jvm.environment     |          | standard-jvm |
    | org.gradle.jvm.version         |          | ${jvmVersion.padRight("standard-jvm".length())} |
   Selection reasons:
      - By constraint: $rejected

org:foo -> $selected
\\--- compileClasspath"""

        where:
        version                             | selected | rejected
        "require '[1.0, 2.0)'"              | '1.5'    | "didn't match version 2.0"
        "strictly '[1.1, 1.4]'"             | '1.4'    | "didn't match versions 2.0, 1.5"
        "prefer '[1.0, 1.4]'; reject '1.4'" | '1.3'    | "rejected version 1.4"
    }

    def "renders custom dependency constraint reasons (#version)"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "foo", "1.1").publish()
        mavenRepo.module("org", "foo", "1.2").publish()
        mavenRepo.module("org", "foo", "1.3").publish()
        mavenRepo.module("org", "foo", "1.4").publish()
        mavenRepo.module("org", "foo", "1.5").publish()
        mavenRepo.module("org", "foo", "2.0").publish()

        buildFile << """
            apply plugin: 'java-library'

            repositories {
               maven { url = "${mavenRepo.uri}" }
            }

            dependencies {
                implementation 'org:foo' // no version
                constraints {
                    implementation('org:foo') {
                         version { $version }
                         because '$reason'
                    }
                }
            }
        """

        when:
        run "dependencyInsight", "--dependency", "foo"

        then:
        outputContains """org:foo:$selected
  Variant compile:
    | Attribute Name                 | Provided | Requested    |
    |--------------------------------|----------|--------------|
    | org.gradle.status              | release  |              |
    | org.gradle.category            | library  | library      |
    | org.gradle.libraryelements     | jar      | classes      |
    | org.gradle.usage               | java-api | java-api     |
    | org.gradle.dependency.bundling |          | external     |
    | org.gradle.jvm.environment     |          | standard-jvm |
    | org.gradle.jvm.version         |          | ${jvmVersion.padRight("standard-jvm".length())} |
   Selection reasons:
      - By constraint: ${rejected}${reason}

org:foo -> $selected
\\--- compileClasspath
"""
        where:
        version                             | reason                                          | selected | rejected
        "require '[1.0, 2.0)'"              | "foo v2+ has an incompatible API for project X" | '1.5'    | "didn't match version 2.0 because "
        "strictly '[1.1, 1.4]'"             | "versions of foo verified to run on platform Y" | '1.4'    | "didn't match versions 2.0, 1.5 because "
        "prefer '[1.0, 1.4]'; reject '1.4'" | "1.4 has a critical bug"                        | '1.3'    | "rejected version 1.4 because "
    }

    def "renders custom dependency reasons"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "foo", "1.1").publish()
        mavenRepo.module("org", "foo", "1.2").publish()
        mavenRepo.module("org", "foo", "1.3").publish()
        mavenRepo.module("org", "foo", "1.4").publish()
        mavenRepo.module("org", "foo", "1.5").publish()
        mavenRepo.module("org", "foo", "2.0").publish()

        buildFile << """
            apply plugin: 'java-library'

            repositories {
               maven { url = "${mavenRepo.uri}" }
            }

            dependencies {
                implementation('org:foo') {
                   version { $version }
                   because '$reason'
                }
            }
        """

        when:
        run "dependencyInsight", "--dependency", "foo"

        then:
        outputContains """org:foo:$selected
  Variant compile:
    | Attribute Name                 | Provided | Requested    |
    |--------------------------------|----------|--------------|
    | org.gradle.status              | release  |              |
    | org.gradle.category            | library  | library      |
    | org.gradle.libraryelements     | jar      | classes      |
    | org.gradle.usage               | java-api | java-api     |
    | org.gradle.dependency.bundling |          | external     |
    | org.gradle.jvm.environment     |          | standard-jvm |
    | org.gradle.jvm.version         |          | ${jvmVersion.padRight("standard-jvm".length())} |
   Selection reasons:
      - Was requested: ${rejected}${reason}

org:foo:${displayVersion} -> $selected
\\--- compileClasspath
"""
        where:
        version                             | displayVersion                    | reason                                          | selected | rejected
        "require '[1.0, 2.0)'"              | '[1.0, 2.0)'                      | "foo v2+ has an incompatible API for project X" | '1.5'    | "didn't match version 2.0 because "
        "strictly '[1.1, 1.4]'"             | '{strictly [1.1, 1.4]}'           | "versions of foo verified to run on platform Y" | '1.4'    | "didn't match versions 2.0, 1.5 because "
        "prefer '[1.0, 1.4]'; reject '1.4'" | '{prefer [1.0, 1.4]; reject 1.4}' | "1.4 has a critical bug"                        | '1.3'    | "rejected version 1.4 because "
    }

    def "doesn't report duplicate reasons"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "foo", "1.1").publish()
        mavenRepo.module("org", "foo", "1.2").publish()
        mavenRepo.module("org", "foo", "1.3").publish()
        mavenRepo.module("org", "foo", "1.4").publish()
        mavenRepo.module("org", "foo", "1.5").publish()
        mavenRepo.module("org", "foo", "2.0").publish()
        mavenRepo.module('org', 'bar', '1.0').dependsOn('org', 'foo', '[1.1,1.3]').publish()
        buildFile << """
            apply plugin: 'java-library'

            repositories {
               maven { url = "${mavenRepo.uri}" }
            }

            dependencies {
                implementation 'org:foo:[1.1,1.3]'
                implementation 'org:bar:1.0'
            }
        """

        when:
        run "dependencyInsight", "--dependency", "foo"

        then:
        outputContains """org:foo:1.3
  Variant compile:
    | Attribute Name                 | Provided | Requested    |
    |--------------------------------|----------|--------------|
    | org.gradle.status              | release  |              |
    | org.gradle.category            | library  | library      |
    | org.gradle.libraryelements     | jar      | classes      |
    | org.gradle.usage               | java-api | java-api     |
    | org.gradle.dependency.bundling |          | external     |
    | org.gradle.jvm.environment     |          | standard-jvm |
    | org.gradle.jvm.version         |          | ${jvmVersion.padRight("standard-jvm".length())} |
   Selection reasons:
      - Was requested: didn't match versions 2.0, 1.5, 1.4

org:foo:[1.1,1.3] -> 1.3
+--- compileClasspath
\\--- org:bar:1.0
     \\--- compileClasspath
"""
    }

    def "doesn't mix rejected versions on different constraints"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "foo", "1.1").publish()
        mavenRepo.module("org", "foo", "1.2").publish()
        mavenRepo.module("org", "bar", "1.0").publish()
        mavenRepo.module("org", "bar", "1.1").publish()
        mavenRepo.module("org", "bar", "1.2").publish()

        buildFile << """
            apply plugin: 'java-library'

            repositories {
               maven { url = "${mavenRepo.uri}" }
            }

            dependencies {
                implementation('org:foo') {
                   version {
                      require '[1.0,)'
                      reject '1.2'
                   }
                }
                implementation('org:bar') {
                   version {
                      require '[1.0,)'
                      reject '[1.1, 1.2]'
                   }
                }
            }
        """

        when:
        run "dependencyInsight", "--dependency", "org"

        then:
        outputContains """org:bar:1.0
  Variant compile:
    | Attribute Name                 | Provided | Requested    |
    |--------------------------------|----------|--------------|
    | org.gradle.status              | release  |              |
    | org.gradle.category            | library  | library      |
    | org.gradle.libraryelements     | jar      | classes      |
    | org.gradle.usage               | java-api | java-api     |
    | org.gradle.dependency.bundling |          | external     |
    | org.gradle.jvm.environment     |          | standard-jvm |
    | org.gradle.jvm.version         |          | ${jvmVersion.padRight("standard-jvm".length())} |
   Selection reasons:
      - Was requested: rejected versions 1.2, 1.1

org:bar:{require [1.0,); reject [1.1, 1.2]} -> 1.0
\\--- compileClasspath

org:foo:1.1
  Variant compile:
    | Attribute Name                 | Provided | Requested    |
    |--------------------------------|----------|--------------|
    | org.gradle.status              | release  |              |
    | org.gradle.category            | library  | library      |
    | org.gradle.libraryelements     | jar      | classes      |
    | org.gradle.usage               | java-api | java-api     |
    | org.gradle.dependency.bundling |          | external     |
    | org.gradle.jvm.environment     |          | standard-jvm |
    | org.gradle.jvm.version         |          | ${jvmVersion.padRight("standard-jvm".length())} |
   Selection reasons:
      - Was requested: rejected version 1.2

org:foo:{require [1.0,); reject 1.2} -> 1.1
\\--- compileClasspath
"""
    }

    def "shows versions rejected by rule"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "foo", "1.1").publish()
        mavenRepo.module("org", "foo", "1.2").publish()
        mavenRepo.module("org", "bar", "1.0").publish()
        mavenRepo.module("org", "bar", "1.1").publish()
        mavenRepo.module("org", "bar", "1.2").publish()

        buildFile << """
            apply plugin: 'java-library'

            repositories {
               maven { url = "${mavenRepo.uri}" }
            }

            dependencies {
                implementation('org:foo') {
                   version {
                      require '[1.0,)'
                      reject '1.2'
                   }
                }
                implementation('org:bar') {
                   version {
                      require '[1.0,)'
                   }
                }
            }

            configurations.compileClasspath.resolutionStrategy.componentSelection.all { ComponentSelection selection ->
               if (selection.candidate.module == 'bar' && selection.candidate.version in ['1.2', '1.1']) {
                   selection.reject("version \${selection.candidate.version} is bad")
               }
            }
        """

        when:
        run "dependencyInsight", "--dependency", "org"

        then:
        outputContains """Task :dependencyInsight
org:bar:1.0
  Variant compile:
    | Attribute Name                 | Provided | Requested    |
    |--------------------------------|----------|--------------|
    | org.gradle.status              | release  |              |
    | org.gradle.category            | library  | library      |
    | org.gradle.libraryelements     | jar      | classes      |
    | org.gradle.usage               | java-api | java-api     |
    | org.gradle.dependency.bundling |          | external     |
    | org.gradle.jvm.environment     |          | standard-jvm |
    | org.gradle.jvm.version         |          | ${jvmVersion.padRight("standard-jvm".length())} |
   Selection reasons:
      - Rejection: 1.2 by rule because version 1.2 is bad
      - Rejection: 1.1 by rule because version 1.1 is bad

org:bar:[1.0,) -> 1.0
\\--- compileClasspath

org:foo:1.1
  Variant compile:
    | Attribute Name                 | Provided | Requested    |
    |--------------------------------|----------|--------------|
    | org.gradle.status              | release  |              |
    | org.gradle.category            | library  | library      |
    | org.gradle.libraryelements     | jar      | classes      |
    | org.gradle.usage               | java-api | java-api     |
    | org.gradle.dependency.bundling |          | external     |
    | org.gradle.jvm.environment     |          | standard-jvm |
    | org.gradle.jvm.version         |          | ${jvmVersion.padRight("standard-jvm".length())} |
   Selection reasons:
      - Was requested: rejected version 1.2

org:foo:{require [1.0,); reject 1.2} -> 1.1
\\--- compileClasspath
"""
    }

    def "renders dependency from BOM as a constraint"() {
        given:
        def leaf = mavenRepo.module("org", "leaf", "1.0").publish()
        def bom = mavenRepo.module('org', 'bom', '1.0')
        bom.packaging = 'pom'
        bom.dependencyConstraint(leaf)
        bom.publish()

        buildFile << """
            apply plugin: 'java-library'

            repositories {
               maven { url = "${mavenRepo.uri}" }
            }

            dependencies {
                implementation platform('org:bom:1.0') $conf
                implementation 'org:leaf'
            }
        """

        when:
        run "dependencyInsight", "--dependency", "leaf"

        then:
        outputContains """
org:leaf:1.0 (by constraint)
  Variant compile:
    | Attribute Name                 | Provided | Requested    |
    |--------------------------------|----------|--------------|
    | org.gradle.status              | release  |              |
    | org.gradle.category            | library  | library      |
    | org.gradle.libraryelements     | jar      | classes      |
    | org.gradle.usage               | java-api | java-api     |
    | org.gradle.dependency.bundling |          | external     |
    | org.gradle.jvm.environment     |          | standard-jvm |
    | org.gradle.jvm.version         |          | ${jvmVersion.padRight("standard-jvm".length())} |

org:leaf:1.0
\\--- org:bom:1.0
     \\--- compileClasspath

org:leaf -> 1.0
\\--- compileClasspath
"""
        where:
        conf << ["",
                 // this is just a sanity check. Nobody should ever write this.
                 "{ capabilities { requireCapability('org:bom-derived-platform') } }"]
    }

    def "shows published dependency reason"() {
        given:
        mavenRepo.with {
            def leaf = module('org.test', 'leaf', '1.0').publish()
            module('org.test', 'a', '1.0')
                .dependsOn(leaf, reason: 'first reason')
                .withModuleMetadata()
                .publish()

        }

        buildFile << """
            apply plugin: 'java-library'

            repositories {
               maven { url = "${mavenRepo.uri}" }
            }

            dependencies {
                implementation 'org.test:a:1.0'
            }
        """

        when:
        run "dependencyInsight", "--dependency", "leaf"

        then:
        outputContains """
org.test:leaf:1.0
  Variant compile:
    | Attribute Name                 | Provided | Requested    |
    |--------------------------------|----------|--------------|
    | org.gradle.status              | release  |              |
    | org.gradle.category            | library  | library      |
    | org.gradle.libraryelements     | jar      | classes      |
    | org.gradle.usage               | java-api | java-api     |
    | org.gradle.dependency.bundling |          | external     |
    | org.gradle.jvm.environment     |          | standard-jvm |
    | org.gradle.jvm.version         |          | ${jvmVersion.padRight("standard-jvm".length())} |
   Selection reasons:
      - Was requested: first reason

org.test:leaf:1.0
\\--- org.test:a:1.0
     \\--- compileClasspath
"""
    }

    def "mentions web-based dependency insight report available using build scans"() {
        given:
        mavenRepo.module("org", "leaf1").publish()
        mavenRepo.module("org", "leaf2").publish()

        mavenRepo.module("org", "middle").dependsOnModules("leaf1", "leaf2").publish()

        mavenRepo.module("org", "top").dependsOnModules("middle", "leaf2").publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:top:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
                showingAllVariants = false
                setDependencySpec { it.requested.module == 'leaf2' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        outputContains """
org:leaf2:1.0
  Variant runtime:
    | Attribute Name             | Provided     | Requested |
    |----------------------------|--------------|-----------|
    | org.gradle.category        | library      |           |
    | org.gradle.libraryelements | jar          |           |
    | org.gradle.status          | release      |           |
    | org.gradle.usage           | java-runtime |           |

org:leaf2:1.0
+--- org:middle:1.0
|    \\--- org:top:1.0
|         \\--- conf
\\--- org:top:1.0 (*)

(*) - Indicates repeated occurrences of a transitive dependency subtree. Gradle expands transitive dependency subtrees only once per project; repeat occurrences only display the root of the subtree, followed by this annotation.

A web-based, searchable dependency report is available by adding the --scan option.
"""
    }

    def "renders multiple rejected modules"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "foo", "1.1").publish()
        mavenRepo.module("org", "foo", "1.2").publish()
        mavenRepo.module("org", "bar", "1.0").publish()
        mavenRepo.module("org", "bar", "1.1").publish()
        mavenRepo.module("org", "bar", "1.2").publish()


        buildFile << """
            apply plugin: 'java-library'

            repositories {
               maven { url = "${mavenRepo.uri}" }
            }

            dependencies {
                constraints {
                    implementation('org:foo') {
                       version {
                          reject '1.0', '1.1', '1.2'
                       }
                    }
                    implementation('org:bar') {
                       version {
                          rejectAll()
                       }
                       because "Nope, you won't use this"
                    }
                }
                implementation 'org:foo:[1.0,)'
                implementation 'org:bar:[1.0,)'
            }
        """

        when:
        run "dependencyInsight", "--dependency", "org"

        then:
        outputContains """
org:bar: FAILED
   Selection reasons:
      - By constraint: Nope, you won't use this
   Failures:
      - Could not resolve org:bar:{reject all versions}.
          - Module 'org:bar' has been rejected:
               Dependency path ':insight-test:unspecified' --> 'org:bar:[1.0,)'
               Constraint path ':insight-test:unspecified' --> 'org:bar:{reject all versions}' because of the following reason: Nope, you won't use this

org:bar:{reject all versions} FAILED
\\--- compileClasspath

org:bar:[1.0,) FAILED
   Failures:
      - Could not resolve org:bar:[1.0,). (already reported)

org:bar:[1.0,) FAILED
\\--- compileClasspath

org:foo: (by constraint) FAILED
   Failures:
      - Could not resolve org:foo:{reject 1.0 & 1.1 & 1.2}.
          - Cannot find a version of 'org:foo' that satisfies the version constraints:
               Dependency path ':insight-test:unspecified' --> 'org:foo:[1.0,)'
               Constraint path ':insight-test:unspecified' --> 'org:foo:{reject 1.0 & 1.1 & 1.2}'

org:foo:{reject 1.0 & 1.1 & 1.2} FAILED
\\--- compileClasspath

org:foo:[1.0,) FAILED
   Failures:
      - Could not resolve org:foo:[1.0,). (already reported)

org:foo:[1.0,) FAILED
\\--- compileClasspath
"""
    }

    def "renders multiple rejection reasons for module"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "foo", "1.1").publish()
        mavenRepo.module("org", "foo", "1.2").publish()


        buildFile << """
            apply plugin: 'java-library'

            repositories {
               maven { url = "${mavenRepo.uri}" }
            }

            dependencies {
                constraints {
                    implementation('org:foo') {
                       version {
                          reject '1.2'
                       }
                    }
                }
                implementation('org:foo:[1.0,)') {
                    version {
                        reject '1.1'
                    }
                }
            }
        """

        when:
        run "dependencyInsight", "--dependency", "foo"

        then:
        outputContains """
org:foo:1.0
  Variant compile:
    | Attribute Name                 | Provided | Requested    |
    |--------------------------------|----------|--------------|
    | org.gradle.status              | release  |              |
    | org.gradle.category            | library  | library      |
    | org.gradle.libraryelements     | jar      | classes      |
    | org.gradle.usage               | java-api | java-api     |
    | org.gradle.dependency.bundling |          | external     |
    | org.gradle.jvm.environment     |          | standard-jvm |
    | org.gradle.jvm.version         |          | ${jvmVersion.padRight("standard-jvm".length())} |
   Selection reasons:
      - By constraint
      - Was requested: rejected versions 1.2, 1.1

org:foo:{reject 1.2} -> 1.0
\\--- compileClasspath

org:foo:{require [1.0,); reject 1.1} -> 1.0
\\--- compileClasspath
"""
    }

    def "shows all published dependency reasons"() {
        given:
        mavenRepo.with {
            def leaf = module('org.test', 'leaf', '1.0').publish()
            module('org.test', 'a', '1.0')
                .dependsOn(leaf, reason: 'first reason')
                .withModuleMetadata()
                .publish()
            module('org.test', 'b', '1.0')
                .dependsOn('org.test', 'c', '1.0').publish()
            module('org.test', 'c')
                .dependsOn(leaf, reason: 'transitive reason')
                .withModuleMetadata()
                .publish()
        }

        buildFile << """
            apply plugin: 'java-library'

            repositories {
               maven { url = "${mavenRepo.uri}" }
            }

            dependencies {
                implementation 'org.test:a:1.0'
                implementation 'org.test:b:1.0'
            }
        """

        when:
        run "dependencyInsight", "--dependency", "leaf"

        then:
        outputContains """org.test:leaf:1.0
  Variant compile:
    | Attribute Name                 | Provided | Requested    |
    |--------------------------------|----------|--------------|
    | org.gradle.status              | release  |              |
    | org.gradle.category            | library  | library      |
    | org.gradle.libraryelements     | jar      | classes      |
    | org.gradle.usage               | java-api | java-api     |
    | org.gradle.dependency.bundling |          | external     |
    | org.gradle.jvm.environment     |          | standard-jvm |
    | org.gradle.jvm.version         |          | ${jvmVersion.padRight("standard-jvm".length())} |
   Selection reasons:
      - Was requested: first reason
      - Was requested: transitive reason

org.test:leaf:1.0
+--- org.test:a:1.0
|    \\--- compileClasspath
\\--- org.test:c:1.0
     \\--- org.test:b:1.0
          \\--- compileClasspath
"""
    }

    def "shows that version is rejected because of attributes (#type)"() {
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "foo", "1.1").publish()
        mavenRepo.module("org", "foo", "1.2").publish()

        def attributes = """{
                    attributes {
                        attribute(COLOR, 'blue')
                    }
                }"""

        buildFile << """
            apply plugin: 'java-library'

            def COLOR = Attribute.of('color', String)

            repositories {
               maven { url = "${mavenRepo.uri}" }
            }

            configurations {
               compileClasspath ${type == 'configuration' ? attributes : ''}
            }

            dependencies {
                implementation('org:foo:[1.0,)') ${type == 'dependency' ? attributes : ''}

                components.all { details ->
                   attributes {
                      def colors = ['1.0' : 'blue', '1.1': 'green', '1.2': 'red']
                      attribute(COLOR, colors[details.id.version])
                   }
                }
            }
        """

        when:
        run "dependencyInsight", "--dependency", "foo"

        then:
        outputContains """Task :dependencyInsight
org:foo:1.0
  Variant compile:
    | Attribute Name                 | Provided | Requested    |
    |--------------------------------|----------|--------------|
    | org.gradle.status              | release  |              |
    | color                          | blue     | blue         |
    | org.gradle.category            | library  | library      |
    | org.gradle.libraryelements     | jar      | classes      |
    | org.gradle.usage               | java-api | java-api     |
    | org.gradle.dependency.bundling |          | external     |
    | org.gradle.jvm.environment     |          | standard-jvm |
    | org.gradle.jvm.version         |          | ${jvmVersion.padRight("standard-jvm".length())} |
   Selection reasons:
      - Rejection: version 1.2:
          - Attribute 'color' didn't match. Requested 'blue', was: 'red'
          - Attribute 'org.gradle.category' didn't match. Requested 'library', was: not found
          - Attribute 'org.gradle.dependency.bundling' didn't match. Requested 'external', was: not found
          - Attribute 'org.gradle.jvm.environment' didn't match. Requested 'standard-jvm', was: not found
          - Attribute 'org.gradle.jvm.version' didn't match. Requested '${JavaVersion.current().majorVersion}', was: not found
          - Attribute 'org.gradle.libraryelements' didn't match. Requested 'classes', was: not found
          - Attribute 'org.gradle.usage' didn't match. Requested 'java-api', was: not found
      - Rejection: version 1.1:
          - Attribute 'color' didn't match. Requested 'blue', was: 'green'
          - Attribute 'org.gradle.category' didn't match. Requested 'library', was: not found
          - Attribute 'org.gradle.dependency.bundling' didn't match. Requested 'external', was: not found
          - Attribute 'org.gradle.jvm.environment' didn't match. Requested 'standard-jvm', was: not found
          - Attribute 'org.gradle.jvm.version' didn't match. Requested '${JavaVersion.current().majorVersion}', was: not found
          - Attribute 'org.gradle.libraryelements' didn't match. Requested 'classes', was: not found
          - Attribute 'org.gradle.usage' didn't match. Requested 'java-api', was: not found

org:foo:[1.0,) -> 1.0
\\--- compileClasspath
"""
        where:
        type << ['configuration', 'dependency']
    }

    def "reports 2nd level dependency conflicts"() {
        given:
        mavenRepo.with {
            module('planet', 'earth', '3.0.0')
                .dependsOn('planet', 'venus', '2.0.0')
                .publish()
            module('planet', 'jupiter', '5.0.0')
                .dependsOn('planet', 'mercury', '1.0.2')
                .dependsOn('planet', 'venus', '1.0')
                .publish()
            module('planet', 'mars', '4.0.0')
                .dependsOn('planet', 'venus', '2.0.1')
                .publish()
            module('planet', 'mercury', '1.0.0').publish()
            module('planet', 'mercury', '1.0.1').publish()
            module('planet', 'mercury', '1.0.2')
                .dependsOn('planet', 'pluto', '1.0.0')
                .publish()
            module('planet', 'venus', '2.0.0')
                .dependsOn('planet', 'mercury', '1.0.0')
                .publish()
            module('planet', 'venus', '2.0.1')
                .dependsOn('planet', 'mercury', '1.0.1')
                .publish()
            module('planet', 'pluto', '1.0.0').publish()
        }

        buildFile << """
            apply plugin: 'java-library'

            repositories {
               maven { url = "${mavenRepo.uri}" }
            }

            dependencies {
                implementation 'planet:earth:3.0.0'
                implementation 'planet:mars:4.0.0'
                implementation 'planet:jupiter:5.0.0'
            }
        """

        when:
        run "dependencyInsight", "--dependency", "mercury"

        then:
        outputContains """> Task :dependencyInsight
planet:mercury:1.0.2
  Variant compile:
    | Attribute Name                 | Provided | Requested    |
    |--------------------------------|----------|--------------|
    | org.gradle.status              | release  |              |
    | org.gradle.category            | library  | library      |
    | org.gradle.libraryelements     | jar      | classes      |
    | org.gradle.usage               | java-api | java-api     |
    | org.gradle.dependency.bundling |          | external     |
    | org.gradle.jvm.environment     |          | standard-jvm |
    | org.gradle.jvm.version         |          | ${jvmVersion.padRight("standard-jvm".length())} |
   Selection reasons:
      - By conflict resolution: between versions 1.0.2 and 1.0.1

planet:mercury:1.0.2
\\--- planet:jupiter:5.0.0
     \\--- compileClasspath

planet:mercury:1.0.1 -> 1.0.2
\\--- planet:venus:2.0.1
     +--- planet:earth:3.0.0 (requested planet:venus:2.0.0)
     |    \\--- compileClasspath
     +--- planet:mars:4.0.0
     |    \\--- compileClasspath
     \\--- planet:jupiter:5.0.0 (requested planet:venus:1.0)
          \\--- compileClasspath
"""
        when:
        run "dependencyInsight", "--dependency", "venus"

        then:
        outputContains """> Task :dependencyInsight
planet:venus:2.0.1
  Variant compile:
    | Attribute Name                 | Provided | Requested    |
    |--------------------------------|----------|--------------|
    | org.gradle.status              | release  |              |
    | org.gradle.category            | library  | library      |
    | org.gradle.libraryelements     | jar      | classes      |
    | org.gradle.usage               | java-api | java-api     |
    | org.gradle.dependency.bundling |          | external     |
    | org.gradle.jvm.environment     |          | standard-jvm |
    | org.gradle.jvm.version         |          | ${jvmVersion.padRight("standard-jvm".length())} |
   Selection reasons:
      - By conflict resolution: between versions 2.0.1, 2.0.0 and 1.0

planet:venus:2.0.1
\\--- planet:mars:4.0.0
     \\--- compileClasspath

planet:venus:1.0 -> 2.0.1
\\--- planet:jupiter:5.0.0
     \\--- compileClasspath

planet:venus:2.0.0 -> 2.0.1
\\--- planet:earth:3.0.0
     \\--- compileClasspath
"""

        when:
        run "dependencyInsight", "--dependency", "pluto"

        then:
        outputContains """> Task :dependencyInsight
planet:pluto:1.0.0
  Variant compile:
    | Attribute Name                 | Provided | Requested    |
    |--------------------------------|----------|--------------|
    | org.gradle.status              | release  |              |
    | org.gradle.category            | library  | library      |
    | org.gradle.libraryelements     | jar      | classes      |
    | org.gradle.usage               | java-api | java-api     |
    | org.gradle.dependency.bundling |          | external     |
    | org.gradle.jvm.environment     |          | standard-jvm |
    | org.gradle.jvm.version         |          | ${jvmVersion.padRight("standard-jvm".length())} |

planet:pluto:1.0.0
\\--- planet:mercury:1.0.2
     +--- planet:jupiter:5.0.0
     |    \\--- compileClasspath
     \\--- planet:venus:2.0.1 (requested planet:mercury:1.0.1)
          +--- planet:earth:3.0.0 (requested planet:venus:2.0.0)
          |    \\--- compileClasspath
          +--- planet:mars:4.0.0
          |    \\--- compileClasspath
          \\--- planet:jupiter:5.0.0 (requested planet:venus:1.0) (*)
"""
    }

    def "reports a strictly on a transitive"() {
        given:
        def foo12 = mavenRepo.module("org", "foo", "1.2").publish()
        mavenRepo.module("org", "foo", "1.5").publish()
        mavenRepo.module("org", "bar", "1.0").dependsOn(foo12).publish()
        buildFile << """
            apply plugin: 'java-library'

            repositories {
               maven { url = "${mavenRepo.uri}" }
            }

            dependencies {
                implementation 'org:bar:1.0'
                constraints {
                    implementation 'org:foo:1.5!!'
                }
            }
"""

        when:
        succeeds 'dependencyInsight', '--dependency', 'foo'

        then:
        outputContains("""> Task :dependencyInsight
org:foo:1.5
  Variant compile:
    | Attribute Name                 | Provided | Requested    |
    |--------------------------------|----------|--------------|
    | org.gradle.status              | release  |              |
    | org.gradle.category            | library  | library      |
    | org.gradle.libraryelements     | jar      | classes      |
    | org.gradle.usage               | java-api | java-api     |
    | org.gradle.dependency.bundling |          | external     |
    | org.gradle.jvm.environment     |          | standard-jvm |
    | org.gradle.jvm.version         |          | ${jvmVersion.padRight("standard-jvm".length())} |
   Selection reasons:
      - By constraint
      - By ancestor

org:foo:{strictly 1.5} -> 1.5
\\--- compileClasspath

org:foo:1.2 -> 1.5
\\--- org:bar:1.0
     \\--- compileClasspath
""")
    }
}
