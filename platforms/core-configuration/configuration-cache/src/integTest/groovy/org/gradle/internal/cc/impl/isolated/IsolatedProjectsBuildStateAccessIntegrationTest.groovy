/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.cc.impl.isolated

class IsolatedProjectsBuildStateAccessIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    def "reports a problem on settings-level access to mutable state of the parent build"() {
        settingsFile("build-logic/settings.gradle", """
            gradle.parent.$invocation
        """)
        settingsFile """
            includeBuild("build-logic")
        """

        when:
        isolatedProjectsFails "help"

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":build-logic")
            problem(problemMessage)
        }

        where:
        invocation                     | problemMessage
        "getSharedServices()"          | "Settings file 'build-logic/settings.gradle': line 2: Build ':build-logic' cannot access Gradle.getSharedServices on build ':'"
        "getOwner()"                   | "Settings file 'build-logic/settings.gradle': line 2: Build ':build-logic' cannot access Gradle.getOwner on build ':'"
        "getServices()"                | "Settings file 'build-logic/settings.gradle': line 2: Build ':build-logic' cannot access Gradle.getServices on build ':'"
        "getStartParameter()"          | "Settings file 'build-logic/settings.gradle': line 2: Build ':build-logic' cannot access Gradle.getStartParameter on build ':'"
        "getPlugins()"                 | "Settings file 'build-logic/settings.gradle': line 2: Build ':build-logic' cannot access Gradle.getPlugins on build ':'"
        "getPluginManager()"           | "Settings file 'build-logic/settings.gradle': line 2: Build ':build-logic' cannot access Gradle.getPluginManager on build ':'"
        "getExtensions()"              | "Settings file 'build-logic/settings.gradle': line 2: Build ':build-logic' cannot access Gradle.getExtensions on build ':'"
        "getProjectRegistry()"         | "Settings file 'build-logic/settings.gradle': line 2: Build ':build-logic' cannot access Gradle.getProjectRegistry on build ':'"
        "allprojects { }"              | "Settings file 'build-logic/settings.gradle': line 2: Build ':build-logic' cannot access Gradle.allprojects on build ':'"
        "beforeProject { }"            | "Settings file 'build-logic/settings.gradle': line 2: Build ':build-logic' cannot access Gradle.beforeProject on build ':'"
        "afterProject { }"             | "Settings file 'build-logic/settings.gradle': line 2: Build ':build-logic' cannot access Gradle.afterProject on build ':'"
        "projectsEvaluated { }"        | "Settings file 'build-logic/settings.gradle': line 2: Build ':build-logic' cannot access Gradle.projectsEvaluated on build ':'"
    }
}
