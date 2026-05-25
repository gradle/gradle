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

class IsolatedProjectsCrossProjectGradleAccessIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    def "reports a problem on project-level access to mutable Gradle state via #invocation"() {
        settingsFile "build-logic/settings.gradle", ""
        settingsFile """
            include("a")
            includeBuild("build-logic")
        """
        file("a/build.gradle") << """
            gradle.$invocation
        """

        when:
        isolatedProjectsFails ":a:help"

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":build-logic", ":", ":a")
            problem("Build file 'a/build.gradle': line 2: Project ':a' cannot access Gradle.$problemAccess on build ':'")
        }

        where:
        invocation                                                     | problemAccess
        "getLifecycle()"                                               | "getLifecycle"
        "beforeSettings({})"                                           | "beforeSettings"
        "beforeSettings({} as Action)"                                 | "beforeSettings"
        "settingsEvaluated({})"                                        | "settingsEvaluated"
        "settingsEvaluated({} as Action)"                              | "settingsEvaluated"
        "getProviders()"                                               | "getProviders"
        "getPlugins()"                                                 | "getPlugins"
        "apply([:])"                                                   | "apply"
        "apply({})"                                                    | "apply"
        "apply({} as Action)"                                          | "apply"
        "getPluginManager()"                                           | "getPluginManager"
        "getExtensions()"                                              | "getExtensions"
    }
}
