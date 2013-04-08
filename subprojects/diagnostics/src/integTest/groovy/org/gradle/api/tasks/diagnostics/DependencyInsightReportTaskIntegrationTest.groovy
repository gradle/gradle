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

import static org.gradle.util.TextUtil.toPlatformLineSeparators

class DependencyInsightReportTaskIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        executer.requireOwnGradleUserHomeDir()
    }

    def "shows basic single tree with repeated dependency"() {
        given:
        mavenRepo.module("org", "leaf1").publish()
        mavenRepo.module("org", "leaf2").publish()

        mavenRepo.module("org", "middle").dependsOn("leaf1", "leaf2").publish()

        mavenRepo.module("org", "top").dependsOn("middle", "leaf2").publish()

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
                setDependencySpec { it.requested.name == 'leaf2' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        output.contains(toPlatformLineSeparators("""
org:leaf2:1.0
+--- org:middle:1.0
|    \\--- org:top:1.0
|         \\--- conf
\\--- org:top:1.0 (*)

(*) - dependencies omitted (listed previously)
"""))
    }

    def "basic dependency insight with conflicting versions"() {
        given:
        mavenRepo.module("org", "leaf1").publish()
        mavenRepo.module("org", "leaf2").publish()
        mavenRepo.module("org", "leaf2", 1.5).publish()
        mavenRepo.module("org", "leaf2", 2.5).publish()
        mavenRepo.module("org", "leaf3").publish()
        mavenRepo.module("org", "leaf4").publish()

        mavenRepo.module("org", "middle1").dependsOn('leaf1', 'leaf2').publish()
        mavenRepo.module("org", "middle2").dependsOn('leaf3', 'leaf4').publish()
        mavenRepo.module("org", "middle3").dependsOn('leaf2').publish()

        mavenRepo.module("org", "toplevel").dependsOn("middle1", "middle2").publish()

        mavenRepo.module("org", "toplevel2").dependsOn("org", "leaf2", "1.5").publish()
        mavenRepo.module("org", "toplevel3").dependsOn("org", "leaf2", "2.5").publish()

        mavenRepo.module("org", "toplevel4").dependsOn("middle3").publish()

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
        output.contains(toPlatformLineSeparators("""
org:leaf2:2.5 (conflict resolution)
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
"""))
    }

    def "shows forced version"() {
        given:
        mavenRepo.module("org", "leaf", 1.0).publish()
        mavenRepo.module("org", "leaf", 2.0).publish()

        mavenRepo.module("org", "foo", 1.0).dependsOn('org', 'leaf', '1.0').publish()
        mavenRepo.module("org", "bar", 1.0).dependsOn('org', 'leaf', '2.0').publish()

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
                setDependencySpec { it.requested.name == 'leaf' }
            }
        """

        when:
        run "insight"

        then:
        output.contains(toPlatformLineSeparators("""
org:leaf:1.0 (forced)
\\--- org:foo:1.0
     \\--- conf

org:leaf:2.0 -> 1.0
\\--- org:bar:1.0
     \\--- conf
"""))
    }

    def "shows multiple outgoing dependencies"() {
        given:
        mavenRepo.module("org", "leaf", "1.0").publish()
        mavenRepo.module("org", "middle", "1.0")
                .dependsOn("org", "leaf", "1.0")
                .dependsOn("org", "leaf", "[1.0,2.0]")
                .dependsOn("org", "leaf", "latest.integration")
                .publish()
        mavenRepo.module("org", "top", "1.0")
                .dependsOn("org", "middle", "1.0")
                .dependsOn("org", "middle", "[1.0,2.0]")
                .dependsOn("org", "middle", "latest.integration")
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
                conf 'org:top:[1.0,2.0]'
                conf 'org:top:latest.integration'
            }
            task insight(type: DependencyInsightReportTask) {
                setDependencySpec { it.requested.name == 'leaf' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        // TODO - need to use a fixed ordering for dynamic requested versions
        output.contains(toPlatformLineSeparators("""
org:leaf:1.0
\\--- org:middle:1.0
     \\--- org:top:1.0
          \\--- conf
"""))
        output.contains(toPlatformLineSeparators("""
org:leaf:latest.integration -> 1.0
\\--- org:middle:1.0
     \\--- org:top:1.0
          \\--- conf
"""))
        output.contains(toPlatformLineSeparators("""
org:leaf:[1.0,2.0] -> 1.0
\\--- org:middle:1.0
     \\--- org:top:1.0
          \\--- conf
"""))
    }

    def "shows substituted versions"() {
        given:
        mavenRepo.module("org", "leaf", 1.0).publish()
        mavenRepo.module("org", "leaf", 2.0).publish()

        mavenRepo.module("org", "foo", 1.0).dependsOn('org', 'leaf', '1.0').publish()
        mavenRepo.module("org", "bar", 1.0).dependsOn('org', 'leaf', '2.0').publish()

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
                setDependencySpec { it.requested.name == 'leaf' }
            }
        """

        when:
        run "insight"

        then:
        output.contains(toPlatformLineSeparators("""
org:leaf:1.0 (selected by rule)
\\--- org:foo:1.0
     \\--- conf

org:leaf:2.0 -> 1.0
\\--- org:bar:1.0
     \\--- conf
"""))
    }

    def "forced version matches the conflict resolution"() {
        given:
        mavenRepo.module("org", "leaf", 1.0).publish()
        mavenRepo.module("org", "leaf", 2.0).publish()

        mavenRepo.module("org", "foo", 1.0).dependsOn('org', 'leaf', '1.0').publish()
        mavenRepo.module("org", "bar", 1.0).dependsOn('org', 'leaf', '2.0').publish()

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
                setDependencySpec { it.requested.name == 'leaf' }
            }
        """

        when:
        run "insight"

        then:
        output.contains(toPlatformLineSeparators("""
