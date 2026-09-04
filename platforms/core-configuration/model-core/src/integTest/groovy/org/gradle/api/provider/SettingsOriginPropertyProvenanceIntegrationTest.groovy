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

import static org.hamcrest.Matchers.containsString

/**
 * Settings-origin configuration of project-owned properties, not settings-owned property tracking.
 */
class SettingsOriginPropertyProvenanceIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        executer.withArgument("-Dorg.gradle.internal.property-provenance=true")
    }

    def "settings plugin origin survives #boundary configuring a project property"() {
        settingsPluginBuild(nested, isolated)

        when:
        fails(":app:settingsValue")

        then:
        failure.assertThatCause(containsString("""Failure trace to source:
    at task ':app:settingsValue' action [get()]
    at plugin 'com.example.settings-provenance' [explicit source]

Shadowed configuration:
    at plugin 'com.example.settings-provenance' [convention]"""))

        where:
        boundary                              | nested | isolated
        "beforeProject"                       | false  | false
        "nested task callback"                | true   | false
        "lifecycle.beforeProject"             | false  | true
        "lifecycle nested task callback"      | true   | true
    }

    def "project script replacement selects its own origin over the settings plugin convention"() {
        settingsPluginBuild(false)
        file("app/build.gradle.kts") << '''
            @Suppress("UNCHECKED_CAST")
            val value = extensions.extraProperties.get("fromSettings") as Property<String>
            value.set(providers.gradleProperty("missing-project-value"))
        '''

        when:
        fails(":app:settingsValue")

        then:
        failure.assertThatCause(containsString("""Failure trace to source:
    at task ':app:settingsValue' action [get()]
    at build file 'app/build.gradle.kts' [explicit source]

Shadowed configuration:
    at plugin 'com.example.settings-provenance' [convention]"""))
    }

    def "settings-owned properties remain untracked"() {
        settingsPluginBuild(false)
        file("settings.gradle.kts") << '''
            (extensions.getByName("settingsOnlyValue") as Property<*>).get()
        '''

        when:
        fails("help")

        then:
        failure.assertHasDescription("Cannot query the value of this property because it has no value available.")
        !failure.error.contains("Failure trace to source")
    }

    def "successful project configuration from a settings plugin is silent"() {
        settingsPluginBuild(true, true)

        when:
        succeeds(":app:settingsValue", "-Pmissing-settings-project-value=present")

        then:
        !output.contains("Failure trace to source")
        !output.contains("Trace limitations")
    }

    def "settings-origin project configuration leaves disabled diagnostics unchanged"() {
        settingsPluginBuild(true)
        executer.withArgument("-Dorg.gradle.internal.property-provenance=false")

        when:
        fails(":app:settingsValue")

        then:
        failure.assertHasCause("""Cannot query the value of this property because it has no value available.
The value of this property is derived from:
  - Gradle property 'missing-settings-project-value'""")
        !failure.error.contains("Failure trace to source")
    }

    private void settingsPluginBuild(boolean nested, boolean isolated = false) {
        file("settings.gradle.kts") << '''
            pluginManagement { includeBuild("build-logic") }
            plugins { id("com.example.settings-provenance") }
            rootProject.name = "settings-origin-example"
            include("app")
        '''
        file("build.gradle.kts") << ""
        file("app/build.gradle.kts") << ""
        file("build-logic/settings.gradle.kts") << 'rootProject.name = "build-logic"'
        file("build-logic/build.gradle.kts") << '''
            plugins { `java-gradle-plugin` }
            gradlePlugin {
                plugins {
                    create("settingsOrigin") {
                        id = "com.example.settings-provenance"
                        implementationClass = "example.SettingsOriginPlugin"
                    }
                }
            }
        '''
        def binding = 'value.set(project.getProviders().gradleProperty("missing-settings-project-value"));'
        file("build-logic/src/main/java/example/SettingsOriginPlugin.java") << """
            package example;

            import org.gradle.api.Plugin;
            import org.gradle.api.initialization.Settings;
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.api.provider.Property;
            import javax.inject.Inject;

            public class SettingsOriginPlugin implements Plugin<Settings> {
                private final ObjectFactory objects;

                @Inject
                public SettingsOriginPlugin(ObjectFactory objects) {
                    this.objects = objects;
                }

                public void apply(Settings settings) {
                    Property<String> settingsOnly = objects.property(String.class);
                    settingsOnly.set(settings.getProviders().gradleProperty("missing-settings-owned-value"));
                    settings.getExtensions().add("settingsOnlyValue", settingsOnly);

                    settings.getGradle().${isolated ? "getLifecycle()." : ""}beforeProject(project -> {
                        Property<String> value = project.getObjects().property(String.class);
                        value.convention("settings default");
                        project.getExtensions().getExtraProperties().set("fromSettings", value);
                        ${nested ? "" : binding}
                        project.getTasks().register("settingsValue", task -> {
                            ${nested ? binding : ""}
                            task.doLast(ignored -> value.get());
                        });
                    });
                }
            }
        """
    }
}
