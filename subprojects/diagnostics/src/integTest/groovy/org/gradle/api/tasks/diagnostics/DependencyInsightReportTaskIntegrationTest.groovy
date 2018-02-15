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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.FeaturePreviewsFixture
import spock.lang.Ignore
import spock.lang.Unroll

class DependencyInsightReportTaskIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        executer.requireOwnGradleUserHomeDir()
    }

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
        output.contains """
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
        output.contains """
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
        output.contains """
org:leaf2:1.0
   variant "runtime"
+--- org:middle:1.0
|    \\--- org:top:1.0
|         \\--- conf
\\--- org:top:1.0 (*)

(*) - dependencies omitted (listed previously)
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
        output.contains """
org:leaf2:2.5 (conflict resolution)
   variant "runtime"
\\--- org:toplevel3:1.0
     \\--- conf

org:leaf2:1.0 -> 2.5
   variant "runtime"
+--- org:middle1:1.0
|    \\--- org:toplevel:1.0
|         \\--- conf
\\--- org:middle3:1.0
     \\--- org:toplevel4:1.0
          \\--- conf

org:leaf2:1.5 -> 2.5
   variant "runtime"
\\--- org:toplevel2:1.0
     \\--- conf
"""
    }

    def "shows forced version"() {
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
            configurations.conf.resolutionStrategy.force 'org:leaf:1.0'
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
        output.contains """
org:leaf:1.0 (forced)
   variant "runtime"
\\--- org:foo:1.0
     \\--- conf

org:leaf:2.0 -> 1.0
   variant "runtime"
