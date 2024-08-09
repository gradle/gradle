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


class IsolatedProjectsJavaProjectSyncTest extends AbstractIdeaSyncTest {

    private IsolatedProjectsIdeSyncFixture fixture = new IsolatedProjectsIdeSyncFixture(testDirectory)

    def "IDEA sync has known IP violations for vanilla Java project"() {
        given:
        simpleJavaProject()

        when:
        ideaSync(IDEA_VERSION)

        then:
        fixture.assertHtmlReportHasNoProblems()
    }

    private void simpleJavaProject() {
        file("settings.gradle") << """
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
