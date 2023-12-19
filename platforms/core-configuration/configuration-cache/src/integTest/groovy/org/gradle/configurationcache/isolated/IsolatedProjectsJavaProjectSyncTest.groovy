/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.configurationcache.isolated

import org.hamcrest.core.StringContains
import spock.lang.Ignore


class IsolatedProjectsJavaProjectSyncTest extends AbstractIdeSyncSmokeTest {

    @Ignore // not yet implemented
    def "vanilla Java project is IP compatible"() {
        given:
        simpleJavaProject()

        when:
        ideaSync("/Applications/IntelliJ IDEA.app")

        then:
        fixture.assertHtmlReportHasProblems {
            totalProblemsCount = 78
            withLocatedProblem(new StringContains("sync.studio.tooling"), "Cannot access project ':app' from project ':'")
            withLocatedProblem(new StringContains("sync.studio.tooling"), "Cannot access project ':lib' from project ':'")
            withLocatedProblem("Plugin class 'JetGradlePlugin'", "Cannot access project ':app' from project ':'")
            withLocatedProblem("Plugin class 'JetGradlePlugin'", "Cannot access project ':lib' from project ':'")
            withLocatedProblem("Plugin class 'JetGradlePlugin'", "Cannot access project ':' from project ':app'")
            withLocatedProblem("Plugin class 'JetGradlePlugin'", "Cannot access project ':lib' from project ':app'")
            withLocatedProblem("Plugin class 'JetGradlePlugin'", "Cannot access project ':' from project ':lib'")
            withLocatedProblem("Plugin class 'JetGradlePlugin'", "Cannot access project ':app' from project ':lib'")
            withLocatedProblem("Plugin class 'JetGradlePlugin'", "Cannot access project ':app' from project ':'. 'Project.evaluationDependsOn' must be used to establish a dependency between project ':app' and project ':' evaluation")
            withLocatedProblem("Plugin class 'JetGradlePlugin'", "Cannot access project ':lib' from project ':'. 'Project.evaluationDependsOn' must be used to establish a dependency between project ':lib' and project ':' evaluation")
        }
    }

    private void simpleJavaProject() {
        settingsFile << """
            rootProject.name = 'project-under-test'
            include ':app'
            include ':lib'
        """

        file("gradle.properties") << """
            org.gradle.configuration-cache.problems=warn
            org.gradle.unsafe.isolated-projects=true
        """

        file("app/build.gradle") << """
            plugins {
                id 'java'
            }

            dependencies {
                implementation(project(':lib'))
            }
        """

        file("lib/build.gradle") << """
            plugins {
                id 'java'
            }
        """
    }
}
