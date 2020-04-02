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

import groovy.transform.CompileStatic
import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.integtests.resolve.locking.LockfileFixture
import spock.lang.Ignore
import spock.lang.Unroll

class DependencyInsightReportTaskIntegrationTest extends AbstractIntegrationSpec {
    def jvmVersion = JavaVersion.current().majorVersion

    def setup() {
        executer.requireOwnGradleUserHomeDir()
        settingsFile << """
            rootProject.name = 'insight-test'
        """
        new ResolveTestFixture(buildFile).addDefaultVariantDerivationStrategy()
    }

    @ToBeFixedForInstantExecution(because = ":dependencyInsight")
    def "requires use of configuration flag if Java plugin isn't applied"() {
        given:
        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
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

    @ToBeFixedForInstantExecution(because = ":dependencyInsight")
    def "indicates that requested dependency cannot be found for default configuration"() {
        given:
        mavenRepo.module("org", "leaf1").publish()
        mavenRepo.module("org", "leaf2").publish()
        mavenRepo.module("org", "middle").dependsOnModules("leaf1", "leaf2").publish()
        mavenRepo.module("org", "top").dependsOnModules("middle", "leaf2").publish()

        file("build.gradle") << """
            apply plugin: 'java'

            repositories {
                maven { url "${mavenRepo.uri}" }
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

        file("build.gradle") << """
            apply plugin: 'java'

            repositories {
                maven { url "${mavenRepo.uri}" }
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

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:top:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
                setDependencySpec { it.requested.module == 'leaf2' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        outputContains """
org:leaf2:1.0
   variant "runtime" [
      org.gradle.status          = release (not requested)
      org.gradle.usage           = java-runtime (not requested)
      org.gradle.libraryelements = jar (not requested)
      org.gradle.category        = library (not requested)
   ]

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

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
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
   variant "runtime" [
      org.gradle.status          = release (not requested)
      org.gradle.usage           = java-runtime (not requested)
      org.gradle.libraryelements = jar (not requested)
      org.gradle.category        = library (not requested)
   ]
   Selection reasons:
      - By conflict resolution : between versions 2.5, 1.5 and 1.0

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
        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
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
   variant "runtime" [
      org.gradle.status          = release (not requested)
      org.gradle.usage           = java-runtime (not requested)
      org.gradle.libraryelements = jar (not requested)
      org.gradle.category        = library (not requested)
   ]

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
        run "dependencyInsight", "--dependency", "leaf", "--configuration", "conf", "--singlepath"

        then:
        outputContains """
org:leaf:1.0
   variant "runtime" [
      org.gradle.status          = release (not requested)
      org.gradle.usage           = java-runtime (not requested)
      org.gradle.libraryelements = jar (not requested)
      org.gradle.category        = library (not requested)
   ]

org:leaf:1.0
\\--- org:a:1.0
     \\--- org:b:1.0
          \\--- conf
"""
    }

    @ToBeFixedForInstantExecution(because = ":dependencyInsight")
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

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
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
        outputContains """Dependency resolution failed because of conflict(s) on the following module(s):
   - org:leaf2 between versions 2.5, 1.5 and 1.0

org:leaf2:2.5
   variant "runtime" [
      org.gradle.status          = release (not requested)
      org.gradle.usage           = java-runtime (not requested)
      org.gradle.libraryelements = jar (not requested)
      org.gradle.category        = library (not requested)
   ]
   Selection reasons:
      - By conflict resolution : between versions 2.5, 1.5 and 1.0

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

    @ToBeFixedForInstantExecution(because = ":dependencyInsight")
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

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
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
        outputContains """Dependency resolution failed because of conflict(s) on the following module(s):
   - org:leaf2 between versions 2.5, 1.5 and 1.0

org:leaf2:2.5
   variant "runtime" [
      org.gradle.status          = release (not requested)
      org.gradle.usage           = java-runtime (not requested)
      org.gradle.libraryelements = jar (not requested)
      org.gradle.category        = library (not requested)
   ]
   Selection reasons:
      - By conflict resolution : between versions 2.5, 1.5 and 1.0

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

    @ToBeFixedForInstantExecution(because = ":dependencyInsight")
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
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf 'org:foo:1.+'
}
"""

        lockfileFixture.createLockfile('lockedConf', ['org:bar:1.0'], false)

        when:
        succeeds 'dependencyInsight', '--configuration', 'lockedConf', '--dependency', 'foo'

        then:
        outputContains """org:foo:1.1 FAILED
   Selection reasons:
      - By constraint : Dependency locking
   Failures:
      - Dependency lock state out of date:
          - Resolved 'org:foo:1.1' which is not part of the dependency lock state

org:foo:1.1 FAILED
\\--- lockedConf

org:foo:1.+ -> 1.1
\\--- lockedConf
"""
    }

    @ToBeFixedForInstantExecution(because = ":dependencyInsight")
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
        name 'repo'
        url '${mavenRepo.uri}'
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

        lockfileFixture.createLockfile('lockedConf', ['org:foo:1.0'], false)

        when:
        succeeds 'dependencyInsight', '--configuration', 'lockedConf', '--dependency', 'foo'

        then:
        outputContains """
org:foo:1.0 FAILED
   Selection reasons:
      - By constraint : dependency was locked to version '1.0'
   Failures:
      - Could not resolve org:foo:{strictly 1.0}.
          - Cannot find a version of 'org:foo' that satisfies the version constraints:
               Dependency path ':insight-test:unspecified' --> 'org:foo:1.+'
               Constraint path ':insight-test:unspecified' --> 'org:foo:1.1'
               Constraint path ':insight-test:unspecified' --> 'org:foo:{strictly 1.0}' because of the following reason: dependency was locked to version '1.0'

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

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
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
                substitute module('org:leaf') with module('org:leaf:1.0')
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
   variant "runtime" [
      org.gradle.status          = release (not requested)
      org.gradle.usage           = java-runtime (not requested)
      org.gradle.libraryelements = jar (not requested)
      org.gradle.category        = library (not requested)
   ]

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
   variant "runtime" [
      org.gradle.status          = release (not requested)
      org.gradle.usage           = java-runtime (not requested)
      org.gradle.libraryelements = jar (not requested)
      org.gradle.category        = library (not requested)
   ]

org:leaf:1.0
\\--- org:foo:1.0
     \\--- substituted

org:leaf:2.0 -> 1.0
\\--- org:bar:1.0
     \\--- substituted
"""
    }

    def "shows forced dynamic version"() {
        given:
        mavenRepo.module("org", "leaf", "1").publish()
        mavenRepo.module("org", "leaf", "2").publish()
        mavenRepo.module("org", "leaf", "3").publish()

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf('org:leaf:[1,2]') {
                    because 'testing stuff'
                    force true
                }
            }
        """

        when:
        executer.expectDeprecationWarning()
        run "dependencyInsight", "--configuration", "conf", "--dependency", "leaf"

        then:
        outputContains """
org:leaf:2
   variant "runtime" [
      org.gradle.status          = release (not requested)
      org.gradle.usage           = java-runtime (not requested)
      org.gradle.libraryelements = jar (not requested)
      org.gradle.category        = library (not requested)
   ]
   Selection reasons:
      - Was requested : didn't match version 3 because testing stuff
      - Forced

org:leaf:[1,2] -> 2
\\--- conf
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

        file("build.gradle") << """
            repositories {
                ivy { url "${ivyRepo.uri}" }
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
                setDependencySpec { it.requested.module == 'leaf' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        outputContains """
org:leaf:1.0
   variant "default" [
      org.gradle.status = integration (not requested)
   ]

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

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf {
                    resolutionStrategy {
                        eachDependency { it.useVersion('1.0') }
                        eachDependency { it.useVersion('2.0'); it.because("RULE 2") }
                        dependencySubstitution {
                            substitute module('org:foo') because "SUBSTITUTION 1" with module('org:foo:3.0')
                            substitute module('org:foo') because "SUBSTITUTION 2" with module('org:bar:2.0')
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
                configuration = configurations.conf
                setDependencySpec { true }
            }
        """

        when:
        run "insight"

        then:
        outputContains """
org:bar:2.0
   variant "runtime" [
      org.gradle.status          = release (not requested)
      org.gradle.usage           = java-runtime (not requested)
      org.gradle.libraryelements = jar (not requested)
      org.gradle.category        = library (not requested)
   ]
   Selection reasons:
      - Forced
      - Selected by rule
      - Selected by rule : RULE 2
      - Selected by rule : SUBSTITUTION 1
      - Selected by rule : SUBSTITUTION 2
      - Selected by rule : SUBSTITUTION 3

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

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
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
                configuration = configurations.conf
                setDependencySpec { true }
            }
        """

        when:
        run "insight"

        then:
        outputContains """
org.test:bar:2.0
   variant "runtime" [
      org.gradle.status          = release (not requested)
      org.gradle.usage           = java-runtime (not requested)
      org.gradle.libraryelements = jar (not requested)
      org.gradle.category        = library (not requested)
   ]
   Selection reasons:
      - Selected by rule : why not?

org:bar:1.0 -> org.test:bar:2.0
\\--- conf

org:baz:1.0 (selected by rule)
   variant "runtime" [
      org.gradle.status          = release (not requested)
      org.gradle.usage           = java-runtime (not requested)
      org.gradle.libraryelements = jar (not requested)
      org.gradle.category        = library (not requested)
   ]

org:baz:1.0
\\--- conf

org:foo:2.0
   variant "runtime" [
      org.gradle.status          = release (not requested)
      org.gradle.usage           = java-runtime (not requested)
      org.gradle.libraryelements = jar (not requested)
      org.gradle.category        = library (not requested)
   ]
   Selection reasons:
      - Selected by rule : because I am in control

org:foo:1.0 -> 2.0
\\--- conf
"""
    }


    def "shows version and reason with dependency substitution"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "bar", "1.0").publish()
        mavenRepo.module("org", "baz", "2.0").publish()

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
               conf {
                  resolutionStrategy.dependencySubstitution {
                     substitute module('org:foo') because 'foo superseded by bar' with module('org:bar:1.0')
                     substitute module('org:baz') with module('org:baz:2.0')
                  }
               }
            }
            dependencies {
                conf 'org:foo:1.0'
                conf 'org:baz:1.1'
            }
            task insight(type: DependencyInsightReportTask) {
                configuration = configurations.conf
                setDependencySpec { true }
            }
        """

        when:
        run "insight"

        then:
        outputContains """
org:baz:2.0 (selected by rule)
   variant "runtime" [
      org.gradle.status          = release (not requested)
      org.gradle.usage           = java-runtime (not requested)
      org.gradle.libraryelements = jar (not requested)
      org.gradle.category        = library (not requested)
   ]

org:baz:1.1 -> 2.0
\\--- conf

org:bar:1.0
   variant "runtime" [
      org.gradle.status          = release (not requested)
      org.gradle.usage           = java-runtime (not requested)
      org.gradle.libraryelements = jar (not requested)
      org.gradle.category        = library (not requested)
   ]
   Selection reasons:
      - Selected by rule : foo superseded by bar

org:foo:1.0 -> org:bar:1.0
\\--- conf
"""
    }

    def "shows substituted modules"() {
        given:
        mavenRepo.module("org", "new-leaf", "77").publish()

        mavenRepo.module("org", "foo", "2.0").dependsOn('org', 'leaf', '1.0').publish()
        mavenRepo.module("org", "bar", "1.0").dependsOn('org', 'leaf', '2.0').publish()

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf {
                    resolutionStrategy.dependencySubstitution {
                        substitute module('org:leaf') with module('org:new-leaf:77')
                        substitute module('org:foo') with module('org:foo:2.0')
                    }
                }
            }
            dependencies {
                conf 'org:foo:1.0', 'org:bar:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
                configuration = configurations.conf
                setDependencySpec { it.requested.module == 'leaf' }
            }
        """

        when:
        run "insight"

        then:
        outputContains """
org:new-leaf:77 (selected by rule)
   variant "runtime" [
      org.gradle.status          = release (not requested)
      org.gradle.usage           = java-runtime (not requested)
      org.gradle.libraryelements = jar (not requested)
      org.gradle.category        = library (not requested)
   ]

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

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf {
                    resolutionStrategy.dependencySubstitution {
                        substitute module('org:foo:1.0') because('I want to') with module('org:foo:2.0')
                        substitute module('org:bar:1.0') because('I am not sure I want to explain') with module('org:bar:2.0')
                    }
                }
            }
            dependencies {
                conf 'org:foo:1.0', 'org:bar:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
                configuration = configurations.conf
                setDependencySpec { true }
            }
        """

        when:
        run "insight"

        then:
        outputContains """org:bar:2.0
   variant "runtime" [
      org.gradle.status          = release (not requested)
      org.gradle.usage           = java-runtime (not requested)
      org.gradle.libraryelements = jar (not requested)
      org.gradle.category        = library (not requested)
   ]
   Selection reasons:
      - Selected by rule : I am not sure I want to explain

org:bar:1.0 -> 2.0
\\--- conf

org:foo:2.0
   variant "runtime" [
      org.gradle.status          = release (not requested)
      org.gradle.usage           = java-runtime (not requested)
      org.gradle.libraryelements = jar (not requested)
      org.gradle.category        = library (not requested)
   ]
   Selection reasons:
      - Selected by rule : I want to

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

        file("build.gradle") << """
            repositories {
                ivy { url "${ivyRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:top:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
                setDependencySpec { it.requested.module == 'leaf' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        outputContains """
org:leaf:1.6
   variant "default" [
      org.gradle.status = integration (not requested)
   ]

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

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            configurations.conf.resolutionStrategy.force 'org:leaf:2.0'
            dependencies {
                conf 'org:foo:1.0', 'org:bar:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
                configuration = configurations.conf
                setDependencySpec { it.requested.module == 'leaf' }
            }
        """

        when:
        run "insight"

        then:
        outputContains """
org:leaf:2.0 (forced)
   variant "runtime" [
      org.gradle.status          = release (not requested)
      org.gradle.usage           = java-runtime (not requested)
      org.gradle.libraryelements = jar (not requested)
      org.gradle.category        = library (not requested)
   ]

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

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            configurations.conf.resolutionStrategy.force 'org:leaf:1.5'
            dependencies {
                conf 'org:foo:1.0', 'org:bar:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
                configuration = configurations.conf
                setDependencySpec { it.requested.module == 'leaf' }
            }
        """

        when:
        run "insight"

        then:
        outputContains """
org:leaf:1.5 (forced)
   variant "runtime" [
      org.gradle.status          = release (not requested)
      org.gradle.usage           = java-runtime (not requested)
      org.gradle.libraryelements = jar (not requested)
      org.gradle.category        = library (not requested)
   ]

org:leaf:1.0 -> 1.5
\\--- org:foo:1.0
     \\--- conf

org:leaf:2.0 -> 1.5
\\--- org:bar:1.0
     \\--- conf
"""
    }

    def "forced version at dependency level"() {
        given:
        mavenRepo.module("org", "leaf", "1.0").publish()
        mavenRepo.module("org", "leaf", "2.0").publish()

        mavenRepo.module("org", "foo", "1.0").dependsOn('org', 'leaf', '1.0').publish()
        mavenRepo.module("org", "bar", "1.0").dependsOn('org', 'leaf', '2.0').publish()

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:foo:1.0', 'org:bar:1.0'
                conf('org:leaf:1.0') {
                  force = true
                }
            }
            task insight(type: DependencyInsightReportTask) {
                configuration = configurations.conf
                setDependencySpec { it.requested.module == 'leaf' }
            }
        """

        when:
        executer.expectDeprecationWarning()
        run "insight"

        then:
        outputContains """
org:leaf:1.0 (forced)
   variant "runtime" [
      org.gradle.status          = release (not requested)
      org.gradle.usage           = java-runtime (not requested)
      org.gradle.libraryelements = jar (not requested)
      org.gradle.category        = library (not requested)
   ]

org:leaf:1.0
+--- conf
\\--- org:foo:1.0
     \\--- conf

org:leaf:2.0 -> 1.0
\\--- org:bar:1.0
     \\--- conf
"""
    }