org:leaf:2.0 (forced)
\\--- org:bar:1.0
     \\--- conf

org:leaf:1.0 -> 2.0
\\--- org:foo:1.0
     \\--- conf
"""))
    }

    def "forced version does not match anything in the graph"() {
        given:
        mavenRepo.module("org", "leaf", 1.0).publish()
        mavenRepo.module("org", "leaf", 2.0).publish()
        mavenRepo.module("org", "leaf", 1.5).publish()

        mavenRepo.module("org", "foo", 1.0).dependsOn('org', 'leaf', '1.0').publish()
        mavenRepo.module("org", "bar", 1.0).dependsOn('org', 'leaf', '2.0').publish()

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
                setDependencySpec { it.requested.name == 'leaf' }
            }
        """

        when:
        run "insight"

        then:
        output.contains(toPlatformLineSeparators("""
org:leaf:1.5 (forced)

org:leaf:1.0 -> 1.5
\\--- org:foo:1.0
     \\--- conf

org:leaf:2.0 -> 1.5
\\--- org:bar:1.0
     \\--- conf
"""))
    }

    def "forced version at dependency level"() {
        given:
        mavenRepo.module("org", "leaf", 1.0).publish()
        mavenRepo.module("org", "leaf", 2.0).publish()

        mavenRepo.module("org", "foo", 1.0).dependsOn('org', 'leaf', '1.0').publish()
        mavenRepo.module("org", "bar", 1.0).dependsOn('org', 'leaf', '2.0').publish()

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
                setDependencySpec { it.requested.name == 'leaf' }
            }
        """

        when:
        run "insight"

        then:
        output.contains(toPlatformLineSeparators("""
org:leaf:1.0 (forced)
+--- conf
\\--- org:foo:1.0
     \\--- conf

org:leaf:2.0 -> 1.0
\\--- org:bar:1.0
     \\--- conf
