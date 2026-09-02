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

package org.gradle.api.provider

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Property mutation provenance: reporting which plugin, script plugin or build script configured a property.
 */
class PropertyProvenanceIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        file("buildSrc/build.gradle") << """
            plugins { id("groovy-gradle-plugin") }
        """
        file("buildSrc/src/main/groovy/com/example/Show.groovy") << """
            package com.example

            import org.gradle.api.DefaultTask
            import org.gradle.api.provider.Property
            import org.gradle.api.tasks.Input
            import org.gradle.api.tasks.Internal
            import org.gradle.api.tasks.TaskAction

            abstract class Show extends DefaultTask {
                // An input property is finalized before the task runs, so mutating it from a task
                // action is rejected. That rejection is where provenance shows up.
                @Input
                abstract Property<String> getProp()

                @Internal
                abstract Property<String> getOther()

                @TaskAction
                void show() {
                    println("prop = " + prop.get())
                }
            }
        """
        buildFile """
            import com.example.Show
        """
    }

    private void withProvenanceEnabled() {
        executer.withArgument("-Dorg.gradle.internal.property-provenance=true")
    }

    def "reports the build script that configured a property"() {
        withProvenanceEnabled()
        buildFile """
            tasks.register("show", Show) {
                prop = "value"
                doLast {
                    prop.set("other")
                }
            }
        """

        when:
        fails("show")

        then:
        failureCauseContains("It was last set by build file 'build.gradle'.")
    }

    def "reports the plugin that configured a property, across a deferred callback"() {
        withProvenanceEnabled()
        pluginWithId("""
            project.tasks.register("show", com.example.Show) {
                it.prop.set("from plugin")
            }
        """)
        buildFile """
            plugins { id("com.example.feature") }

            tasks.named("show") {
                doLast {
                    prop.set("other")
                }
            }
        """

        when:
        fails("show")

        then:
        failureCauseContains("It was last set by plugin 'com.example.feature'.")
    }

    def "reports the plugin that configured a property from configureEach"() {
        withProvenanceEnabled()
        pluginWithId("""
            project.tasks.withType(com.example.Show).configureEach {
                it.prop.set("from plugin")
            }
        """)
        buildFile """
            plugins { id("com.example.feature") }

            tasks.register("show", Show)

            tasks.named("show") {
                doLast {
                    prop.set("other")
                }
            }
        """

        when:
        fails("show")

        then:
        failureCauseContains("It was last set by plugin 'com.example.feature'.")
    }

    def "falls back to the implementation class for a plugin applied without an id"() {
        withProvenanceEnabled()
        buildFile """
            class FeaturePlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.tasks.register("show", Show) {
                        it.prop.set("from plugin")
                    }
                }
            }

            apply plugin: FeaturePlugin

            tasks.named("show") {
                doLast {
                    prop.set("other")
                }
            }
        """

        when:
        fails("show")

        then:
        failureCauseContains("It was last set by plugin class 'FeaturePlugin'.")
    }

    def "attribution is restored after a plugin applies another plugin"() {
        withProvenanceEnabled()
        pluginWithId("""
            project.tasks.register("show", com.example.Show) {
                it.prop.set("from inner plugin")
            }
        """)
        buildFile """
            class OuterPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.pluginManager.apply("com.example.feature")
                    project.tasks.register("showOuter", Show) {
                        it.prop.set("from outer plugin")
                    }
                }
            }

            apply plugin: OuterPlugin

            tasks.named("show") { doLast { prop.set("other") } }
            tasks.named("showOuter") { doLast { prop.set("other") } }
        """

        when:
        fails("show")

        then:
        failureCauseContains("It was last set by plugin 'com.example.feature'.")

        when:
        // the executer resets its arguments after each invocation
        withProvenanceEnabled()
        fails("showOuter")

        then:
        failureCauseContains("It was last set by plugin class 'OuterPlugin'.")
    }

    def "distinguishes build files in a multi-project build"() {
        withProvenanceEnabled()
        settingsFile """
            include(":lib")
            include(":app")
        """
        buildFile """
            subprojects { p -> p.tasks.register("show", Show) }

            // configuring another project from the root build file
            project(":app") {
                tasks.named("show") {
                    prop = "set from root"
                    doLast { prop.set("other") }
                }
            }
        """
        file("lib/build.gradle") << """
            tasks.named("show") {
                prop = "set from lib"
                doLast { prop.set("other") }
            }
        """
        // :app is configured entirely from the root build file
        file("app/build.gradle") << ""

        when:
        fails(":lib:show")

        then:
        failureCauseContains("The value for task ':lib:show' property 'prop' is final and cannot be changed any further. It was last set by build file 'lib${File.separatorChar}build.gradle'.")

        when:
        withProvenanceEnabled()
        fails(":app:show")

        then:
        // cross-project configuration: the root build file did it, not the app build file
        failureCauseContains("The value for task ':app:show' property 'prop' is final and cannot be changed any further. It was last set by build file 'build.gradle'.")
    }

    def "reports provenance for a script plugin"() {
        withProvenanceEnabled()
        file("other.gradle") << """
            tasks.register("show", com.example.Show) {
                prop = "from script plugin"
            }
        """
        buildFile """
            apply from: "other.gradle"

            tasks.named("show") {
                doLast {
                    prop.set("other")
                }
            }
        """

        when:
        fails("show")

        then:
        failureCauseContains("It was last set by script 'other.gradle'.")
    }

    def "reports provenance when a property has no value"() {
        withProvenanceEnabled()
        pluginWithId("""
            project.tasks.register("show", com.example.Show) {
                it.other.set(project.providers.gradleProperty("missingProperty"))
            }
        """)
        buildFile """
            plugins { id("com.example.feature") }

            tasks.named("show") {
                prop = "value"
                doLast {
                    println(other.get())
                }
            }
        """

        when:
        fails("show")

        then:
        failureCauseContains("This property was last set by plugin 'com.example.feature'.")
    }

    def "reports nothing when provenance is disabled"() {
        buildFile """
            tasks.register("show", Show) {
                prop = "value"
                doLast {
                    prop.set("other")
                }
            }
        """

        when:
        fails("show")

        then:
        failure.assertHasCause("The value for task ':show' property 'prop' is final and cannot be changed any further.")
        !failure.output.contains("It was last set by")
    }

    private void pluginWithId(String applyBody) {
        file("buildSrc/src/main/groovy/com/example/FeaturePlugin.groovy") << """
            package com.example

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class FeaturePlugin implements Plugin<Project> {
                void apply(Project project) {
                    $applyBody
                }
            }
        """
        file("buildSrc/src/main/resources/META-INF/gradle-plugins/com.example.feature.properties") << """
            implementation-class=com.example.FeaturePlugin
        """
    }
}
