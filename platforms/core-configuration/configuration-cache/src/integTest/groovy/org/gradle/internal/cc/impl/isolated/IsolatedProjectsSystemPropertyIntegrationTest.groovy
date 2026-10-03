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

class IsolatedProjectsSystemPropertyIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    def "system property mutation in a build script is a violation (#api, #mode)"() {
        buildFile("""
            $api
        """)

        when:
        isolatedProjectsFailsUsing(mode, "help")

        then:
        fixture.assertIsolatedProjectsProblems(mode) {
            projectsConfigured(":")
            problem("Build file 'build.gradle': line 2: $message")
        }

        where:
        api                                                        | message
        "System.setProperty('some.property', 'new.value')"         | "mutation of system property 'some.property'"
        "System.clearProperty('some.property')"                    | "removal of system property 'some.property'"
        "System.properties.put('some.property', 'new.value')"      | "mutation of system property 'some.property'"
        "System.properties.setProperty('some.property', 'x')"      | "mutation of system property 'some.property'"
        "System.getProperties().remove('some.property')"           | "removal of system property 'some.property'"
        "System.properties.putAll(['some.property': 'new.value'])" | "mutation of system property 'some.property'"

        combined:
        mode << ALL_MODES
    }

    def "replacing all system properties in a build script is a violation (#mode)"() {
        buildFile("""
            System.setProperties(new Properties())
        """)

        when:
        isolatedProjectsFailsUsing(mode, "help")

        then:
        fixture.assertIsolatedProjectsProblems(mode) {
            projectsConfigured(":")
            problem("Build file 'build.gradle': line 2: replacing all system properties")
        }

        where:
        mode << ALL_MODES
    }

    def "clearing all system properties in a build script is a violation (#api, #mode)"() {
        buildFile("""
            $api
        """)

        when:
        isolatedProjectsFailsUsing(mode, "help")

        then:
        fixture.assertIsolatedProjectsProblems(mode) {
            projectsConfigured(":")
            problem("Build file 'build.gradle': line 2: clearing all system properties")
        }

        where:
        api << [
            "System.properties.clear()",
            "System.properties.keySet().clear()",
            "System.properties.entrySet().clear()"
        ]

        combined:
        mode << ALL_MODES
    }

    def "system property mutation in a plugin is a violation (#mode)"() {
        file("buildSrc/src/main/groovy/SomePlugin.groovy") << """
            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class SomePlugin implements Plugin<Project> {
                void apply(Project project) {
                    System.setProperty('some.property', 'new.value')
                }
            }
        """
        buildFile("""
            apply plugin: SomePlugin
        """)

        when:
        isolatedProjectsFailsUsing(mode, "help")

        then:
        fixture.assertIsolatedProjectsProblems(mode) {
            projectsConfigured(":buildSrc", ":")
            problem("Plugin class 'SomePlugin': mutation of system property 'some.property'")
        }

        where:
        mode << ALL_MODES
    }

    def "system property mutation in an allprojects block of a #location is a violation (#mode)"() {
        file(scriptPath) << """
            gradle.allprojects {
                System.setProperty('some.property', 'new.value')
            }
        """
        buildFile("")

        when:
        isolatedProjectsFailsUsing(mode, "help", *extraArgs)

        then:
        fixture.assertIsolatedProjectsProblems(mode) {
            projectsConfigured(":")
            problem("${locationPrefix}: line 3: mutation of system property 'some.property'")
        }

        where:
        location          | scriptPath        | extraArgs             | locationPrefix
        "settings script" | "settings.gradle" | []                    | "Settings file 'settings.gradle'"
        "init script"     | "init.gradle"     | ["-I", "init.gradle"] | "Initialization script 'init.gradle'"

        combined:
        mode << ALL_MODES
    }

    // Root init/settings are somewhat special because we expect these to always be serial
    def "system property mutation in root #location is allowed"() {
        file(scriptPath) << """
            System.setProperty('some.property', 'new.value')
            System.clearProperty('other.property')
            System.properties.put('third.property', 'value')
        """
        buildFile("""
            tasks.register("ok")
        """)

        when:
        isolatedProjectsRun("ok", *extraArgs)

        then:
        fixture.assertStateStored {
            projectsConfigured(":")
        }

        where:
        location          | scriptPath        | extraArgs
        "settings script" | "settings.gradle" | []
        "init script"     | "init.gradle"     | ["-I", "init.gradle"]
    }

    // Settings of included builds are evaluated while the root build's settings are prepared,
    // before any project exists, so they still run on a single thread
    def "system property mutation in settings of #kind build is allowed"() {
        settingsFile(settingsSnippet)
        file("included/settings.gradle") << """
            System.setProperty('some.property', 'new.value')
        """
        file("included/build.gradle") << """
            plugins {
                id 'groovy-gradle-plugin'
            }
        """
        file("included/src/main/groovy/my-plugin.gradle") << ""
        buildFile("""
            plugins {
                id 'my-plugin'
            }
            tasks.register("ok")
        """)

        when:
        isolatedProjectsRun("ok")

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":included")
        }

        where:
        kind      | settingsSnippet
        "library" | 'includeBuild("included")'
        "plugin"  | 'pluginManagement { includeBuild("included") }'
    }

    // This is a violation because buildSrc is evaluated after projects have been created, so it's difficult to determine whether the mutation is safe or not,
    // because the check is based on phase and not the type of script being evaluated.
    // We could potentially improve this.
    def "system property mutation in buildSrc settings is a violation (#mode)"() {
        file("buildSrc/settings.gradle") << """
            System.setProperty('some.property', 'new.value')
        """
        file("buildSrc/build.gradle") << ""
        buildFile("""
            tasks.register("ok")
        """)

        when:
        isolatedProjectsFailsUsing(mode, "ok")

        then:
        fixture.assertIsolatedProjectsProblems(mode) {
            projectsConfigured(":buildSrc", ":")
            problem("Settings file 'buildSrc/settings.gradle': line 2: mutation of system property 'some.property'")
        }

        where:
        mode << ALL_MODES
    }

    def "system property mutation in a task action is allowed"() {
        buildFile("""
            tasks.register("mutate") {
                doLast {
                    System.setProperty('some.property', 'new.value')
                    println("value = " + System.getProperty('some.property'))
                }
            }
        """)

        when:
        isolatedProjectsRun("mutate")

        then:
        outputContains("value = new.value")
        fixture.assertStateStored {
            projectsConfigured(":")
        }
    }

    // We might want to throw here? I'm not sure, this has to do with the fact that we generally
    // don't do tracking for value sources, so we don't know that the mutation is happening in a value source.
    def "system property mutation in a value source is not reported"() {
        buildFile("""
            import org.gradle.api.provider.*

            abstract class Mutator implements ValueSource<String, ValueSourceParameters.None> {
                String obtain() {
                    System.setProperty('some.property', 'new.value')
                    return 'done'
                }
            }

            println("source = " + providers.of(Mutator) {}.get())
            tasks.register("ok")
        """)

        when:
        isolatedProjectsRun("ok")

        then:
        outputContains("source = done")
        fixture.assertStateStored {
            projectsConfigured(":")
        }
    }
}
