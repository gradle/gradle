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

package org.gradle.internal.declarativedsl.settings


import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class DeclarativeDslProjectSettingsIntegrationSpec extends AbstractIntegrationSpec {

    def "can interpret the settings file with the declarative DSL"() {
        given:
        file("settings.gradle.dcl") << """
            rootProject.name = "test-value"
            include(":a")
            include(":b")

            dependencyResolutionManagement {
                repositories {
                    mavenCentral()
                    google()
                }
            }
            pluginManagement {
                includeBuild("pluginIncluded")
                repositories {
                    mavenCentral()
                    google()
                }
            }
        """
        buildFile << "println('name = ' + rootProject.name)"
        file("a/build.gradle") << ""
        file("b/build.gradle") << ""
        file("pluginIncluded/settings.gradle.dcl") << "rootProject.name = \"pluginIncluded\""

        expect:
        succeeds(":help", ":a:help", ":b:help")
        outputContains("name = test-value")
    }

    def 'schema is written during settings interpretation'() {
        given:
        file("settings.gradle.dcl") << """
            rootProject.name = "test"
        """

        when:
        run(":help")

        then:
        def schemaFile = file(".gradle/declarative-schema/settings.dcl.schema")
        schemaFile.isFile() && schemaFile.text != ""
    }

    def 'reports #kind errors in settings'() {
        given:
        file("settings.gradle.dcl") << """
            rootProject.name = "test"
            $code
        """

        when:
        def failure = fails(":help")

        then:
        failure.assertHasErrorOutput(expectedMessage)

        where:
        kind               | code                  | expectedMessage
        "syntax"           | "..."                 | "2:13: parsing error: Expecting an element"
        "language feature" | "@A dependencies { }" | "2:13: unsupported language feature: AnnotationUsage"
        "semantic"         | "x = 1"               | "2:13: unresolved reference 'x'"
    }

    def 'can apply settings plugins'() {
        given:
        file("included-settings-plugin/build.gradle") << """
            plugins {
                id('java-gradle-plugin')
            }
            gradlePlugin {
                plugins {
                    create("settingsPlugin") {
                        id = "com.example.restricted.settings"
                        implementationClass = "com.example.restricted.RestrictedSettingsPlugin"
                    }
                }
            }
        """

        file("included-settings-plugin/src/main/java/com/example/restricted/Extension.java") << """
            package com.example.restricted;

            import org.gradle.declarative.dsl.model.annotations.Restricted;
            import org.gradle.api.provider.Property;

            import javax.inject.Inject;

            @Restricted
            public abstract class Extension {
                @Restricted
                public abstract Property<String> getId();
            }
        """

        file("included-settings-plugin/src/main/java/com/example/restricted/RestrictedSettingsPlugin.java") << """
            package com.example.restricted;

            import org.gradle.api.Plugin;
            import org.gradle.api.initialization.Settings;

            public class RestrictedSettingsPlugin implements Plugin<Settings> {
                @Override
                public void apply(Settings target) {
                    Extension restricted = target.getExtensions().create("restricted", Extension.class);
                    target.getGradle().settingsEvaluated(settings -> {
                        System.out.println("id = " + restricted.getId().get());
                    });
                }
            }
        """

        file("settings.gradle.dcl") << """
            pluginManagement {
                includeBuild("included-settings-plugin")
            }

            plugins {
                id("com.example.restricted.settings")
            }

            restricted {
                id = "test"
            }
        """

        expect:
        succeeds("help")
        outputContains("id = test")
    }
}
