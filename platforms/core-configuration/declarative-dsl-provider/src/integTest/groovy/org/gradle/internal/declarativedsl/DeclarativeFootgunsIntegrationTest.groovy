/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.declarativedsl

import org.gradle.api.internal.plugins.software.RegistersSoftwareTypes
import org.gradle.api.internal.plugins.software.SoftwareType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

final class DeclarativeFootgunsIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        // enable DCL support to have KTS accessors generated
        propertiesFile << "org.gradle.kotlin.dsl.dcl=true"
    }

    def "use an existing block at the wrong scope"() {
        given:
        file("build-logic/src/main/java/com/example/restricted/LibraryExtension.java") << """
            package com.example.restricted;

            import org.gradle.api.tasks.Nested;
            import org.gradle.declarative.dsl.model.annotations.Restricted;

            @Restricted
            public interface LibraryExtension {
                @Nested
                InnerExtension getInner();
            }
        """
        file("build-logic/src/main/java/com/example/restricted/InnerExtension.java") << """
            package com.example.restricted;

            import org.gradle.api.provider.Property;
            import org.gradle.declarative.dsl.model.annotations.Restricted;

            @Restricted
            public interface InnerExtension {
            }
        """
        file("build-logic/src/main/java/com/example/restricted/SoftwareTypeRegistrationPlugin.java") << defineSettingsPluginRegisteringSoftwareTypeProvidingPlugin()
        file("build-logic/src/main/java/com/example/restricted/RestrictedPlugin.java") << """
            package com.example.restricted;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.artifacts.DependencyScopeConfiguration;
            import ${SoftwareType.class.name};

            public abstract class RestrictedPlugin implements Plugin<Project> {
                @SoftwareType(name = "library", modelPublicType = LibraryExtension.class)
                public abstract LibraryExtension getRestricted();

                @Override
                public void apply(Project project) {}
            }
        """

        file("build-logic/build.gradle") << defineRestrictedPluginBuild()
        file("build.gradle.dcl") << """
            library {
                library { // Wrong scope
                }
            }
        """
        file("settings.gradle") << defineSettings()

        expect:
        fails("help")
        result.assertHasErrorOutput("Check failed.")
//        result.assertHasErrorOutput("Unknown expression: library() with args: {} at: ${file("build.gradle.dcl").path}:3") // After change in https://github.com/gradle/gradle/pull/32403
    }

    private String defineSettingsPluginRegisteringSoftwareTypeProvidingPlugin() {
        return """
            package com.example.restricted;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.initialization.Settings;
            import org.gradle.api.internal.SettingsInternal;
            import org.gradle.plugin.software.internal.SoftwareTypeRegistry;
            import ${RegistersSoftwareTypes.class.name};

            @RegistersSoftwareTypes({ RestrictedPlugin.class })
            abstract public class SoftwareTypeRegistrationPlugin implements Plugin<Settings> {
                @Override
                public void apply(Settings target) {}
            }
        """
    }

    private String defineSettings() {
        return """
            pluginManagement {
                includeBuild("build-logic")
            }

            plugins {
                id("com.example.restricted.ecosystem")
            }

            dependencyResolutionManagement {
                repositories {
                    mavenCentral()
                }
            }

            rootProject.name = 'example'
            enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
        """
    }

    private String defineRestrictedPluginBuild() {
        return """
            plugins {
                id('java-gradle-plugin')
            }

            ${mavenCentralRepository()}

            gradlePlugin {
                plugins {
                    create("restrictedPlugin") {
                        id = "com.example.restricted"
                        implementationClass = "com.example.restricted.RestrictedPlugin"
                    }
                    create("softwareTypeRegistrator") {
                        id = "com.example.restricted.ecosystem"
                        implementationClass = "com.example.restricted.SoftwareTypeRegistrationPlugin"
                    }
                }
            }
        """
    }
}
