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
import org.gradle.integtests.fixtures.MavenRepository

import static org.gradle.util.TextUtil.toPlatformLineSeparators

class DependencyInsightReportTaskIntegrationTest extends AbstractIntegrationSpec {

    def repo = new MavenRepository(file("repo"))

    def setup() {
        distribution.requireOwnUserHomeDir()
    }

    def "shows basic single tree"() {
        given:
        repo.module("org", "leaf1").publish()
        repo.module("org", "leaf2").publish()

        repo.module("org", "middle").dependsOn("leaf1", "leaf2").publish()

        repo.module("org", "top").dependsOn("middle", "leaf2").publish()

        file("build.gradle") << """
            repositories {
                maven { url "${repo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:top:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
                includes = { it.requested.name == 'leaf2' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        output.contains(toPlatformLineSeparators("""
org:leaf2:1.0
+--- org:top:1.0
|    \\--- conf
\\--- org:middle:1.0
     \\--- org:top:1.0
          \\--- conf
"""))
    }

    def "basic dependency insight with conflicting versions"() {
        given:
        repo.module("org", "leaf1").publish()
        repo.module("org", "leaf2").publish()
        repo.module("org", "leaf2", 1.5).publish()
        repo.module("org", "leaf2", 2.5).publish()
        repo.module("org", "leaf3").publish()
        repo.module("org", "leaf4").publish()

        repo.module("org", "middle1").dependsOn('leaf1', 'leaf2').publish()
        repo.module("org", "middle2").dependsOn('leaf3', 'leaf4').publish()
        repo.module("org", "middle3").dependsOn('leaf2').publish()

        repo.module("org", "toplevel").dependsOn("middle1", "middle2").publish()

        repo.module("org", "toplevel2").dependsOn("org", "leaf2", "1.5").publish()
        repo.module("org", "toplevel3").dependsOn("org", "leaf2", "2.5").publish()

        repo.module("org", "toplevel4").dependsOn("middle3").publish()

        file("build.gradle") << """
            apply plugin: DependencyReportingPlugin

            repositories {
                maven { url "${repo.uri}" }
            }

            configurations {
                conf
            }
            dependencies {
                conf 'org:toplevel:1.0', 'org:toplevel2:1.0', 'org:toplevel3:1.0', 'org:toplevel4:1.0'
            }
        """

        when:
        run "dependencyInsight", "--includes", "leaf2", "--configuration", "conf"

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
        repo.module("org", "leaf", 1.0).publish()
        repo.module("org", "leaf", 2.0).publish()

        repo.module("org", "foo", 1.0).dependsOn('org', 'leaf', '1.0').publish()
        repo.module("org", "bar", 1.0).dependsOn('org', 'leaf', '2.0').publish()

        file("build.gradle") << """
            repositories {
                maven { url "${repo.uri}" }
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
                includes = { it.requested.name == 'leaf' }
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

    def "forced version matches the conflict resolution"() {
        given:
        repo.module("org", "leaf", 1.0).publish()
        repo.module("org", "leaf", 2.0).publish()

        repo.module("org", "foo", 1.0).dependsOn('org', 'leaf', '1.0').publish()
        repo.module("org", "bar", 1.0).dependsOn('org', 'leaf', '2.0').publish()

        file("build.gradle") << """
            repositories {
                maven { url "${repo.uri}" }
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
                includes = { it.requested.name == 'leaf' }
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
        repo.module("org", "leaf", 1.0).publish()
        repo.module("org", "leaf", 2.0).publish()
        repo.module("org", "leaf", 1.5).publish()

        repo.module("org", "foo", 1.0).dependsOn('org', 'leaf', '1.0').publish()
        repo.module("org", "bar", 1.0).dependsOn('org', 'leaf', '2.0').publish()

        file("build.gradle") << """
            repositories {
                maven { url "${repo.uri}" }
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
                includes = { it.requested.name == 'leaf' }
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
        repo.module("org", "leaf", 1.0).publish()
        repo.module("org", "leaf", 2.0).publish()

        repo.module("org", "foo", 1.0).dependsOn('org', 'leaf', '1.0').publish()
        repo.module("org", "bar", 1.0).dependsOn('org', 'leaf', '2.0').publish()

        file("build.gradle") << """
            repositories {
                maven { url "${repo.uri}" }
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
                includes = { it.requested.name == 'leaf' }
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
                includes = { it.requested.name == 'leaf2' }
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
                includes = { it.requested.name == 'whatever' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        output.contains("No resolved dependencies found")
    }

    def "informs that nothing matches the input dependency"() {
        given:
        repo.module("org", "top").publish()

        file("build.gradle") << """
            repositories {
                maven { url "${repo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:top:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
                includes = { it.requested.name == 'foo.unknown' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        output.contains("No resolved dependencies matching given input were found")
    }

    def "deals with unresolved dependencies"() {
        given:
        repo.module("org", "top").dependsOn("middle").publish()

        file("build.gradle") << """
            repositories {
                maven { url "${repo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:top:1.0'
            }
            task insight(type: DependencyInsightReportTask) {
                includes = { it.requested.name == 'middle' }
                configuration = configurations.conf
            }
        """

        when:
        run "insight"

        then:
        output.contains("No resolved dependencies matching given input were found")
    }

    //TODO SF more coverage
    // - cycle
}
