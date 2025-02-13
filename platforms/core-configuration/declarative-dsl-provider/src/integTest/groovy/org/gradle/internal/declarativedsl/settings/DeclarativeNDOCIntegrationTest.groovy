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

package org.gradle.internal.declarativedsl.settings


import org.gradle.api.internal.plugins.software.RegistersSoftwareTypes
import org.gradle.api.internal.plugins.software.SoftwareType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Tests that the {@link org.gradle.api.NamedDomainObjectContainer NamedDomainObjectContainer} works
 * as expected in declarative projects.
 */
final class DeclarativeNDOCIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        // enable DCL support to have KTS accessors generated
        propertiesFile << "org.gradle.kotlin.dsl.dcl=true"
    }

    def "can configure a NDOC in a declarative DSL"() {
        given:
        file("build-logic/src/main/java/com/example/restricted/LibraryExtension.java") << """
            package com.example.restricted;

            import org.gradle.api.NamedDomainObjectContainer;
            import org.gradle.declarative.dsl.model.annotations.Restricted;

            @Restricted
            public interface LibraryExtension {
                NamedDomainObjectContainer<Database> getDatabases();
            }
        """
        file("build-logic/src/main/java/com/example/restricted/Database.java") << """
            package com.example.restricted;

            import org.gradle.api.Named;
            import org.gradle.api.provider.Property;
            import org.gradle.declarative.dsl.model.annotations.Restricted;

            @Restricted
            public interface Database extends Named {
                @Restricted
                Property<String> getDesc();
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
                public void apply(Project project) {
                    getRestricted().getDatabases().forEach(db -> {
                        // No DBs exist at this point...which makes it had to configure them
                        project.getLogger().warn("During project evaulation DB: " + db.getName() + " found.");
                    });

                    project.afterEvaluate(p -> {
                        getRestricted().getDatabases().forEach(db -> {
                            // DBs only exist after project evaluation, which may be too late for plugins that are not also applied after evaulation to read their values
                            p.getLogger().warn("After project evaulation DB: " + db.getName() + " found.");
                        });
                    });
                }
            }
        """

        file("build-logic/build.gradle") << defineRestrictedPluginBuild()
        file("build.gradle.dcl") << """
            library {
                databases {
                    database("db1") {
                        desc = "test"
                    }
                }
            }
        """
        file("settings.gradle") << defineSettings()

        expect:
        succeeds("help")
        outputContains("Before project evaulation DB: db1 found.") // Shouldn't this be present?
        outputContains("After project evaulation DB: db1 found.")
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
