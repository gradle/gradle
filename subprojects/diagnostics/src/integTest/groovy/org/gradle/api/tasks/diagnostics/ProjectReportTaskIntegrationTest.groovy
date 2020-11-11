/*
 * Copyright 2020 the original author or authors.
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
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

class ProjectReportTaskIntegrationTest extends AbstractIntegrationSpec {

    @ToBeFixedForConfigurationCache
    def "reports project structure with single composite"() {
        given:
        file("settings.gradle") << """rootProject.name = 'my-root-project'
include('p1')
include('p2')
include('p2:p22')
includeBuild('another')"""
        file('another/settings.gradle') << """include('a1')
include('a2')
include('a2:a22')"""

        when:
        run ":projects"

        then:
        outputContains """
Root project 'my-root-project'
+--- Project ':p1'
\\--- Project ':p2'
     \\--- Project ':p2:p22'

Project ':another' (included build)
+--- Project ':another:a1'
\\--- Project ':another:a2'
     \\--- Project ':another:a2:a22'
"""
    }

    @ToBeFixedForConfigurationCache
    def "reports project structure with single empty composite"() {
        given:
        file("settings.gradle") << """rootProject.name = 'my-root-project'
includeBuild('another')
        """
        file('another/settings.gradle') << ""

        when:
        run ":projects"

        then:
        outputContains """
Root project 'my-root-project'
No sub-projects

Project ':another' (included build)
No sub-projects"""
    }

    @ToBeFixedForConfigurationCache
    def "reports project structure with transitive composite"() {
        given:
        file("settings.gradle") << """rootProject.name = 'my-root-project'
includeBuild('another')"""
        file('another/settings.gradle') << "includeBuild('../third')"
        file('third/settings.gradle') << "include('t1')"

        when:
        run ":projects"

        then:
        outputContains """
Root project 'my-root-project'
No sub-projects

Project ':another' (included build)
No sub-projects

Project ':third' (included build)
\\--- Project ':third:t1'"""
    }

    @ToBeFixedForConfigurationCache
    def "included builds are only shown in the context of the root project"() {
        given:
        file("settings.gradle") << """rootProject.name = 'my-root-project'
include('p1')
include('p1:p11')
includeBuild('another')"""
        file("p1").mkdir()
        file('another/settings.gradle') << ""

        when:
        projectDir("p1")
        run "projects"

        then:
        outputDoesNotContain "another"
    }
}
