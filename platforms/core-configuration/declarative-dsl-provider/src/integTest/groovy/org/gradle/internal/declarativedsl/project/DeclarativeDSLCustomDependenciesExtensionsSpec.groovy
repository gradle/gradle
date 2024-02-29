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

package org.gradle.internal.declarativedsl.project

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class DeclarativeDSLCustomDependenciesExtensionsSpec extends AbstractIntegrationSpec {
    def 'can configure a custom plugin extension in declarative DSL with custom dependencies extension'() {
        given:
        file("buildSrc/build.gradle") << defineRestrictedPluginBuild()
        file("buildSrc/src/main/java/com/example/restricted/LibraryExtension.java") << defineLibraryExtension("com.example.restricted.DependenciesExtension")
        file("buildSrc/src/main/java/com/example/restricted/DependenciesExtension.java") << defineDependenciesExtension()
        file("buildSrc/src/main/java/com/example/restricted/RestrictedPlugin.java") << """
            package com.example.restricted;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;
            import org.gradle.api.plugins.JavaLibraryPlugin;

            public class RestrictedPlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    LibraryExtension restricted = project.getExtensions().create("library", LibraryExtension.class);
                    project.getConfigurations().resolvable("api");
                    project.getConfigurations().resolvable("implementation");
                }
            }
        """

        and:
        file("build.gradle.something") << defineBuildScript()
        file("src/main/java/com/example/Lib.java") << defineExampleJavaLibraryClass()

        expect:
        succeeds("tasks")
    }

    def 'can configure a custom plugin extension in declarative DSL and build a java plugin with custom dependencies extension'() {
        given:
        file("buildSrc/build.gradle") << defineRestrictedPluginBuild()
        file("buildSrc/src/main/java/com/example/restricted/LibraryExtension.java") << defineLibraryExtension("com.example.restricted.DependenciesExtension")
        file("buildSrc/src/main/java/com/example/restricted/DependenciesExtension.java") << defineDependenciesExtension()
        file("buildSrc/src/main/java/com/example/restricted/RestrictedPlugin.java") << """
            package com.example.restricted;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;
            import org.gradle.api.plugins.JavaLibraryPlugin;

            public class RestrictedPlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    project.getPluginManager().apply(JavaLibraryPlugin.class);
                    LibraryExtension restricted = project.getExtensions().create("library", LibraryExtension.class);\
                }
            }
        """

        and:
        file("build.gradle.something") << defineBuildScript()
        file("src/main/java/com/example/Lib.java") << defineExampleJavaLibraryClass()

        expect:
        succeeds("build")

        and:
        file("build/libs/${testDirectory.name}.jar").exists()
    }

    def 'can configure a custom plugin extension in declarative DSL with pre-defined, extracted dependencies extension'() {
        given:
        file("buildSrc/build.gradle") << defineRestrictedPluginBuild()
        file("buildSrc/src/main/java/com/example/restricted/LibraryExtension.java") << defineLibraryExtension()
        file("buildSrc/src/main/java/com/example/restricted/DependenciesExtension.java") << defineDependenciesExtension()
        file("buildSrc/src/main/java/com/example/restricted/RestrictedPlugin.java") << """
            package com.example.restricted;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;
            import org.gradle.api.plugins.JavaLibraryPlugin;

            public class RestrictedPlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    LibraryExtension restricted = project.getExtensions().create("library", LibraryExtension.class);
                    project.getConfigurations().resolvable("api");
                    project.getConfigurations().resolvable("implementation");
                }
            }
        """

        and:
        file("src/main/java/com/example/Lib.java") << defineExampleJavaLibraryClass()
        file("build.gradle.something") << defineBuildScript()

        expect:
        succeeds("tasks")
    }

    def 'can configure a custom plugin extension in declarative DSL and build a java plugin with pre-defined, extracted dependencies extension'() {
        given:
        file("buildSrc/build.gradle") << defineRestrictedPluginBuild()
        file("buildSrc/src/main/java/com/example/restricted/LibraryExtension.java") << defineLibraryExtension()
        file("buildSrc/src/main/java/com/example/restricted/DependenciesExtension.java") << defineDependenciesExtension()
        file("buildSrc/src/main/java/com/example/restricted/RestrictedPlugin.java") << """
            package com.example.restricted;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;
            import org.gradle.api.plugins.JavaLibraryPlugin;

            public class RestrictedPlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    project.getPluginManager().apply(JavaLibraryPlugin.class);
                    LibraryExtension restricted = project.getExtensions().create("library", LibraryExtension.class);\
                }
            }
        """

        and:
        file("src/main/java/com/example/Lib.java") << defineExampleJavaLibraryClass()
        file("build.gradle.something") << defineBuildScript()

        expect:
        succeeds("build")

        and:
        file("build/libs/${testDirectory.name}.jar").exists()
    }

    private String defineLibraryExtension(String dependenciesType = RestrictedLibraryDependencies.name) {
        return """
            package com.example.restricted;

            import org.gradle.api.Action;
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;
            import org.gradle.declarative.dsl.model.annotations.Adding;
            import org.gradle.declarative.dsl.model.annotations.Configuring;
            import org.gradle.declarative.dsl.model.annotations.Restricted;
            import ${dependenciesType};

            import javax.inject.Inject;

            @Restricted
            public abstract class LibraryExtension {
                private final ${dependenciesType} dependencies;

                @Inject
                public LibraryExtension(ObjectFactory objectFactory) {
                    this.dependencies = objectFactory.newInstance(${dependenciesType}.class);
                }

                @Configuring
                public void dependencies(Action<? super ${dependenciesType}> configure) {
                    configure.execute(dependencies);
                }
            }
        """
    }

    private String defineExampleJavaLibraryClass() {
        return """
            package com.example;

            import com.google.common.collect.ImmutableSet;
            import org.apache.commons.lang3.StringUtils;

            public class Lib {
                public static ImmutableSet<String> getPeople() {
                    return ImmutableSet.of(capitalize("adam johnson"), capitalize("bob smith"), capitalize("carl jones"));
                }

                private static String capitalize(String input) {
                    return StringUtils.capitalize(input);
                }
            }
        """
    }

    private String defineDependenciesExtension() {
        return """
            package com.example.restricted;

            import org.gradle.api.artifacts.dsl.DependencyCollector;
            import org.gradle.api.artifacts.dsl.GradleDependencies;
            import org.gradle.api.plugins.jvm.PlatformDependencyModifiers;
            import org.gradle.api.plugins.jvm.TestFixturesDependencyModifiers;
            import org.gradle.declarative.dsl.model.annotations.Adding;
            import org.gradle.declarative.dsl.model.annotations.Restricted;
            import org.gradle.declarative.dsl.model.annotations.Configuring;
            import org.gradle.api.Action;
            import org.gradle.api.artifacts.ExternalModuleDependency;

            @Restricted
            public interface DependenciesExtension extends PlatformDependencyModifiers, TestFixturesDependencyModifiers, GradleDependencies {
                DependencyCollector getApi();
                DependencyCollector getImplementation();

                @Adding
                default void api(String dependencyNotation) {
                    api(dependencyNotation, null);
                }

                default void api(String dependencyNotation, Action<? super ExternalModuleDependency> configure) {
                    getApi().add(dependencyNotation, configure);
                }

                @Adding
                default void implementation(String dependencyNotation) {
                    implementation(dependencyNotation, null);
                }

                default void implementation(String dependencyNotation, Action<? super ExternalModuleDependency> configure) {
                    getImplementation().add(dependencyNotation, configure);
                }
            }
        """
    }

    private String defineBuildScript() {
        return """
            plugins {
                id("com.example.restricted")
            }

            library {
                dependencies {
                    api("com.google.guava:guava:30.1.1-jre")
                    implementation("com.apache.commons:commons-lang3:3.12.0")
                }
            }
        """
    }

    private String defineRestrictedPluginBuild() {
        return """
            plugins {
                id('java-gradle-plugin')
            }
            gradlePlugin {
                plugins {
                    create("restrictedPlugin") {
                        id = "com.example.restricted"
                        implementationClass = "com.example.restricted.RestrictedPlugin"
                    }
                }
            }
        """
    }
}
