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
    def 'can configure an extension using DependencyCollector in declarative DSL'() {
        given: "a plugin that creates a custom extension using a DependencyCollector"
        file("buildSrc/src/main/java/com/example/restricted/DependenciesExtension.java") << defineDependenciesExtension()
        file("buildSrc/src/main/java/com/example/restricted/LibraryExtension.java") << defineLibraryExtension()
        file("buildSrc/src/main/java/com/example/restricted/RestrictedPlugin.java") << """
            package com.example.restricted;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.artifacts.DependencyScopeConfiguration;

            public class RestrictedPlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    // no plugin application, must create configurations manually
                    DependencyScopeConfiguration api = project.getConfigurations().dependencyScope("api").get();
                    DependencyScopeConfiguration implementation = project.getConfigurations().dependencyScope("implementation").get();

                    // create and wire the custom dependencies extension's dependencies to these global configurations
                    LibraryExtension restricted = project.getExtensions().create("library", LibraryExtension.class);
                    api.fromDependencyCollector(restricted.getDependencies().getApi());
                    implementation.fromDependencyCollector(restricted.getDependencies().getImplementation());
                }
            }
        """
        file("buildSrc/build.gradle") << defineRestrictedPluginBuild()

        and: "a build script that adds dependencies using the custom extension"
        file("build.gradle.something") << defineDeclarativeDSLBuildScript()

        expect: "a dependency has been added to the api configuration"
        succeeds("dependencies", "--configuration", "api")
        outputContains("com.google.guava:guava:30.1.1-jre")

        and: "a dependency has been added to the implementation configuration"
        succeeds("dependencies", "--configuration", "implementation")
        outputContains("com.apache.commons:commons-lang3:3.12.0")
    }

    def 'can configure an extension using DependencyCollector in declarative DSL and build a java plugin'() {
        given: "a plugin that creates a custom extension using a DependencyCollector and applies the java library plugin"
        file("buildSrc/src/main/java/com/example/restricted/DependenciesExtension.java") << defineDependenciesExtension()
        file("buildSrc/src/main/java/com/example/restricted/LibraryExtension.java") << defineLibraryExtension()
        file("buildSrc/src/main/java/com/example/restricted/RestrictedPlugin.java") << """
            package com.example.restricted;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.plugins.JavaLibraryPlugin;

            public class RestrictedPlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    // api and implementation configurations created by plugin
                    project.getPluginManager().apply(JavaLibraryPlugin.class);

                    // inner dependencies extension automatically wired to configurations created by plugin
                    project.getExtensions().create("library", LibraryExtension.class);
                }
            }
        """
        file("buildSrc/build.gradle") << defineRestrictedPluginBuild()

        and: "a build script that adds dependencies using the custom extension, and defines a source file requiring the dependencies to compile"
        file("src/main/java/com/example/Lib.java") << defineExampleJavaClass()
        file("build.gradle.something") << defineDeclarativeDSLBuildScript()

        expect: "the library can be built successfully"
        succeeds("build")
        file("build/libs/${testDirectory.name}.jar").exists()
    }

    private String defineDependenciesExtension() {
        return """
            package com.example.restricted;

            import org.gradle.api.artifacts.dsl.DependencyCollector;
            import org.gradle.api.artifacts.dsl.GradleDependencies;
            import org.gradle.api.plugins.jvm.PlatformDependencyModifiers;
            import org.gradle.api.plugins.jvm.TestFixturesDependencyModifiers;
            import org.gradle.declarative.dsl.model.annotations.Restricted;

            @Restricted
            public interface DependenciesExtension extends PlatformDependencyModifiers, TestFixturesDependencyModifiers, GradleDependencies {
                DependencyCollector getApi();
                DependencyCollector getImplementation();
            }
        """
    }

    private String defineLibraryExtension() {
        return """
            package com.example.restricted;

            import org.gradle.api.Action;
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.declarative.dsl.model.annotations.Configuring;
            import org.gradle.declarative.dsl.model.annotations.Restricted;

            import javax.inject.Inject;

            @Restricted
            public abstract class LibraryExtension {
                private final DependenciesExtension dependencies;

                @Inject
                public LibraryExtension(ObjectFactory objectFactory) {
                    this.dependencies = objectFactory.newInstance(DependenciesExtension.class);
                }

                public DependenciesExtension getDependencies() {
                    return dependencies;
                }

                @Configuring
                public void dependencies(Action<? super DependenciesExtension> configure) {
                    configure.execute(dependencies);
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

    private String defineExampleJavaClass() {
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

    private String defineDeclarativeDSLBuildScript() {
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
}