    def "forced version combined with constraint"() {
        given:
        mavenRepo.module("org", "leaf", "2.0").publish()
        mavenRepo.module("org", "foo", "1.0").dependsOn('org', 'leaf', '1.0').publish()

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
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
                configuration = configurations.conf
                setDependencySpec { it.requested.module == 'leaf' }
            }
        """

        when:
        run "insight"

        then:
        outputContains """
org:leaf:2.0
   variant "runtime" [
      org.gradle.status          = release (not requested)
      org.gradle.usage           = java-runtime (not requested)
      org.gradle.libraryelements = jar (not requested)
      org.gradle.category        = library (not requested)
   ]
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
        file("build.gradle") << """
            task insight(type: DependencyInsightReportTask) {
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
        file("build.gradle") << """
            configurations {
                conf
            }
            task insight(type: DependencyInsightReportTask) {
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

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:top:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
                setDependencySpec { it.requested.module == 'foo.unknown' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        outputContains("No dependencies matching given input were found")
    }

    @ToBeFixedForInstantExecution(because = ":dependencyInsight")
    def "marks modules that can't be resolved as 'FAILED'"() {
        given:
        mavenRepo.module("org", "top").dependsOnModules("middle").publish()

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:top:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
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

    @ToBeFixedForInstantExecution(because = ":dependencyInsight")
    def "marks modules that can't be resolved after forcing a different version as 'FAILED'"() {
        given:
        mavenRepo.module("org", "top").dependsOn("org", "middle", "1.0").publish()
        mavenRepo.module("org", "middle", "1.0").publish()

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
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
          - ${mavenRepoURL}/org/middle/2.0/middle-2.0.pom
        If the artifact you are trying to retrieve can be found in the repository but without metadata in 'Maven POM' format, you need to adjust the 'metadataSources { ... }' of the repository declaration.

org:middle:1.0 -> 2.0 FAILED
\\--- org:top:1.0
     \\--- conf
"""
    }

    @ToBeFixedForInstantExecution(because = ":dependencyInsight")
    def "marks modules that can't be resolved after conflict resolution as 'FAILED'"() {
        given:
        mavenRepo.module("org", "top").dependsOn("org", "middle", "1.0").publish()
        mavenRepo.module("org", "middle", "1.0").publish()

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:top:1.0'
                conf 'org:middle:2.0'
            }
            task insight(type: DependencyInsightReportTask) {
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

    @ToBeFixedForInstantExecution(because = ":dependencyInsight")
    def "marks modules that can't be resolved after substitution as 'FAILED'"() {
        given:
        mavenRepo.module("org", "top").dependsOn("org", "middle", "1.0").publish()
        mavenRepo.module("org", "middle", "1.0").publish()

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf {
                    resolutionStrategy.dependencySubstitution {
                        substitute module("org:middle") with module("org:middle:2.0+")
                    }
                }
            }
            dependencies {
                conf 'org:top:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
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
          - ${mavenRepoURL}/org/middle/maven-metadata.xml

org:middle:1.0 -> 2.0+ FAILED
\\--- org:top:1.0
     \\--- conf
"""
    }

    @Ignore
    def "shows version resolved from a range where some selectors did not match anything"() {
        given:
        mavenRepo.module("org", "leaf", "1.5").publish()
        mavenRepo.module("org", "top", "1.0")
            .dependsOn("org", "leaf", "1.0")
            .dependsOn("org", "leaf", "[1.5,1.9]")
            .dependsOn("org", "leaf", "0.8+")
            .publish()

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:top:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
                setDependencySpec { it.requested.module == 'leaf' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        outputContains """
org:leaf:1.5 (conflict resolution)

org:leaf:1.0 -> 1.5
\\--- org:top:1.0
     \\--- conf

org:leaf:0.8+ -> 1.5
\\--- org:top:1.0
     \\--- conf

org:leaf:[1.5,1.9] -> 1.5
\\--- org:top:1.0
     \\--- conf
"""
    }

    @ToBeFixedForInstantExecution(because = ":dependencyInsight")
    def "shows multiple failed outgoing dependencies"() {
        given:
        ivyRepo.module("org", "top", "1.0")
            .dependsOn("org", "leaf", "1.0")
            .dependsOn("org", "leaf", "[1.5,2.0]")
            .dependsOn("org", "leaf", "1.6+")
            .publish()

        file("build.gradle") << """
            repositories {
                ivy { url "${ivyRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:top:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
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
          - ${ivyRepoURL}/org/leaf/1.0/ivy-1.0.xml
        If the artifact you are trying to retrieve can be found in the repository but without metadata in 'ivy.xml' format, you need to adjust the 'metadataSources { ... }' of the repository declaration.

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
          - ${ivyRepoURL}/org/leaf/

org:leaf:[1.5,2.0] FAILED
\\--- org:top:1.0
     \\--- conf
"""
    }

    @ToBeFixedForInstantExecution(because = ":dependencyInsight")
    void "marks project dependencies that cannot be resolved as 'FAILED'"() {
        given:
        file("settings.gradle") << "include 'A', 'B', 'C'; rootProject.name='root'"

        file("build.gradle") << """
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
          - Project : declares a dependency from configuration 'conf' to configuration 'default' which is not declared in the descriptor for project :A.

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
          - Project :B declares a dependency from configuration 'default' to configuration 'default' which is not declared in the descriptor for project :C.

project :C FAILED
\\--- project :B
     \\--- conf
"""

    }

    def "deals with dependency cycles"() {
        given:
        mavenRepo.module("org", "leaf1").dependsOnModules("leaf2").publish()
        mavenRepo.module("org", "leaf2").dependsOnModules("leaf1").publish()

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:leaf1:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
                setDependencySpec { it.requested.module == 'leaf2' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        outputContains """
org:leaf2:1.0
   variant "runtime" [
      org.gradle.status          = release (not requested)
      org.gradle.usage           = java-runtime (not requested)
      org.gradle.libraryelements = jar (not requested)
      org.gradle.category        = library (not requested)
   ]

org:leaf2:1.0
\\--- org:leaf1:1.0
     +--- conf
     \\--- org:leaf2:1.0 (*)
"""
    }

    def "deals with dependency cycle to root"() {
        given:
        file("settings.gradle") << "include 'impl'; rootProject.name='root'"

        file("build.gradle") << """
            allprojects {
                apply plugin: 'java-library'
                group = 'org.foo'
                version = '1.0'
            }
            archivesBaseName = 'root'
            dependencies {
                implementation project(":impl")
            }
            project(":impl") {
                dependencies {
                    implementation project(":")
                }
            }
            task insight(type: DependencyInsightReportTask) {
                setDependencySpec { true }
                configuration = configurations.runtimeClasspath
            }
        """

        when:
        run "insight"

        then:
        outputContains """
project :
   variant "runtimeClasspath" [
      org.gradle.usage               = java-runtime
      org.gradle.category            = library
      org.gradle.libraryelements     = jar
      org.gradle.dependency.bundling = external
      org.gradle.jvm.version         = $jvmVersion
   ]
   variant "runtimeElements" [
      org.gradle.usage               = java-runtime
      org.gradle.libraryelements     = jar
      org.gradle.dependency.bundling = external
      org.gradle.category            = library
      org.gradle.jvm.version         = $jvmVersion
   ]

project :
\\--- project :impl
     \\--- project : (*)
"""
    }

    def "selects a module component dependency with a given name"() {
        given:
        mavenRepo.module("org", "leaf1").dependsOnModules("leaf2").publish()
        mavenRepo.module("org", "leaf2").dependsOnModules("leaf3").publish()
        mavenRepo.module("org", "leaf3").publish()

        file("settings.gradle") << "include 'impl'; rootProject.name='root'"

        file("build.gradle") << """
            allprojects {
                apply plugin: 'java-library'
                group = 'org.foo'
                version = '1.0-SNAPSHOT'
                repositories {
                    maven { url "${mavenRepo.uri}" }
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
                setDependencySpec { it.requested instanceof ModuleComponentSelector && it.requested.module == 'leaf2' }
                configuration = configurations.runtimeClasspath
            }
        """

        when:
        run "insight"

        then:
        outputContains """
org:leaf2:1.0
   variant "runtime" [
      org.gradle.status              = release (not requested)
      org.gradle.usage               = java-runtime
      org.gradle.libraryelements     = jar
      org.gradle.category            = library

      Requested attributes not found in the selected variant:
         org.gradle.dependency.bundling = external
         org.gradle.jvm.version         = $jvmVersion
   ]

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

        file("settings.gradle") << "include 'impl'; rootProject.name='root'"

        file("build.gradle") << """
            allprojects {
                apply plugin: 'java-library'
                group = 'org.foo'
                version = '1.0-SNAPSHOT'
                repositories {
                    maven { url "${mavenRepo.uri}" }
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
                setDependencySpec { it.requested instanceof ProjectComponentSelector && it.requested.projectPath == ':impl' }
                configuration = configurations.compileClasspath
            }
        """

        when:
        run "insight"

        then:
        outputContains """
project :impl
   variant "apiElements" [
      org.gradle.usage               = java-api
      org.gradle.libraryelements     = jar (compatible with: classes)
      org.gradle.dependency.bundling = external
      org.gradle.category            = library
      org.gradle.jvm.version         = $jvmVersion
   ]

project :impl
\\--- compileClasspath
"""
    }

    @ToBeFixedForInstantExecution(because = ":dependencyInsight")
    def "selects a module component dependency with a given name with dependency command line option"() {
        given:
        mavenRepo.module("org", "leaf1").dependsOnModules("leaf2").publish()
        mavenRepo.module("org", "leaf2").dependsOnModules("leaf3").publish()
        mavenRepo.module("org", "leaf3").publish()
        mavenRepo.module("org", "leaf4").publish()

        file("settings.gradle") << "include 'api', 'impl'; rootProject.name='root'"

        file("build.gradle") << """
            allprojects {
                apply plugin: 'java-library'
                group = 'org.foo'
                version = '1.0-SNAPSHOT'
                repositories {
                    maven { url "${mavenRepo.uri}" }
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
   variant "compile" [
      org.gradle.status              = release (not requested)
      org.gradle.usage               = java-api
      org.gradle.libraryelements     = jar (compatible with: classes)
      org.gradle.category            = library

      Requested attributes not found in the selected variant:
         org.gradle.dependency.bundling = external
         org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
   ]

org:leaf4:1.0
\\--- project :impl
     \\--- compileClasspath
"""
    }

    @ToBeFixedForInstantExecution(because = ":dependencyInsight")
    def "selects both api and implementation dependencies with dependency command line option"() {
        given:
        mavenRepo.module("org", "leaf1").publish()
        mavenRepo.module("org", "leaf2").publish()

        file("build.gradle") << """
                apply plugin: 'java-library'
                repositories {
                    maven { url "${mavenRepo.uri}" }
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
   variant "compile" [
      org.gradle.status              = release (not requested)
      org.gradle.usage               = java-api
      org.gradle.libraryelements     = jar (compatible with: classes)
      org.gradle.category            = library

      Requested attributes not found in the selected variant:
         org.gradle.dependency.bundling = external
         org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
   ]

org:leaf1:1.0
\\--- compileClasspath
"""

        when:
        run "dependencyInsight", "--dependency", "leaf2"

        then:
        outputContains """
org:leaf2:1.0
   variant "compile" [
      org.gradle.status              = release (not requested)
      org.gradle.usage               = java-api
      org.gradle.libraryelements     = jar (compatible with: classes)
      org.gradle.category            = library

      Requested attributes not found in the selected variant:
         org.gradle.dependency.bundling = external
         org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
   ]

org:leaf2:1.0
\\--- compileClasspath
"""
    }

    @ToBeFixedForInstantExecution(because = ":dependencyInsight")
    def "selects a project component dependency with a given name with dependency command line option"() {
        given:
        mavenRepo.module("org", "leaf1").dependsOnModules("leaf2").publish()
        mavenRepo.module("org", "leaf2").dependsOnModules("leaf3").publish()
        mavenRepo.module("org", "leaf3").publish()
        mavenRepo.module("org", "leaf4").publish()

        file("settings.gradle") << "include 'api', 'impl', 'some:deeply:nested'; rootProject.name='root'"

        file("build.gradle") << """
            allprojects {
                apply plugin: 'java-library'
                group = 'org.foo'
                version = '1.0-SNAPSHOT'
                repositories {
                    maven { url "${mavenRepo.uri}" }
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
   variant "apiElements" [
      org.gradle.usage               = java-api
      org.gradle.libraryelements     = jar (compatible with: classes)
      org.gradle.dependency.bundling = external
      org.gradle.category            = library
      org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
   ]

project :api
\\--- project :impl
     \\--- compileClasspath
"""

        when:
        run "dependencyInsight", "--dependency", ":nested"

        then:
        outputContains """
project :some:deeply:nested
   variant "apiElements" [
      org.gradle.usage               = java-api
      org.gradle.libraryelements     = jar (compatible with: classes)
      org.gradle.dependency.bundling = external
      org.gradle.category            = library
      org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
   ]

project :some:deeply:nested
\\--- compileClasspath
"""

        when:
        run "dependencyInsight", "--dependency", ":some:deeply:nested"

        then:
        outputContains """
project :some:deeply:nested
   variant "apiElements" [
      org.gradle.usage               = java-api
      org.gradle.libraryelements     = jar (compatible with: classes)
      org.gradle.dependency.bundling = external
      org.gradle.category            = library
      org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
   ]

project :some:deeply:nested
\\--- compileClasspath
"""
    }

    @ToBeFixedForInstantExecution(because = ":dependencyInsight")
    def "renders tree with a mix of project and external dependencies"() {
        given:
        mavenRepo.module("org", "leaf1").dependsOnModules("leaf2").publish()
        mavenRepo.module("org", "leaf2").dependsOnModules("leaf3").publish()
        mavenRepo.module("org", "leaf3").publish()

        file("settings.gradle") << "include 'api', 'impl'; rootProject.name='root'"

        file("build.gradle") << """
            allprojects {
                apply plugin: 'java-library'
                group = 'org.foo'
                version = '1.0-SNAPSHOT'
                repositories {
                    maven { url "${mavenRepo.uri}" }
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
   variant "compile" [
      org.gradle.status              = release (not requested)
      org.gradle.usage               = java-api
      org.gradle.libraryelements     = jar (compatible with: classes)
      org.gradle.category            = library

      Requested attributes not found in the selected variant:
         org.gradle.dependency.bundling = external
         org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
   ]

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

        file("build.gradle") << """
            repositories {
               maven { url "${mavenRepo.uri}" }
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

        when:
        run "dependencyInsight", "--dependency", "foo", "--configuration", "compile"

        then:
        result.groupedOutput.task(":dependencyInsight").output.contains("""foo:bar:2.0
   variant "runtime" [
      org.gradle.status          = release (not requested)
      org.gradle.usage           = java-runtime (not requested)
      org.gradle.libraryelements = jar (not requested)
      org.gradle.category        = library (not requested)
   ]

foo:bar:2.0
\\--- compile

foo:foo:1.0
   variant "runtime" [
      org.gradle.status          = release (not requested)
      org.gradle.usage           = java-runtime (not requested)
      org.gradle.libraryelements = jar (not requested)
      org.gradle.category        = library (not requested)
   ]

foo:foo:1.0
\\--- compile
""")
    }

    @Unroll
    @ToBeFixedForInstantExecution(because = ":dependencyInsight")
    def "renders dependency constraint differently"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "foo", "1.1").publish()
        mavenRepo.module("org", "foo", "1.2").publish()
        mavenRepo.module("org", "foo", "1.3").publish()
        mavenRepo.module("org", "foo", "1.4").publish()
        mavenRepo.module("org", "foo", "1.5").publish()
        mavenRepo.module("org", "foo", "2.0").publish()

        file("build.gradle") << """
            apply plugin: 'java-library'

            repositories {
               maven { url "${mavenRepo.uri}" }
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
   variant "compile" [
      org.gradle.status              = release (not requested)
      org.gradle.usage               = java-api
      org.gradle.libraryelements     = jar (compatible with: classes)
      org.gradle.category            = library

      Requested attributes not found in the selected variant:
         org.gradle.dependency.bundling = external
         org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
   ]
   Selection reasons:
      - By constraint : $rejected

org:foo -> $selected
\\--- compileClasspath"""

        where:
        version                             | selected | rejected
        "require '[1.0, 2.0)'"              | '1.5'    | "didn't match version 2.0"
        "strictly '[1.1, 1.4]'"             | '1.4'    | "didn't match versions 2.0, 1.5"
        "prefer '[1.0, 1.4]'; reject '1.4'" | '1.3'    | "rejected version 1.4"
    }

    @Unroll
    @ToBeFixedForInstantExecution(because = ":dependencyInsight")
    def "renders custom dependency constraint reasons (#version)"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "foo", "1.1").publish()
        mavenRepo.module("org", "foo", "1.2").publish()
        mavenRepo.module("org", "foo", "1.3").publish()
        mavenRepo.module("org", "foo", "1.4").publish()
        mavenRepo.module("org", "foo", "1.5").publish()
        mavenRepo.module("org", "foo", "2.0").publish()

        file("build.gradle") << """
            apply plugin: 'java-library'

            repositories {
               maven { url "${mavenRepo.uri}" }
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
   variant "compile" [
      org.gradle.status              = release (not requested)
      org.gradle.usage               = java-api
      org.gradle.libraryelements     = jar (compatible with: classes)
      org.gradle.category            = library

      Requested attributes not found in the selected variant:
         org.gradle.dependency.bundling = external
         org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
   ]
   Selection reasons:
      - By constraint : ${rejected}${reason}

org:foo -> $selected
\\--- compileClasspath
"""
        where:
        version                             | reason                                          | selected | rejected
        "require '[1.0, 2.0)'"              | "foo v2+ has an incompatible API for project X" | '1.5'    | "didn't match version 2.0 because "
        "strictly '[1.1, 1.4]'"             | "versions of foo verified to run on platform Y" | '1.4'    | "didn't match versions 2.0, 1.5 because "
        "prefer '[1.0, 1.4]'; reject '1.4'" | "1.4 has a critical bug"                        | '1.3'    | "rejected version 1.4 because "
    }

    @Unroll
    @ToBeFixedForInstantExecution(because = ":dependencyInsight")
    def "renders custom dependency reasons"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "foo", "1.1").publish()
        mavenRepo.module("org", "foo", "1.2").publish()
        mavenRepo.module("org", "foo", "1.3").publish()
        mavenRepo.module("org", "foo", "1.4").publish()
        mavenRepo.module("org", "foo", "1.5").publish()
        mavenRepo.module("org", "foo", "2.0").publish()

        file("build.gradle") << """
            apply plugin: 'java-library'

            repositories {
               maven { url "${mavenRepo.uri}" }
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
   variant "compile" [
      org.gradle.status              = release (not requested)
      org.gradle.usage               = java-api
      org.gradle.libraryelements     = jar (compatible with: classes)
      org.gradle.category            = library

      Requested attributes not found in the selected variant:
         org.gradle.dependency.bundling = external
         org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
   ]
   Selection reasons:
      - Was requested : ${rejected}${reason}

org:foo:${displayVersion} -> $selected
\\--- compileClasspath
"""
        where:
        version                             | displayVersion                    | reason                                          | selected | rejected
        "require '[1.0, 2.0)'"              | '[1.0, 2.0)'                      | "foo v2+ has an incompatible API for project X" | '1.5'    | "didn't match version 2.0 because "
        "strictly '[1.1, 1.4]'"             | '{strictly [1.1, 1.4]}'           | "versions of foo verified to run on platform Y" | '1.4'    | "didn't match versions 2.0, 1.5 because "
        "prefer '[1.0, 1.4]'; reject '1.4'" | '{prefer [1.0, 1.4]; reject 1.4}' | "1.4 has a critical bug"                        | '1.3'    | "rejected version 1.4 because "
    }

    @ToBeFixedForInstantExecution(because = ":dependencyInsight")
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
        file("build.gradle") << """
            apply plugin: 'java-library'

            repositories {
               maven { url "${mavenRepo.uri}" }
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
   variant "compile" [
      org.gradle.status              = release (not requested)
      org.gradle.usage               = java-api
      org.gradle.libraryelements     = jar (compatible with: classes)
      org.gradle.category            = library

      Requested attributes not found in the selected variant:
         org.gradle.dependency.bundling = external
         org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
   ]
   Selection reasons:
      - Was requested : didn't match versions 2.0, 1.5, 1.4

org:foo:[1.1,1.3] -> 1.3
+--- compileClasspath
\\--- org:bar:1.0
     \\--- compileClasspath
"""
    }

    @ToBeFixedForInstantExecution(because = ":dependencyInsight")
    def "doesn't mix rejected versions on different constraints"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "foo", "1.1").publish()
        mavenRepo.module("org", "foo", "1.2").publish()
        mavenRepo.module("org", "bar", "1.0").publish()
        mavenRepo.module("org", "bar", "1.1").publish()
        mavenRepo.module("org", "bar", "1.2").publish()

        file("build.gradle") << """
            apply plugin: 'java-library'

            repositories {
               maven { url "${mavenRepo.uri}" }
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
   variant "compile" [
      org.gradle.status              = release (not requested)
      org.gradle.usage               = java-api
      org.gradle.libraryelements     = jar (compatible with: classes)
      org.gradle.category            = library

      Requested attributes not found in the selected variant:
         org.gradle.dependency.bundling = external
         org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
   ]
   Selection reasons:
      - Was requested : rejected versions 1.2, 1.1

org:bar:{require [1.0,); reject [1.1, 1.2]} -> 1.0
\\--- compileClasspath

org:foo:1.1
   variant "compile" [
      org.gradle.status              = release (not requested)
      org.gradle.usage               = java-api
      org.gradle.libraryelements     = jar (compatible with: classes)
      org.gradle.category            = library

      Requested attributes not found in the selected variant:
         org.gradle.dependency.bundling = external
         org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
   ]
   Selection reasons:
      - Was requested : rejected version 1.2

org:foo:{require [1.0,); reject 1.2} -> 1.1
\\--- compileClasspath
"""
    }

    @ToBeFixedForInstantExecution(because = ":dependencyInsight")
    def "shows versions rejected by rule"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "foo", "1.1").publish()
        mavenRepo.module("org", "foo", "1.2").publish()
        mavenRepo.module("org", "bar", "1.0").publish()
        mavenRepo.module("org", "bar", "1.1").publish()
        mavenRepo.module("org", "bar", "1.2").publish()

        file("build.gradle") << """
            apply plugin: 'java-library'

            repositories {
               maven { url "${mavenRepo.uri}" }
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
   variant "compile" [
      org.gradle.status              = release (not requested)
      org.gradle.usage               = java-api
      org.gradle.libraryelements     = jar (compatible with: classes)
      org.gradle.category            = library

      Requested attributes not found in the selected variant:
         org.gradle.dependency.bundling = external
         org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
   ]
   Selection reasons:
      - Rejection : 1.2 by rule because version 1.2 is bad
      - Rejection : 1.1 by rule because version 1.1 is bad

org:bar:[1.0,) -> 1.0
\\--- compileClasspath

org:foo:1.1
   variant "compile" [
      org.gradle.status              = release (not requested)
      org.gradle.usage               = java-api
      org.gradle.libraryelements     = jar (compatible with: classes)
      org.gradle.category            = library

      Requested attributes not found in the selected variant:
         org.gradle.dependency.bundling = external
         org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
   ]
   Selection reasons:
      - Was requested : rejected version 1.2

org:foo:{require [1.0,); reject 1.2} -> 1.1
\\--- compileClasspath
"""
    }

    @Unroll
    @ToBeFixedForInstantExecution(because = ":dependencyInsight")
    def "renders dependency from BOM as a constraint"() {
        given:
        def leaf = mavenRepo.module("org", "leaf", "1.0").publish()
        def bom = mavenRepo.module('org', 'bom', '1.0')
        bom.packaging = 'pom'
        bom.dependencyConstraint(leaf)
        bom.publish()

        file("build.gradle") << """
            apply plugin: 'java-library'

            repositories {
               maven { url "${mavenRepo.uri}" }
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
   variant "compile" [
      org.gradle.status              = release (not requested)
      org.gradle.usage               = java-api
      org.gradle.libraryelements     = jar (compatible with: classes)
      org.gradle.category            = library

      Requested attributes not found in the selected variant:
         org.gradle.dependency.bundling = external
         org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
   ]

org:leaf:1.0
\\--- org:bom:1.0
     \\--- compileClasspath

org:leaf -> 1.0
\\--- compileClasspath
"""
        where:
        conf << ["",
                 // this is just a sanity check. Nobody should ever write this.
                 "{ capabilities { requireCapability('org:bom-derived-platform') } }" ]
    }

    @ToBeFixedForInstantExecution(because = ":dependencyInsight")
    def "shows published dependency reason"() {
        given:
        mavenRepo.with {
            def leaf = module('org.test', 'leaf', '1.0').publish()
            module('org.test', 'a', '1.0')
                .dependsOn(leaf, reason: 'first reason')
                .withModuleMetadata()
                .publish()

        }

        file("build.gradle") << """
            apply plugin: 'java-library'

            repositories {
               maven { url "${mavenRepo.uri}" }
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
   variant "compile" [
      org.gradle.status              = release (not requested)
      org.gradle.usage               = java-api
      org.gradle.libraryelements     = jar (compatible with: classes)
      org.gradle.category            = library

      Requested attributes not found in the selected variant:
         org.gradle.dependency.bundling = external
         org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
   ]
   Selection reasons:
      - Was requested : first reason

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

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:top:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
                setDependencySpec { it.requested.module == 'leaf2' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        outputContains """
org:leaf2:1.0
   variant "runtime" [
      org.gradle.status          = release (not requested)
      org.gradle.usage           = java-runtime (not requested)
      org.gradle.libraryelements = jar (not requested)
      org.gradle.category        = library (not requested)
   ]

org:leaf2:1.0
+--- org:middle:1.0
|    \\--- org:top:1.0
|         \\--- conf
\\--- org:top:1.0 (*)

(*) - dependencies omitted (listed previously)

A web-based, searchable dependency report is available by adding the --scan option.
"""
    }

    @ToBeFixedForInstantExecution(because = ":dependencyInsight")
    def "renders multiple rejected modules"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "foo", "1.1").publish()
        mavenRepo.module("org", "foo", "1.2").publish()
        mavenRepo.module("org", "bar", "1.0").publish()
        mavenRepo.module("org", "bar", "1.1").publish()
        mavenRepo.module("org", "bar", "1.2").publish()


        file("build.gradle") << """
            apply plugin: 'java-library'

            repositories {
               maven { url "${mavenRepo.uri}" }
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
      - By constraint : Nope, you won't use this
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

    @ToBeFixedForInstantExecution(because = ":dependencyInsight")
    def "renders multiple rejection reasons for module"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "foo", "1.1").publish()
        mavenRepo.module("org", "foo", "1.2").publish()


        file("build.gradle") << """
            apply plugin: 'java-library'

            repositories {
               maven { url "${mavenRepo.uri}" }
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
   variant "compile" [
      org.gradle.status              = release (not requested)
      org.gradle.usage               = java-api
      org.gradle.libraryelements     = jar (compatible with: classes)
      org.gradle.category            = library

      Requested attributes not found in the selected variant:
         org.gradle.dependency.bundling = external
         org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
   ]
   Selection reasons:
      - By constraint
      - Was requested : rejected versions 1.2, 1.1

org:foo:{reject 1.2} -> 1.0
\\--- compileClasspath

org:foo:{require [1.0,); reject 1.1} -> 1.0
\\--- compileClasspath
"""
    }

    @ToBeFixedForInstantExecution(because = ":dependencyInsight")
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

        file("build.gradle") << """
            apply plugin: 'java-library'

            repositories {
               maven { url "${mavenRepo.uri}" }
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
   variant "compile" [
      org.gradle.status              = release (not requested)
      org.gradle.usage               = java-api
      org.gradle.libraryelements     = jar (compatible with: classes)
      org.gradle.category            = library

      Requested attributes not found in the selected variant:
         org.gradle.dependency.bundling = external
         org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
   ]
   Selection reasons:
      - Was requested : first reason
      - Was requested : transitive reason

org.test:leaf:1.0
+--- org.test:a:1.0
|    \\--- compileClasspath
\\--- org.test:c:1.0
     \\--- org.test:b:1.0
          \\--- compileClasspath
"""
    }

    @Unroll
    @ToBeFixedForInstantExecution(because = ":dependencyInsight")
    def "shows that version is rejected because of attributes (#type)"() {
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "foo", "1.1").publish()
        mavenRepo.module("org", "foo", "1.2").publish()

        def attributes = """{
                    attributes {
                        attribute(COLOR, 'blue')
                    }
                }"""

        file("build.gradle") << """
            apply plugin: 'java-library'

            def COLOR = Attribute.of('color', String)

            repositories {
               maven { url "${mavenRepo.uri}" }
            }

            configurations {
               compileClasspath ${type=='configuration'?attributes:''}
            }

            dependencies {
                implementation('org:foo:[1.0,)') ${type=='dependency'?attributes:''}

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
   variant "compile" [
      color                          = blue
      org.gradle.status              = release (not requested)
      org.gradle.usage               = java-api
      org.gradle.libraryelements     = jar (compatible with: classes)
      org.gradle.category            = library

      Requested attributes not found in the selected variant:
         org.gradle.dependency.bundling = external
         org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
   ]
   Selection reasons:
      - Rejection : version 1.2:
          - Attribute 'color' didn't match. Requested 'blue', was: 'red'
          - Attribute 'org.gradle.category' didn't match. Requested 'library', was: not found
          - Attribute 'org.gradle.dependency.bundling' didn't match. Requested 'external', was: not found
          - Attribute 'org.gradle.jvm.version' didn't match. Requested '${JavaVersion.current().majorVersion}', was: not found
          - Attribute 'org.gradle.libraryelements' didn't match. Requested 'classes', was: not found
          - Attribute 'org.gradle.usage' didn't match. Requested 'java-api', was: not found
      - Rejection : version 1.1:
          - Attribute 'color' didn't match. Requested 'blue', was: 'green'
          - Attribute 'org.gradle.category' didn't match. Requested 'library', was: not found
          - Attribute 'org.gradle.dependency.bundling' didn't match. Requested 'external', was: not found
          - Attribute 'org.gradle.jvm.version' didn't match. Requested '${JavaVersion.current().majorVersion}', was: not found
          - Attribute 'org.gradle.libraryelements' didn't match. Requested 'classes', was: not found
          - Attribute 'org.gradle.usage' didn't match. Requested 'java-api', was: not found

org:foo:[1.0,) -> 1.0
\\--- compileClasspath
"""
        where:
        type << ['configuration', 'dependency']
    }

    @ToBeFixedForInstantExecution(because = ":dependencyInsight")
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

        file("build.gradle") << """
            apply plugin: 'java-library'

            repositories {
               maven { url "${mavenRepo.uri}" }
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
   variant "compile" [
      org.gradle.status              = release (not requested)
      org.gradle.usage               = java-api
      org.gradle.libraryelements     = jar (compatible with: classes)
      org.gradle.category            = library

      Requested attributes not found in the selected variant:
         org.gradle.dependency.bundling = external
         org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
   ]
   Selection reasons:
      - By conflict resolution : between versions 1.0.2 and 1.0.1

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
   variant "compile" [
      org.gradle.status              = release (not requested)
      org.gradle.usage               = java-api
      org.gradle.libraryelements     = jar (compatible with: classes)
      org.gradle.category            = library

      Requested attributes not found in the selected variant:
         org.gradle.dependency.bundling = external
         org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
   ]
   Selection reasons:
      - By conflict resolution : between versions 2.0.1, 2.0.0 and 1.0

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
   variant "compile" [
      org.gradle.status              = release (not requested)
      org.gradle.usage               = java-api
      org.gradle.libraryelements     = jar (compatible with: classes)
      org.gradle.category            = library

      Requested attributes not found in the selected variant:
         org.gradle.dependency.bundling = external
         org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
   ]

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


    @CompileStatic
    static String decodeURI(URI uri) {
        def url = URLDecoder.decode(uri.toASCIIString(), 'utf-8')
        if (url.endsWith('/')) {
            url = url.substring(0, url.length()-1)
        }
        url
    }

    @CompileStatic
    String getMavenRepoURL() {
        decodeURI(mavenRepo.uri)
    }

    @CompileStatic
    String getIvyRepoURL() {
        decodeURI(ivyRepo.uri)
    }
}
