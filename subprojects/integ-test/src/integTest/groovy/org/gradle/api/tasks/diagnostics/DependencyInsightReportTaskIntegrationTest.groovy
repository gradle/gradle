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

    def "renders the dependency insight"() {
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
            repositories {
                maven { url "${repo.uri}" }
            }

            configurations {
                conf
            }
            dependencies {
                conf 'org:toplevel:1.0', 'org:toplevel2:1.0', 'org:toplevel3:1.0', 'org:toplevel4:1.0'
            }

            task insight(type: DependencyInsightReportTask) {
                configuration = configurations.conf
                includes = { ResolvedDependencyResult dep ->
                    dep.requested.name == 'leaf2'
                }
            }
        """

        when:
        run "insight"

        then:
        1 == 1
        output.contains(toPlatformLineSeparators("""
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
"""))
    }

    //TODO SF more coverage
    // - additional description
    // - no matching dependencies
    // - configuration / dependency not configured
    // - unresolved dependencies
    // - forced versions into something that does not exist in the graph
}
