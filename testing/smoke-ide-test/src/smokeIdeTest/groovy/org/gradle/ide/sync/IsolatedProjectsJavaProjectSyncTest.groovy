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

class IsolatedProjectsJavaProjectSyncTest extends AbstractIdeSyncTest {

    def "can sync simple java build without problems"() {
        given:
        simpleJavaProject()

        when:
        ideaSync(IDEA_COMMUNITY_VERSION)

        then:
        report.assertHtmlReportHasNoProblems()
    }

    private void simpleJavaProject() {
        projectFile("settings.gradle") << """
            rootProject.name = 'project-under-test'
            include ':app'
            include ':lib'
        """

        projectFile("gradle.properties") << """
            org.gradle.unsafe.isolated-projects=true
        """

        projectFile("app/build.gradle") << """
            plugins {
                id 'java'
            }

            dependencies {
                implementation(project(':lib'))
            }
        """

        projectFile("app/src/main/java/App.java") << """
            public class App {
                public static void main(String[] args) { System.out.println(Lib.hello()); }
           }
        """

        projectFile("lib/build.gradle") << """
            plugins {
                id 'java'
            }
        """

        projectFile("lib/src/main/java/Lib.java") << """
            public class Lib {
                public static String hello() { return "Hello, sync!"; }
           }
        """
    }
}