\\--- org:bar:1.0
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
        output.contains """
org:leaf:1.0
   variant "runtime"
\\--- org:middle:1.0
     \\--- org:top:1.0
          \\--- conf

org:leaf:[1.0,2.0] -> 1.0
   variant "runtime"
\\--- org:middle:1.0
     \\--- org:top:1.0
          \\--- conf

org:leaf:latest.integration -> 1.0
   variant "runtime"
\\--- org:middle:1.0
     \\--- org:top:1.0
          \\--- conf
"""
    }

    def "shows versions substitute by resolve rule"() {
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
                conf {
                    resolutionStrategy.eachDependency { if (it.requested.name == 'leaf') { it.useVersion('1.0') } }
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
        output.contains """
org:leaf:1.0 (selected by rule)
   variant "runtime"
\\--- org:foo:1.0
     \\--- conf

org:leaf:2.0 -> 1.0
   variant "runtime"
\\--- org:bar:1.0
     \\--- conf
"""
    }

    def "shows custom selection reason using eachDependency"() {
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
        output.contains """
org.test:bar:2.0 (why not?)
   variant "default"

org:bar:1.0 -> org.test:bar:2.0
   variant "default"
\\--- conf

org:baz:1.0 (selected by rule)
   variant "default"
\\--- conf

org:foo:2.0 (because I am in control)
   variant "default"

org:foo:1.0 -> 2.0
   variant "default"
\\--- conf
"""
    }


    def "shows custom selection reason with dependency substitution"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "bar", "1.0").publish()

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
               conf {
                  resolutionStrategy.dependencySubstitution {
                     all {
                        it.useTarget('org:bar:1.0', 'foo superceded by bar')
                     }
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
        output.contains """org:bar:1.0 (foo superceded by bar)
   variant "default"

org:foo:1.0 -> org:bar:1.0
   variant "default"
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
        output.contains """
org:new-leaf:77 (selected by rule)
   variant "runtime"

org:leaf:1.0 -> org:new-leaf:77
   variant "runtime"
\\--- org:foo:2.0
     \\--- conf

org:leaf:2.0 -> org:new-leaf:77
   variant "runtime"
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
        output.contains """
org:bar:2.0 (I am not sure I want to explain)
   variant "default"

org:bar:1.0 -> 2.0
   variant "default"
\\--- conf

org:foo:2.0 (I want to)
   variant "default"

org:foo:1.0 -> 2.0
   variant "default"
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
        output.contains """
org:leaf:1.6
   variant "runtime"

org:leaf:1.+ -> 1.6
   variant "runtime"
\\--- org:top:1.0
     \\--- conf

org:leaf:[1.5,1.9] -> 1.6
   variant "runtime"
\\--- org:top:1.0
     \\--- conf

org:leaf:latest.integration -> 1.6
   variant "runtime"
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
        output.contains """
org:leaf:2.0 (forced)
   variant "runtime"
\\--- org:bar:1.0
     \\--- conf

org:leaf:1.0 -> 2.0
   variant "runtime"
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
        output.contains """
org:leaf:1.5 (forced)
   variant "runtime"

org:leaf:1.0 -> 1.5
   variant "runtime"
\\--- org:foo:1.0
     \\--- conf

org:leaf:2.0 -> 1.5
   variant "runtime"
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
        run "insight"

        then:
        output.contains """
org:leaf:1.0 (forced)
   variant "default"
+--- conf
\\--- org:foo:1.0
     \\--- conf

org:leaf:2.0 -> 1.0
   variant "default"
\\--- org:bar:1.0
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
        output.contains("No dependencies matching given input were found")
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
        output.contains("No dependencies matching given input were found")
    }

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
        output.contains """
org:middle:1.0 FAILED
\\--- org:top:1.0
     \\--- conf
"""
    }

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
        output.contains """
org:middle:2.0 (forced) FAILED

org:middle:1.0 -> 2.0 FAILED
\\--- org:top:1.0
     \\--- conf
"""
    }

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
        output.contains """
org:middle:2.0 (conflict resolution) FAILED
\\--- conf

org:middle:1.0 -> 2.0 FAILED
\\--- org:top:1.0
     \\--- conf
"""
    }

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
        output.contains """
org:middle:2.0+ (selected by rule) FAILED

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
        output.contains """
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
        output.contains """
org:leaf:1.0 FAILED
\\--- org:top:1.0
     \\--- conf

org:leaf:1.6+ FAILED
\\--- org:top:1.0
     \\--- conf

org:leaf:[1.5,2.0] FAILED
\\--- org:top:1.0
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
        output.contains """
org:leaf2:1.0
   variant "runtime"
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
                apply plugin: 'java'
                group = 'org.foo'
                version = '1.0'
            }
            archivesBaseName = 'root'
            dependencies {
                compile project(":impl")
            }
            project(":impl") {
                dependencies {
                    compile project(":")
                }
            }
            task insight(type: DependencyInsightReportTask) {
                setDependencySpec { true }
                configuration = configurations.compile
            }
        """

        when:
        run "insight"

        then:
        output.contains """
project :
   variant "compile"
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
                apply plugin: 'java'
                group = 'org.foo'
                version = '1.0-SNAPSHOT'
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
            }
            dependencies {
                compile project(':impl')
            }
            project(':impl') {
                dependencies {
                    compile 'org:leaf1:1.0'
                }
            }
            task insight(type: DependencyInsightReportTask) {
                setDependencySpec { it.requested instanceof ModuleComponentSelector && it.requested.module == 'leaf2' }
                configuration = configurations.compile
            }
        """

        when:
        run "insight"

        then:
        output.contains """
org:leaf2:1.0
   variant "runtime"
\\--- org:leaf1:1.0
     \\--- project :impl
          \\--- compile
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
                apply plugin: 'java'
                group = 'org.foo'
                version = '1.0-SNAPSHOT'
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
            }
            dependencies {
                compile project(':impl')
            }
            project(':impl') {
                dependencies {
                    compile 'org:leaf1:1.0'
                }
            }
            task insight(type: DependencyInsightReportTask) {
                setDependencySpec { it.requested instanceof ProjectComponentSelector && it.requested.projectPath == ':impl' }
                configuration = configurations.compile
            }
        """

        when:
        run "insight"

        then:
        output.contains """
project :impl
   variant "runtimeElements" [
      org.gradle.usage = java-runtime-jars (not requested)
   ]
\\--- compile
"""
    }

    def "selects a module component dependency with a given name with dependency command line option"() {
        given:
        mavenRepo.module("org", "leaf1").dependsOnModules("leaf2").publish()
        mavenRepo.module("org", "leaf2").dependsOnModules("leaf3").publish()
        mavenRepo.module("org", "leaf3").publish()
        mavenRepo.module("org", "leaf4").publish()

        file("settings.gradle") << "include 'api', 'impl'; rootProject.name='root'"

        file("build.gradle") << """
            allprojects {
                apply plugin: 'java'
                group = 'org.foo'
                version = '1.0-SNAPSHOT'
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
            }
            dependencies {
                compile project(':impl')
            }
            project(':api') {
                dependencies {
                    compile 'org:leaf1:1.0'
                }
            }
            project(':impl') {
                dependencies {
                    compile project(':api')
                    compile 'org:leaf4:1.0'
                }
            }
        """

        when:
        run "dependencyInsight", "--dependency", "leaf4"

        then:
        output.contains """
org:leaf4:1.0
   variant "default" [
      Requested attributes not found in the selected variant:
         org.gradle.usage = java-api
   ]
\\--- project :impl
     \\--- compileClasspath
"""
    }

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
        output.contains """
org:leaf1:1.0
   variant "default" [
      Requested attributes not found in the selected variant:
         org.gradle.usage = java-api
   ]
\\--- compileClasspath
"""

        when:
        run "dependencyInsight", "--dependency", "leaf2"

        then:
        output.contains """
org:leaf2:1.0
   variant "default" [
      Requested attributes not found in the selected variant:
         org.gradle.usage = java-api
   ]
\\--- compileClasspath
"""
    }

    def "selects a project component dependency with a given name with dependency command line option"() {
        given:
        mavenRepo.module("org", "leaf1").dependsOnModules("leaf2").publish()
        mavenRepo.module("org", "leaf2").dependsOnModules("leaf3").publish()
        mavenRepo.module("org", "leaf3").publish()
        mavenRepo.module("org", "leaf4").publish()

        file("settings.gradle") << "include 'api', 'impl'; rootProject.name='root'"

        file("build.gradle") << """
            allprojects {
                apply plugin: 'java'
                group = 'org.foo'
                version = '1.0-SNAPSHOT'
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
            }
            dependencies {
                compile project(':impl')
            }
            project(':api') {
                dependencies {
                    compile 'org:leaf1:1.0'
                }
            }
            project(':impl') {
                dependencies {
                    compile project(':api')
                    compile 'org:leaf4:1.0'
                }
            }
        """

        when:
        run "dependencyInsight", "--dependency", ":api"

        then:
        output.contains """
project :api
   variant "apiElements" [
      org.gradle.usage = java-api
   ]
\\--- project :impl
     \\--- compileClasspath
"""
    }

    def "renders tree with a mix of project and external dependencies"() {
        given:
        mavenRepo.module("org", "leaf1").dependsOnModules("leaf2").publish()
        mavenRepo.module("org", "leaf2").dependsOnModules("leaf3").publish()
        mavenRepo.module("org", "leaf3").publish()

        file("settings.gradle") << "include 'api', 'impl'; rootProject.name='root'"

        file("build.gradle") << """
            allprojects {
                apply plugin: 'java'
                group = 'org.foo'
                version = '1.0-SNAPSHOT'
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
            }
            dependencies {
                compile project(':impl')
            }
            project(':api') {
                dependencies {
                    compile 'org:leaf2:1.0'
                }
            }
            project(':impl') {
                dependencies {
                    compile project(':api')
                    compile 'org:leaf1:1.0'
                }
            }
        """

        when:
        run "dependencyInsight", "--dependency", "leaf3"

        then:
        output.contains """
org:leaf3:1.0
   variant "runtime" [
      Requested attributes not found in the selected variant:
         org.gradle.usage = java-api
   ]
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
        failure.assertHasCause("Resolving configuration 'api' directly is not allowed")

        when:
        run "dependencyInsight", "--dependency", "foo", "--configuration", "compile"

        then:
        output.contains """:dependencyInsight
foo:bar:2.0
   variant "default"
\\--- compile

foo:foo:1.0
   variant "default"
\\--- compile"""
    }


    @Unroll
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
        output.contains """org:foo:$selected (via constraint)
   variant "default" [
      Requested attributes not found in the selected variant:
         org.gradle.usage = java-api
   ]

org:foo -> $selected
   variant "default" [
      Requested attributes not found in the selected variant:
         org.gradle.usage = java-api
   ]
\\--- compileClasspath
"""
        where:
        version                             | selected
        "prefer '[1.0, 2.0)'"               | '1.5'
        "strictly '[1.1, 1.4]'"             | '1.4'
        "prefer '[1.0, 1.4]'; reject '1.4'" | '1.3'
    }

    @Unroll
    def "renders custom dependency constraint reasons"() {
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
        output.contains """org:foo:$selected (via constraint, $reason)
   variant "default" [
      Requested attributes not found in the selected variant:
         org.gradle.usage = java-api
   ]

org:foo -> $selected
   variant "default" [
      Requested attributes not found in the selected variant:
         org.gradle.usage = java-api
   ]
\\--- compileClasspath
"""
        where:
        version                             | reason                                          | selected
        "prefer '[1.0, 2.0)'"               | "foo v2+ has an incompatible API for project X" | '1.5'
        "strictly '[1.1, 1.4]'"             | "versions of foo verified to run on platform Y" | '1.4'
        "prefer '[1.0, 1.4]'; reject '1.4'" | "1.4 has a critical bug"                        | '1.3'
    }

    @Unroll
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
        output.contains """org:foo:$selected ($reason)
   variant "default" [
      Requested attributes not found in the selected variant:
         org.gradle.usage = java-api
   ]

org:foo:$displayVersion -> $selected
   variant "default" [
      Requested attributes not found in the selected variant:
         org.gradle.usage = java-api
   ]
\\--- compileClasspath
"""
        where:
        version                             | displayVersion | reason                                          | selected
        "prefer '[1.0, 2.0)'"               | '[1.0, 2.0)'   | "foo v2+ has an incompatible API for project X" | '1.5'
        "strictly '[1.1, 1.4]'"             | '[1.1, 1.4]'   | "versions of foo verified to run on platform Y" | '1.4'
        "prefer '[1.0, 1.4]'; reject '1.4'" | '[1.0, 1.4]'   | "1.4 has a critical bug"                        | '1.3'
    }

    def "renders dependency from BOM as a constraint"() {
        given:
        def leaf = mavenRepo.module("org", "leaf", "1.0").publish()
        def bom = mavenRepo.module('org', 'bom', '1.0')
        bom.packaging = 'pom'
        bom.dependencyConstraint(leaf)
        bom.publish()

        FeaturePreviewsFixture.enableImprovedPomSupport(settingsFile)

        file("build.gradle") << """
            apply plugin: 'java-library'
            
            repositories {
               maven { url "${mavenRepo.uri}" }
            }
            
            dependencies {
                implementation 'org:bom:1.0'
                implementation 'org:leaf'
            }
        """

        when:
        run "dependencyInsight", "--dependency", "leaf"

        then:
        output.contains """org:leaf:1.0 (via constraint)
   variant "compile" [
      org.gradle.usage = java-api
   ]
\\--- org:bom:1.0
     \\--- compileClasspath

org:leaf -> 1.0
   variant "compile" [
      org.gradle.usage = java-api
   ]
\\--- compileClasspath
"""
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
        FeaturePreviewsFixture.enableGradleMetadata(settingsFile)

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
        output.contains """org.test:leaf:1.0 (first reason)
   variant "default" [
      Requested attributes not found in the selected variant:
         org.gradle.usage = java-api
   ]
\\--- org.test:a:1.0
     \\--- compileClasspath"""
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
        output.contains """
org:leaf2:1.0
   variant "runtime"
+--- org:middle:1.0
|    \\--- org:top:1.0
|         \\--- conf
\\--- org:top:1.0 (*)

(*) - dependencies omitted (listed previously)

A web-based, searchable dependency report is available by adding the --scan option.
"""
    }
}
