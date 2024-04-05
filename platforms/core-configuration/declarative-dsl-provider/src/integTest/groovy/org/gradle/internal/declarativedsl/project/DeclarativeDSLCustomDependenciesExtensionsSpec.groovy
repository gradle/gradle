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
import org.gradle.integtests.fixtures.versions.KotlinGradlePluginVersions

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
        file("settings.gradle") << defineSettings()

        expect: "a dependency has been added to the api configuration"
        succeeds("dependencies", "--configuration", "api")
        outputContains("com.google.guava:guava:30.1.1-jre")

        and: "a dependency has been added to the implementation configuration"
        succeeds("dependencies", "--configuration", "implementation")
        outputContains("org.apache.commons:commons-lang3:3.12.0")
    }

    def 'can configure an extension using DependencyCollector in declarative DSL with a getter name NOT associated with an expected configuration name'() {
        given: "a plugin that creates a custom extension using a DependencyCollector"
        file("buildSrc/src/main/java/com/example/restricted/DependenciesExtension.java") << """
            package com.example.restricted;

            import org.gradle.api.artifacts.dsl.DependencyCollector;
            import org.gradle.api.artifacts.dsl.Dependencies;
            import org.gradle.declarative.dsl.model.annotations.Restricted;

            @Restricted
            public interface DependenciesExtension extends Dependencies {
                DependencyCollector getSomething();
                DependencyCollector getSomethingElse();
            }
        """
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
                    DependencyScopeConfiguration myConf = project.getConfigurations().dependencyScope("myConf").get();
                    DependencyScopeConfiguration myOtherConf = project.getConfigurations().dependencyScope("myOtherConf").get();

                    // create and wire the custom dependencies extension's dependencies to these global configurations
                    LibraryExtension restricted = project.getExtensions().create("library", LibraryExtension.class);
                    myConf.fromDependencyCollector(restricted.getDependencies().getSomething());
                    myOtherConf.fromDependencyCollector(restricted.getDependencies().getSomethingElse());
                }
            }
        """
        file("buildSrc/build.gradle") << defineRestrictedPluginBuild()

        and: "a build script that adds dependencies using the custom extension"
        file("build.gradle.something") << """
            plugins {
                id("com.example.restricted")
            }

            library {
                dependencies {
                    something("com.google.guava:guava:30.1.1-jre")
                    somethingElse("org.apache.commons:commons-lang3:3.12.0")
                }
            }
        """
        file("settings.gradle") << defineSettings()

        expect: "a dependency has been added to the something configuration"
        succeeds("dependencies", "--configuration", "myConf")
        outputContains("com.google.guava:guava:30.1.1-jre")

        and: "a dependency has been added to the somethingElse configuration"
        succeeds("dependencies", "--configuration", "myOtherConf")
        outputContains("org.apache.commons:commons-lang3:3.12.0")
    }

    def 'can NOT configure an extension using DependencyCollector in declarative DSL if extension does NOT extend Dependencies'() {
        given: "a plugin that creates a custom extension using a DependencyCollector on an extension that does NOT extend Dependencies"
        file("buildSrc/src/main/java/com/example/restricted/DependenciesExtension.java") << defineDependenciesExtension(false)
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
        file("settings.gradle") << defineSettings()

        expect: "the build fails"
        fails("dependencies", "--configuration", "api")
        failure.assertHasCause("Failed to interpret the declarative DSL file '${testDirectory.file("build.gradle.something").path}'")
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

                    // create and wire the custom dependencies extension's dependencies to the global configurations created by the plugin
                    LibraryExtension restricted = project.getExtensions().create("library", LibraryExtension.class);
                    project.getConfigurations().getByName("api").fromDependencyCollector(restricted.getDependencies().getApi());
                    project.getConfigurations().getByName("implementation").fromDependencyCollector(restricted.getDependencies().getImplementation());
                }
            }
        """
        file("buildSrc/build.gradle") << defineRestrictedPluginBuild()

        and: "a build script that adds dependencies using the custom extension, and defines a source file requiring the dependencies to compile"
        file("src/main/java/com/example/Lib.java") << defineExampleJavaClass()
        file("build.gradle.something") << defineDeclarativeDSLBuildScript()
        file("settings.gradle") << defineSettings()

        expect: "the library can be built successfully"
        succeeds("build")
        file("build/libs/example.jar").exists()
    }

    def 'can configure an extension using DependencyCollector in declarative DSL that uses Kotlin properties for the getters'() {
        given: "a plugin that creates a custom extension using a DependencyCollector"
        file("buildSrc/src/main/kotlin/com/example/restricted/DependenciesExtension.kt") << """
            package com.example.restricted

            import org.gradle.api.artifacts.dsl.DependencyCollector
            import org.gradle.api.artifacts.dsl.Dependencies
            import org.gradle.declarative.dsl.model.annotations.Restricted

            @Restricted
            interface DependenciesExtension : Dependencies {
                val something: DependencyCollector
                val somethingElse: DependencyCollector
            }
        """
        file("buildSrc/src/main/kotlin/com/example/restricted/LibraryExtension.kt") << defineLibraryExtensionKotlin()
        file("buildSrc/src/main/kotlin/com/example/restricted/RestrictedPlugin.kt") << """
            package com.example.restricted

            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import org.gradle.api.artifacts.DependencyScopeConfiguration

            class RestrictedPlugin : Plugin<Project> {
                override fun apply(project: Project) {
                    // no plugin application, must create configurations manually
                    val myConf = project.getConfigurations().dependencyScope("myConf").get()
                    val myOtherConf = project.getConfigurations().dependencyScope("myOtherConf").get()

                    // create and wire the custom dependencies extension's dependencies to these global configurations
                    val restricted = project.getExtensions().create("library", LibraryExtension::class.java)
                    myConf.fromDependencyCollector(restricted.dependencies.something)
                    myOtherConf.fromDependencyCollector(restricted.dependencies.somethingElse)
                }
            }
        """
        file("buildSrc/build.gradle") << defineRestrictedPluginBuild(true)

        and: "a build script that adds dependencies using the custom extension"
        file("build.gradle.something") << """
            plugins {
                id("com.example.restricted")
            }

            library {
                dependencies {
                    something("com.google.guava:guava:30.1.1-jre")
                    somethingElse("org.apache.commons:commons-lang3:3.12.0")
                }
            }
        """
        file("settings.gradle") << defineSettings()

        expect: "a dependency has been added to the something configuration"
        succeeds("dependencies", "--configuration", "myConf")
        outputContains("com.google.guava:guava:30.1.1-jre")

        and: "a dependency has been added to the somethingElse configuration"
        succeeds("dependencies", "--configuration", "myOtherConf")
        outputContains("org.apache.commons:commons-lang3:3.12.0")
    }

    private String defineDependenciesExtension(boolean extendDependencies = true) {
        return """
            package com.example.restricted;

            import org.gradle.api.artifacts.dsl.DependencyCollector;
            import org.gradle.api.artifacts.dsl.Dependencies;
            import org.gradle.declarative.dsl.model.annotations.Restricted;

            @Restricted
            public interface DependenciesExtension ${(extendDependencies) ? "extends Dependencies" : "" } {
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

    private String defineLibraryExtensionKotlin() {
        // language=kotlin
        return """
            package com.example.restricted

            import org.gradle.api.Action
            import org.gradle.api.model.ObjectFactory
            import org.gradle.declarative.dsl.model.annotations.Configuring
            import org.gradle.declarative.dsl.model.annotations.Restricted

            import javax.inject.Inject

            @Restricted
            abstract class LibraryExtension @Inject constructor(objectFactory: ObjectFactory) {
                val dependencies: DependenciesExtension = objectFactory.newInstance(DependenciesExtension::class.java)

                @Configuring
                fun dependencies(configure: Action<DependenciesExtension>) {
                    configure.execute(dependencies)
                }
            }
        """
    }

    private String defineRestrictedPluginBuild(boolean kotlin = false) {
        return """
            plugins {
                id('java-gradle-plugin')
                ${ if (kotlin) { 'id("org.jetbrains.kotlin.jvm") version("' + new KotlinGradlePluginVersions().latestStableOrRC + '")' } else { '' } }
            }

            ${mavenCentralRepository()}

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
                    implementation("org.apache.commons:commons-lang3:3.12.0")
                }
            }
        """
    }

    private String defineSettings() {
        return """
            dependencyResolutionManagement {
                repositories {
                    mavenCentral()
                }
            }

            rootProject.name = 'example'
        """
    }
}
