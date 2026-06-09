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

class IsolatedProjectsStartParameterIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    def "mutating StartParameter after settings evaluation is a violation (#location)"() {
        // The violation pipeline (onMutableCall -> listener -> IP problem) is the same for every setter
        // and only the call location varies, so one representative setter exercised from each location
        // covers it; that every individual setter is instrumented is checked by the fast reflective unit
        // test StartParameterMutationInstrumentationTest. The root case has no included build on purpose:
        // adding one would make its configured-project set depend on the mode (in fail-fast the root
        // throws before the included build is configured, in diagnostics everything is configured).
        if (scriptPath.startsWith("included/")) {
            settingsFile("""
                includeBuild("included")
            """)
        }
        file(scriptPath) << """
            ${apiExpr}.setDryRun(true)
        """

        when:
        isolatedProjectsFailsUsing(mode, "help")

        then:
        fixture.assertIsolatedProjectsProblems(mode) {
            projectsConfigured(*configured)
            problem("Build file '${scriptPath}': line 2: Cannot call 'setDryRun(boolean)' on StartParameter after settings have been evaluated when Isolated Projects is enabled.")
        }

        where:
        location           | scriptPath              | apiExpr                        | configured
        "root project"     | "build.gradle"          | "gradle.startParameter"        | [":"]
        "included project" | "included/build.gradle" | "gradle.startParameter"        | [":", ":included"]
        "parent build"     | "included/build.gradle" | "gradle.parent.startParameter" | [":", ":included"]

        combined:
        mode << ALL_MODES
    }

    def "mutating StartParameter from root build settings script is allowed"() {
        settingsFile("""
            gradle.startParameter.setDryRun(true)
        """)

        when:
        isolatedProjectsRun("help")

        then:
        fixture.assertStateStored {
            projectsConfigured(":")
        }
    }

    def "mutating StartParameter from included build settings script is allowed"() {
        settingsFile("""
            includeBuild("included")
        """)
        file("included/settings.gradle") << """
            gradle.startParameter.setDryRun(true)
        """

        when:
        isolatedProjectsRun("help")

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":included")
        }
    }

    def "configuring the StartParameter of a GradleBuild task is not a violation"() {
        // The task's StartParameter is a copy created to define the nested build; mutating it
        // configures a build that has not started yet, which is the window where mutation is legal.
        // The mutation listener only guards a running build's own StartParameter.
        settingsFile("""
            rootProject.name = 'outer'
        """)
        buildFile("""
            tasks.register('nested', GradleBuild) {
                dir = file('other')
                tasks = ['hello']
                startParameter.setOffline(true)
                startParameter.projectProperties.put('greeting', 'hi from outer')
            }
        """)
        file("other/settings.gradle") << """
            rootProject.name = 'inner'
        """
        file("other/build.gradle") << """
            def greeting = providers.gradleProperty('greeting')
            def offline = gradle.startParameter.offline
            tasks.register('hello') {
                doLast { println("inner says: \${greeting.get()}, offline=\$offline") }
            }
        """

        when:
        isolatedProjectsRun("nested")

        then:
        outputContains("inner says: hi from outer, offline=true")
        fixture.assertStateStored {
            // ":" is the outer build; ":other" is the nested build run by the task
            projectsConfigured(":", ":other")
        }
    }

}
