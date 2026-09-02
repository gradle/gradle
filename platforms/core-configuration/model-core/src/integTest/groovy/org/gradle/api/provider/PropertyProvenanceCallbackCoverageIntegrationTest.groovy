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
 * Records which callback registration points carry a plugin's attribution to a property mutation, and which
 * do not.
 * <p>
 * Attribution is carried by {@code UserCodeApplicationContext.Application.reapplyLater(...)}, applied where
 * Gradle stores user code. It is therefore a property of the <em>registration boundary</em>, not of the kind
 * of user code: a Groovy closure, a Java {@code Action} and a Kotlin lambda registered through the same API
 * all behave the same way. The cases that lose attribution are the ones where Gradle never sees the
 * registration.
 */
class PropertyProvenanceCallbackCoverageIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        executer.withArgument("-Dorg.gradle.internal.property-provenance=true")
        file("buildSrc/build.gradle") << """
            plugins { id("groovy-gradle-plugin") }
        """
        file("buildSrc/src/main/groovy/com/example/Show.groovy") << """
            package com.example

            import org.gradle.api.DefaultTask
            import org.gradle.api.provider.Property
            import org.gradle.api.tasks.Internal

            abstract class Show extends DefaultTask {
                @Internal abstract Property<String> getProp()
            }
        """
        file("buildSrc/src/main/resources/META-INF/gradle-plugins/com.example.feature.properties") << """
            implementation-class=com.example.FeaturePlugin
        """
    }

    def "attribution survives #registrationPoint"() {
        featurePlugin("""
            project.tasks.register("show", Show)
            $registration
        """)
        buildFile """
            import com.example.Show

            plugins { id("com.example.feature") }

            gradle.taskGraph.whenReady {
                println("PROVENANCE: " + tasks.getByName("show").prop.explicitMutation)
            }
            tasks.register("run")
        """

        when:
        succeeds("run")

        then:
        outputContains("PROVENANCE: set by plugin 'com.example.feature'")

        where:
        registrationPoint            | registration
        "direct mutation"            | 'project.tasks.named("show").get().prop.set("x")'
        "tasks.register action"      | 'project.tasks.named("show").configure { it.prop.set("x") }'
        "tasks.named configure"      | 'project.tasks.named("show") { it.prop.set("x") }'
        "withType configureEach"     | 'project.tasks.withType(Show).configureEach { it.prop.set("x") }'
        "project.afterEvaluate"      | 'project.afterEvaluate { p -> p.tasks.named("show").get().prop.set("x") }'
        "gradle.projectsEvaluated"   | 'project.gradle.projectsEvaluated { project.tasks.getByName("show").prop.set("x") }'
        "pluginManager.withPlugin"   | 'project.pluginManager.withPlugin("base") { project.tasks.getByName("show").prop.set("x") }; project.pluginManager.apply("base")'
        "taskGraph.whenReady"        | 'project.gradle.taskGraph.whenReady { project.tasks.getByName("show").prop.set("x") }'
        "configurations.configureEach" | 'project.configurations.configureEach { project.tasks.getByName("show").prop.set("x") }; project.configurations.create("probe")'
    }

    def "attribution is lost when a plugin mutates a property from its own thread"() {
        featurePlugin("""
            project.tasks.register("show", Show)
            def executor = java.util.concurrent.Executors.newSingleThreadExecutor()
            executor.submit({ project.tasks.getByName("show").prop.set("x") } as Runnable).get()
            executor.shutdown()
        """)
        buildFile """
            import com.example.Show

            plugins { id("com.example.feature") }

            gradle.taskGraph.whenReady {
                println("PROVENANCE: " + tasks.getByName("show").prop.explicitMutation)
            }
            tasks.register("run")
        """

        when:
        succeeds("run")

        then:
        // Gradle did not create this thread, so there is no causal context to restore.
        outputContains("PROVENANCE: set by unknown code")
    }

    def "a plugin's own store of user code is attributed to whoever runs it"() {
        file("buildSrc/src/main/groovy/com/example/Holder.groovy") << """
            package com.example

            import org.gradle.api.Action

            // Gradle never sees this registration, so it cannot decorate it.
            class Holder {
                static List<Action<Object>> actions = []
                static void store(Action<Object> a) { actions << a }
                static void runAll() { actions.each { it.execute(new Object()) } }
            }
        """
        featurePlugin("""
            project.tasks.register("show", Show)
            Holder.store({ project.tasks.getByName("show").prop.set("x") })
        """)
        buildFile """
            import com.example.Show
            import com.example.Holder

            plugins { id("com.example.feature") }

            Holder.runAll()

            gradle.taskGraph.whenReady {
                println("PROVENANCE: " + tasks.getByName("show").prop.explicitMutation)
            }
            tasks.register("run")
        """

        when:
        succeeds("run")

        then:
        // Attributed to the build script that ran the action, not the plugin that wrote it.
        outputContains("PROVENANCE: set by build file 'build.gradle'")
    }

    def "a mutation performed inside a provider transform is attributed to whoever evaluates it"() {
        featurePlugin("""
            project.tasks.register("show", Show)
            project.tasks.register("trigger", Show) {
                it.prop.set(project.provider {
                    project.tasks.getByName("show").prop.set("x")
                    "x"
                })
            }
        """)
        buildFile """
            import com.example.Show

            plugins { id("com.example.feature") }

            gradle.taskGraph.whenReady {
                tasks.getByName("trigger").prop.get()
                println("PROVENANCE: " + tasks.getByName("show").prop.explicitMutation)
            }
            tasks.register("run")
        """

        when:
        succeeds("run")

        then:
        // A transform carries no mutation attribution of its own, so the evaluator's context is used.
        outputContains("PROVENANCE: set by build file 'build.gradle'")
    }

    private void featurePlugin(String applyBody) {
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
    }
}
