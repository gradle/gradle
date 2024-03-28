/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.ide.sync

import org.gradle.ide.sync.fixtures.IsolatedProjectsIdeSyncFixture
import org.hamcrest.core.StringContains

class IsolatedProjectsJavaProjectSyncTest extends AbstractSyncSmokeIdeTest {

    private IsolatedProjectsIdeSyncFixture fixture = new IsolatedProjectsIdeSyncFixture(testDirectory)
    /**
     * To run this test locally you should have Android Studio installed in /Applications/Android Studio.*.app folder,
     * or you should set "studioHome" system property with the Android Studio installation path,
     * or you should enable automatic download of Android Studio with the -PautoDownloadAndroidStudio=true.
     *
     * Additionally, you should also have ANDROID_HOME env. variable set with Android SDK (normally on MacOS it's installed in "$HOME/Library/Android/sdk").
     *
     * To enable headless mode run with -PrunAndroidStudioInHeadlessMode=true.
     */
    def "Android Studio sync has known IP violations for vanilla Java project"() {
        given:
        simpleJavaProject()

        when:
        androidStudioSync()

        then:
        fixture.assertHtmlReportHasProblems {
            totalProblemsCount = 76
            withLocatedProblem(new StringContains("sync.studio.tooling"), "Project ':' cannot access 'Project.apply' functionality on subprojects via 'allprojects'")
            withLocatedProblem("Plugin class 'JetGradlePlugin'", "Project ':' cannot access 'Project.gradle' functionality on subprojects via 'allprojects'")
            withLocatedProblem("Plugin class 'JetGradlePlugin'", "Project ':' cannot access 'Project.extensions' functionality on subprojects via 'allprojects'")
            withLocatedProblem("Plugin class 'JetGradlePlugin'", "Project ':' cannot access 'Project.tasks' functionality on subprojects via 'allprojects'")
            withLocatedProblem("Plugin class 'JetGradlePlugin'", "Project ':' cannot access 'Project.plugins' functionality on child projects")
            withLocatedProblem("Plugin class 'JetGradlePlugin'", "Project ':' cannot access 'Project.version' functionality on child projects")
            withLocatedProblem("Plugin class 'JetGradlePlugin'", "Project ':' cannot access 'Project.description' functionality on child projects")
            withLocatedProblem("Plugin class 'JetGradlePlugin'", "Project ':' cannot access 'Project.buildDir' functionality on child projects")
            withLocatedProblem("Plugin class 'JetGradlePlugin'", "Project ':' cannot access 'Project.group' functionality on child projects")
            withLocatedProblem("Plugin class 'JetGradlePlugin'", "Project ':' cannot access 'Project.hasProperty' functionality on child projects")
            withLocatedProblem("Plugin class 'JetGradlePlugin'", "Project ':' cannot access 'testing' extension on child projects")
            withLocatedProblem("Plugin class 'JetGradlePlugin'", "Project ':' cannot access 'Project.gradle' functionality on child projects")
            withLocatedProblem("Plugin class 'JetGradlePlugin'", "Project ':' cannot access 'Project.extensions' functionality on child projects")
            withLocatedProblem("Plugin class 'JetGradlePlugin'", "Project ':' cannot access 'Project.tasks' functionality on child projects")
            withLocatedProblem("Plugin class 'JetGradlePlugin'", "Project ':' cannot access 'Project.findProperty' functionality on child projects")
            withLocatedProblem("Plugin class 'JetGradlePlugin'", "Project ':' cannot access 'Project.configurations' functionality on child projects")
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
