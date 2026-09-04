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

import static org.hamcrest.Matchers.matchesRegex

/**
 * The first project-scoped property-provenance slice, including the four failure call-site paths that
 * motivated its failure-only caller capture.
 */
class PropertyProvenanceIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        executer.withArgument("-Dorg.gradle.internal.property-provenance=true")
    }

    def "Kotlin DSL get failure has an exact operation line and convention source line"() {
        file("settings.gradle.kts") << ""
        file("build.gradle.kts") << '''
            val value = objects.property<String>()
            value.convention(providers.gradleProperty("not-defined"))
            value.get()
        '''

        when:
        fails("help")

        then:
        failure.assertThatDescription(matchesRegex("(?s).*Failure trace to source:\\R" +
            "    at build file 'build\\.gradle\\.kts' \\(build\\.gradle\\.kts:\\d+\\) \\[get\\(\\)\\]\\R" +
            "    at build file 'build\\.gradle\\.kts' \\(build\\.gradle\\.kts:\\d+\\) \\[convention\\].*"))
    }

    def "task action get failure is attributed to the executing task"() {
        kotlinTaskBuild('''
            value.convention("default")
            value.set(providers.gradleProperty("not-defined"))
            doLast { value.get() }
        ''')

        when:
        fails("show")

        then:
        failure.assertThatCause(matchesRegex("(?s).*Failure trace to source:\\R" +
            "    at task ':show' action \\(build\\.gradle\\.kts:\\d+\\) \\[get\\(\\)\\]\\R" +
            "    at build file 'build\\.gradle\\.kts' \\(build\\.gradle\\.kts:\\d+\\) \\[explicit source\\]\\R\\R" +
            "Shadowed configuration:\\R" +
            "    at build file 'build\\.gradle\\.kts' \\(build\\.gradle\\.kts:\\d+\\) \\[convention\\].*"))
    }

    def "indirect mapped Provider evaluation retains the property source and outer get site"() {
        kotlinTaskBuild('''
            value.set(providers.gradleProperty("not-defined"))
            val derived = value.map { it.uppercase() }
            doLast { derived.get() }
        ''')

        when:
        fails("show")

        then:
        failure.assertThatCause(matchesRegex("(?s).*Failure trace to source:\\R" +
            "    at task ':show' action \\(build\\.gradle\\.kts:\\d+\\) \\[get\\(\\)\\]\\R" +
            "    at build file 'build\\.gradle\\.kts' \\(build\\.gradle\\.kts:\\d+\\) \\[explicit source\\].*"))
    }

    def "finalized property set reports the task action call site without retaining the attempt"() {
        file("settings.gradle.kts") << ""
        file("build.gradle.kts") << '''
            abstract class Show : DefaultTask() {
                @get:Input
                abstract val value: Property<String>
            }

            tasks.register<Show>("show") {
                value.set("configured")
                doLast { value.set("too late") }
            }
        '''

        when:
        fails("show")

        then:
        failure.assertThatCause(matchesRegex("(?s).*The value for task ':show' property 'value' is final and cannot be changed any further\\.\\R" +
            "Failure trace to source:\\R" +
            "    at task ':show' action \\(build\\.gradle\\.kts:\\d+\\) \\[set\\(\\)\\]\\R" +
            "    at build file 'build\\.gradle\\.kts' \\(build\\.gradle\\.kts:\\d+\\) \\[explicit source\\].*"))
    }

    def "Java plugin reports direct configuration as the selected source"() {
        javaPluginBuild()

        when:
        fails("directPluginValue")

        then:
        failure.assertThatCause(pluginTrace("directPluginValue"))
    }

    def "Java plugin attribution survives a deferred task configuration callback"() {
        javaPluginBuild()

        when:
        fails("deferredPluginValue")

        then:
        failure.assertThatCause(pluginTrace("deferredPluginValue"))
    }

    def "Groovy DSL trace deliberately omits line-level call sites"() {
        file("build.gradle") << '''
            def value = objects.property(String)
            value.convention(providers.gradleProperty("not-defined"))
            value.get()
        '''

        when:
        fails("help")

        then:
        failureCauseContains("at build file 'build.gradle' [get()]")
        failureCauseContains("at build file 'build.gradle' [convention]")
        !failure.output.contains("(build.gradle:")
    }

    def "disabled provenance leaves the existing missing-value message unchanged"() {
        executer.withArgument("-Dorg.gradle.internal.property-provenance=false")
        file("settings.gradle.kts") << ""
        file("build.gradle.kts") << '''
            objects.property<String>().get()
        '''

        when:
        fails("help")

        then:
        failure.assertHasDescription("Cannot query the value of this property because it has no value available.")
        !failure.output.contains("Failure trace to source")
    }

    private void kotlinTaskBuild(String configuration) {
        file("settings.gradle.kts") << ""
        file("build.gradle.kts") << """
            abstract class Show : DefaultTask() {
                @get:Internal
                abstract val value: Property<String>
            }

            tasks.register<Show>("show") {
                $configuration
            }
        """
    }

    private void javaPluginBuild() {
        file("settings.gradle.kts") << ""
        file("buildSrc/settings.gradle.kts") << "rootProject.name = \"build-logic\""
        file("buildSrc/build.gradle.kts") << '''
            plugins {
                `java-gradle-plugin`
            }

            gradlePlugin {
                plugins {
                    create("propertyProvenance") {
                        id = "com.example.property-provenance"
                        implementationClass = "com.example.PropertyProvenancePlugin"
                    }
                }
            }
        '''
        file("buildSrc/src/main/java/com/example/PropertyProvenancePlugin.java") << '''
            package com.example;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.provider.Property;

            public final class PropertyProvenancePlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    Property<String> direct = missingProperty(project);
                    project.getTasks().register("directPluginValue", task ->
                        task.doLast(ignored -> direct.get())
                    );

                    project.getTasks().register("deferredPluginValue", task -> {
                        Property<String> deferred = missingProperty(project);
                        task.doLast(ignored -> deferred.get());
                    });
                }

                private static Property<String> missingProperty(Project project) {
                    Property<String> value = project.getObjects().property(String.class);
                    value.convention(project.getProviders().gradleProperty("missing-convention"));
                    value.set(project.getProviders().gradleProperty("missing-explicit"));
                    return value;
                }
            }
        '''
        file("build.gradle.kts") << '''
            plugins {
                id("com.example.property-provenance")
            }
        '''
    }

    private static def pluginTrace(String taskName) {
        matchesRegex("(?s).*Failure trace to source:\\R" +
            "    at task ':${taskName}' action \\(PropertyProvenancePlugin\\.java:\\d+\\) \\[get\\(\\)\\]\\R" +
            "    at plugin 'com\\.example\\.property-provenance' \\(PropertyProvenancePlugin\\.java:\\d+\\) \\[explicit source\\]\\R\\R" +
            "Shadowed configuration:\\R" +
            "    at plugin 'com\\.example\\.property-provenance' \\(PropertyProvenancePlugin\\.java:\\d+\\) \\[convention\\].*")
    }
}
