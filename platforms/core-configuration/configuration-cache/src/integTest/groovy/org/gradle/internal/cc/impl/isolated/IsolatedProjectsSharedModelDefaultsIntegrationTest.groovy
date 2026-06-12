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

import spock.lang.Issue

class IsolatedProjectsSharedModelDefaultsIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    @Issue("https://github.com/gradle/gradle/issues/38028")
    def "reports problem when a project plugin registers shared model defaults"() {
        given:
        buildFile """
            import org.gradle.api.initialization.SharedModelDefaults
            import javax.inject.Inject

            abstract class DefaultsRegisteringPlugin implements Plugin<Project> {
                @Inject
                abstract SharedModelDefaults getSharedModelDefaults()

                void apply(Project project) {
                    sharedModelDefaults.add("myProjectType", String, {})
                }
            }
            apply plugin: DefaultsRegisteringPlugin
        """

        when:
        isolatedProjectsFailsUsing(mode, "help")

        then:
        fixture.assertIsolatedProjectsProblems(mode) {
            projectsConfigured(":")
            problem("Build file 'build.gradle': line 10: Project ':' cannot register shared model defaults. Shared model defaults can only be registered in a settings script, using the 'defaults' block.", 1)
        }

        where:
        mode << ALL_MODES
    }

    def "without isolated projects, a project plugin registering shared model defaults fails with the late IllegalStateException"() {
        given:
        buildFile """
            import org.gradle.api.initialization.SharedModelDefaults
            import javax.inject.Inject

            abstract class DefaultsRegisteringPlugin implements Plugin<Project> {
                @Inject
                abstract SharedModelDefaults getSharedModelDefaults()

                void apply(Project project) {
                    sharedModelDefaults.add("myProjectType", String, {})
                }
            }
            apply plugin: DefaultsRegisteringPlugin
        """

        when:
        fails("help")

        then:
        failure.assertHasCause("Cannot add shared model defaults after processing.")
    }
}