"""))
    }

    def "shows decent failure when inputs missing"() {
        given:
        file("build.gradle") << """
            task insight(type: DependencyInsightReportTask) {
                setDependencySpec { it.requested.name == 'leaf2' }
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
                setDependencySpec { it.requested.name == 'whatever' }
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
                setDependencySpec { it.requested.name == 'foo.unknown' }
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
        mavenRepo.module("org", "top").dependsOn("middle").publish()

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
                setDependencySpec { it.requested.name == 'middle' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        output.contains(toPlatformLineSeparators("""
org:middle:1.0 FAILED
\\--- org:top:1.0
     \\--- conf
"""))
    }

    def "marks modules that can't be resolved after forcing a different version as 'FAILED'"() {
        given:
        mavenRepo.module("org", "top").dependsOn("org", "middle", "1.0").publish()
        mavenRepo.module("org", "middle", 1.0).publish()

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
                setDependencySpec { it.requested.name == 'middle' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        output.contains(toPlatformLineSeparators("""
org:middle:2.0 (forced) FAILED

org:middle:1.0 -> 2.0 FAILED
\\--- org:top:1.0
     \\--- conf
"""))
    }

    def "marks modules that can't be resolved after conflict resolution as 'FAILED'"() {
        given:
        mavenRepo.module("org", "top").dependsOn("org", "middle", "1.0").publish()
        mavenRepo.module("org", "middle", 1.0).publish()

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
                setDependencySpec { it.requested.name == 'middle' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        output.contains(toPlatformLineSeparators("""
org:middle:2.0 (conflict resolution) FAILED
\\--- conf

org:middle:1.0 -> 2.0 FAILED
\\--- org:top:1.0
     \\--- conf
"""))
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
                    resolutionStrategy.eachDependency { if (it.requested.name == 'middle') { it.useVersion('2.0+') } }
                }
            }
            dependencies {
                conf 'org:top:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
                setDependencySpec { it.requested.name == 'middle' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        output.contains(toPlatformLineSeparators("""
org:middle:2.0+ (selected by rule) FAILED

org:middle:1.0 -> 2.0+ FAILED
\\--- org:top:1.0
     \\--- conf
"""))
    }

    def "shows version resolved from a range"() {
        given:
        mavenRepo.module("org", "leaf", "1.5").publish()
        mavenRepo.module("org", "top", "1.0")
                .dependsOn("org", "leaf", "1.0")
                .dependsOn("org", "leaf", "[1.5,1.9]")
                .dependsOn("org", "leaf", "1.3+")
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
                setDependencySpec { it.requested.name == 'leaf' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        output.contains(toPlatformLineSeparators("""
org:leaf:1.5 (conflict resolution)

org:leaf:1.0 -> 1.5
\\--- org:top:1.0
     \\--- conf

org:leaf:1.3+ -> 1.5
\\--- org:top:1.0
     \\--- conf

org:leaf:[1.5,1.9] -> 1.5
\\--- org:top:1.0
     \\--- conf
"""))
    }

    def "shows multiple failed outgoing dependencies"() {
        given:
        mavenRepo.module("org", "top", "1.0")
                .dependsOn("org", "leaf", "1.0")
                .dependsOn("org", "leaf", "[1.5,2.0]")
                .dependsOn("org", "leaf", "1.6+")
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
                setDependencySpec { it.requested.name == 'leaf' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        // TODO - need to use a fixed ordering for dynamic requested versions
        output.contains(toPlatformLineSeparators("""
org:leaf:1.0 FAILED
\\--- org:top:1.0
     \\--- conf
"""))
        output.contains(toPlatformLineSeparators("""
org:leaf:1.6+ FAILED
\\--- org:top:1.0
     \\--- conf
"""))
        output.contains(toPlatformLineSeparators("""
org:leaf:[1.5,2.0] FAILED
\\--- org:top:1.0
     \\--- conf
"""))
    }

    def "deals with dependency cycles"() {
        given:
        mavenRepo.module("org", "leaf1").dependsOn("leaf2").publish()
        mavenRepo.module("org", "leaf2").dependsOn("leaf1").publish()

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
                setDependencySpec { it.requested.name == 'leaf2' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        output.contains(toPlatformLineSeparators("""
org:leaf2:1.0
\\--- org:leaf1:1.0
     +--- conf
     \\--- org:leaf2:1.0 (*)
"""))
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
        output.contains(toPlatformLineSeparators("""
org.foo:root:1.0
\\--- org.foo:impl:1.0
     \\--- org.foo:root:1.0 (*)"""))
    }

    def "shows project dependencies"() {
        given:
        mavenRepo.module("org", "leaf1").dependsOn("leaf2").publish()
        mavenRepo.module("org", "leaf2").dependsOn("leaf3").publish()
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
                setDependencySpec { it.requested.name == 'leaf2' }
                configuration = configurations.compile
            }
        """

        when:
        run "insight"

        then:
        output.contains(toPlatformLineSeparators("""
org:leaf2:1.0
\\--- org:leaf1:1.0
     \\--- org.foo:impl:1.0-SNAPSHOT
          \\--- compile
"""))
    }
}
