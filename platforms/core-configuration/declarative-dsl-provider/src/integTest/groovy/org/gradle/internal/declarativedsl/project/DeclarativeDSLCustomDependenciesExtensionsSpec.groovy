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

import org.gradle.api.JavaVersion
import org.gradle.api.internal.plugins.software.RegistersSoftwareTypes
import org.gradle.api.internal.plugins.software.SoftwareType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.versions.KotlinGradlePluginVersions
import org.jetbrains.kotlin.config.JvmTarget

class DeclarativeDSLCustomDependenciesExtensionsSpec extends AbstractIntegrationSpec {
    def 'can configure an extension using DependencyCollector in declarative DSL'() {
        given: "a plugin that creates a custom extension using a DependencyCollector"
        file("build-logic/src/main/java/com/example/restricted/DependenciesExtension.java") << defineDependenciesExtension()
        file("build-logic/src/main/java/com/example/restricted/LibraryExtension.java") << defineLibraryExtension()
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
                    // no plugin application, must create configurations manually
                    DependencyScopeConfiguration api = project.getConfigurations().dependencyScope("api").get();
                    DependencyScopeConfiguration implementation = project.getConfigurations().dependencyScope("implementation").get();

                    // create and wire the custom dependencies extension's dependencies to these global configurations
                    api.fromDependencyCollector(getRestricted().getDependencies().getApi());
                    implementation.fromDependencyCollector(getRestricted().getDependencies().getImplementation());
                }
            }
        """
        file("build-logic/build.gradle") << defineRestrictedPluginBuild()

        and: "a build script that adds dependencies using the custom extension"
        file("build.gradle.dcl") << defineDeclarativeDSLBuildScript()
        file("settings.gradle") << defineSettings(typeSafeProjectAccessors)

        expect: "a dependency has been added to the api configuration"
        succeeds("dependencies", "--configuration", "api")
        outputContains("com.google.guava:guava:30.1.1-jre")

        and: "a dependency has been added to the implementation configuration"
        succeeds("dependencies", "--configuration", "implementation")
        outputContains("org.apache.commons:commons-lang3:3.12.0")

        where:
        typeSafeProjectAccessors << [true, false]
    }

    def 'can configure an extension using DependencyCollector in declarative DSL with @Restricted methods available on supertype'() {
        given:
        file("build-logic/src/main/java/com/example/restricted/LibraryExtension.java") << """
            package com.example.restricted;

            import org.gradle.api.Action;
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.declarative.dsl.model.annotations.Configuring;
            import org.gradle.declarative.dsl.model.annotations.Restricted;

            import javax.inject.Inject;

            @Restricted
            public abstract class LibraryExtension {
                private final SubDependencies sub;

                @Inject
                public LibraryExtension(ObjectFactory objectFactory) {
                    this.sub = objectFactory.newInstance(SubDependencies.class);
                }

                public SubDependencies getSub() {
                    return sub;
                }

                @Configuring
                public void sub(Action<? super SubDependencies> configure) {
                    configure.execute(getSub());
                }
            }
        """
        file("build-logic/src/main/java/com/example/restricted/BaseDependencies.java") << """
            package com.example.restricted;

            import org.gradle.api.artifacts.dsl.Dependencies;
            import org.gradle.declarative.dsl.model.annotations.Restricted;

            @Restricted
            public interface BaseDependencies extends Dependencies {
                @Restricted
                default String baseMethod(String arg) {
                    System.out.println(arg);
                    return arg;
                }
            }
        """
        file("build-logic/src/main/java/com/example/restricted/SubDependencies.java") << """
            package com.example.restricted;

            import org.gradle.declarative.dsl.model.annotations.Restricted;
            import org.gradle.api.artifacts.dsl.DependencyCollector;

            @Restricted
            public interface SubDependencies extends BaseDependencies {
                @Restricted
                default String subMethod(String arg) {
                    System.out.println(arg);
                    return arg;
                }

                DependencyCollector getConf();
            }
        """
        file("build-logic/src/main/java/com/example/restricted/SoftwareTypeRegistrationPlugin.java") << defineSettingsPluginRegisteringSoftwareTypeProvidingPlugin()
        file("build-logic/src/main/java/com/example/restricted/RestrictedPlugin.java") << """
            package com.example.restricted;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import ${SoftwareType.class.name};

            public abstract class RestrictedPlugin implements Plugin<Project> {
                @SoftwareType(name = "library", modelPublicType = LibraryExtension.class)
                public abstract LibraryExtension getLibrary();

                @Override
                public void apply(Project target) { }
            }
        """
        file("build-logic/build.gradle") << defineRestrictedPluginBuild()

        file("build.gradle.dcl") << """
            library {
                sub {
                    conf(baseMethod("base:name:1.0"))
                    conf(subMethod("sub:name:1.0"))
                }
            }
        """
        file("settings.gradle") << defineSettings(typeSafeProjectAccessors)

        expect:
        succeeds("build")
        outputContains("base:name:1.0")
        outputContains("sub:name:1.0")

        where:
        typeSafeProjectAccessors << [true, false]
    }

    def 'can configure an extension using DependencyCollector in declarative DSL with a getter name NOT associated with an expected configuration name'() {
        given: "a plugin that creates a custom extension using a DependencyCollector"
        file("build-logic/src/main/java/com/example/restricted/DependenciesExtension.java") << """
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
        file("build-logic/src/main/java/com/example/restricted/LibraryExtension.java") << defineLibraryExtension()
        file("build-logic/src/main/java/com/example/restricted/SoftwareTypeRegistrationPlugin.java") << defineSettingsPluginRegisteringSoftwareTypeProvidingPlugin()
        file("build-logic/src/main/java/com/example/restricted/RestrictedPlugin.java") << """
            package com.example.restricted;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.artifacts.DependencyScopeConfiguration;
            import ${SoftwareType.class.name};

            public abstract class RestrictedPlugin implements Plugin<Project> {
                @SoftwareType(name = "library", modelPublicType = LibraryExtension.class)
                public abstract LibraryExtension getLibrary();

                @Override
                public void apply(Project project) {
                    // no plugin application, must create configurations manually
                    DependencyScopeConfiguration myConf = project.getConfigurations().dependencyScope("myConf").get();
                    DependencyScopeConfiguration myOtherConf = project.getConfigurations().dependencyScope("myOtherConf").get();

                    // create and wire the custom dependencies extension's dependencies to these global configurations
                    myConf.fromDependencyCollector(getLibrary().getDependencies().getSomething());
                    myOtherConf.fromDependencyCollector(getLibrary().getDependencies().getSomethingElse());
                }
            }
        """
        file("build-logic/build.gradle") << defineRestrictedPluginBuild()

        and: "a build script that adds dependencies using the custom extension"
        file("build.gradle.dcl") << """
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
        file("build-logic/src/main/java/com/example/restricted/DependenciesExtension.java") << defineDependenciesExtension(false)
        file("build-logic/src/main/java/com/example/restricted/LibraryExtension.java") << defineLibraryExtension()
        file("build-logic/src/main/java/com/example/restricted/SoftwareTypeRegistrationPlugin.java") << defineSettingsPluginRegisteringSoftwareTypeProvidingPlugin()
        file("build-logic/src/main/java/com/example/restricted/RestrictedPlugin.java") << """
            package com.example.restricted;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.artifacts.DependencyScopeConfiguration;
            import ${SoftwareType.class.name};

            public abstract class RestrictedPlugin implements Plugin<Project> {
                @SoftwareType(name = "library", modelPublicType = LibraryExtension.class)
                public abstract LibraryExtension getLibrary();

                @Override
                public void apply(Project project) {
                    // no plugin application, must create configurations manually
                    DependencyScopeConfiguration api = project.getConfigurations().dependencyScope("api").get();
                    DependencyScopeConfiguration implementation = project.getConfigurations().dependencyScope("implementation").get();

                    // create and wire the custom dependencies extension's dependencies to these global configurations
                    api.fromDependencyCollector(getLibrary().getDependencies().getApi());
                    implementation.fromDependencyCollector(getLibrary().getDependencies().getImplementation());
                }
            }
        """
        file("build-logic/build.gradle") << defineRestrictedPluginBuild()

        and: "a build script that adds dependencies using the custom extension"
        file("build.gradle.dcl") << defineDeclarativeDSLBuildScript()
        file("settings.gradle") << defineSettings()

        expect: "the build fails"
        fails("dependencies", "--configuration", "api")
        failure.assertHasCause("Failed to interpret the declarative DSL file '${testDirectory.file("build.gradle.dcl").path}'")
    }

    def 'can configure an extension using DependencyCollector in declarative DSL and build a java plugin'() {
        given: "a plugin that creates a custom extension using a DependencyCollector and applies the java library plugin"
        file("build-logic/src/main/java/com/example/restricted/DependenciesExtension.java") << defineDependenciesExtension()
        file("build-logic/src/main/java/com/example/restricted/LibraryExtension.java") << defineLibraryExtension()
        file("build-logic/src/main/java/com/example/restricted/SoftwareTypeRegistrationPlugin.java") << defineSettingsPluginRegisteringSoftwareTypeProvidingPlugin()
        file("build-logic/src/main/java/com/example/restricted/RestrictedPlugin.java") << """
            package com.example.restricted;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.plugins.JavaLibraryPlugin;
            import ${SoftwareType.class.name};

            public abstract class RestrictedPlugin implements Plugin<Project> {
                @SoftwareType(name = "library", modelPublicType = LibraryExtension.class)
                public abstract LibraryExtension getLibrary();

                @Override
                public void apply(Project project) {
                    // api and implementation configurations created by plugin
                    project.getPluginManager().apply(JavaLibraryPlugin.class);

                    // create and wire the custom dependencies extension's dependencies to the global configurations created by the plugin
                    project.getConfigurations().getByName("api").fromDependencyCollector(getLibrary().getDependencies().getApi());
                    project.getConfigurations().getByName("implementation").fromDependencyCollector(getLibrary().getDependencies().getImplementation());
                }
            }
        """
        file("build-logic/build.gradle") << defineRestrictedPluginBuild()

        and: "a build script that adds dependencies using the custom extension, and defines a source file requiring the dependencies to compile"
        file("src/main/java/com/example/Lib.java") << defineExampleJavaClass()
        file("build.gradle.dcl") << defineDeclarativeDSLBuildScript()
        file("settings.gradle") << defineSettings()

        expect: "the library can be built successfully"
        succeeds("build")
        file("build/libs/example.jar").exists()
    }

    def 'can configure an extension using DependencyCollector in declarative DSL that uses Kotlin properties for the getters'() {
        given: "a plugin that creates a custom extension using a DependencyCollector"
        file("build-logic/src/main/kotlin/com/example/restricted/DependenciesExtension.kt") << """
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
        file("build-logic/src/main/kotlin/com/example/restricted/LibraryExtension.kt") << defineLibraryExtensionKotlin()
        file("build-logic/src/main/java/com/example/restricted/SoftwareTypeRegistrationPlugin.java") << defineSettingsPluginRegisteringSoftwareTypeProvidingPlugin()
        file("build-logic/src/main/kotlin/com/example/restricted/RestrictedPlugin.kt") << """
            package com.example.restricted

            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import org.gradle.api.artifacts.DependencyScopeConfiguration
            import ${SoftwareType.class.name}

            abstract class RestrictedPlugin : Plugin<Project> {
                @get:SoftwareType(name = "library", modelPublicType = LibraryExtension::class)
                abstract val restricted: LibraryExtension

                override fun apply(project: Project) {
                    // no plugin application, must create configurations manually
                    val myConf = project.getConfigurations().dependencyScope("myConf").get()
                    val myOtherConf = project.getConfigurations().dependencyScope("myOtherConf").get()

                    // create and wire the custom dependencies extension's dependencies to these global configurations
                    myConf.fromDependencyCollector(restricted.dependencies.something)
                    myOtherConf.fromDependencyCollector(restricted.dependencies.somethingElse)
                }
            }
        """
        file("build-logic/build.gradle") << defineRestrictedPluginBuild(true)

        and: "a build script that adds dependencies using the custom extension"
        file("build.gradle.dcl") << """
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

    def 'can configure an extension using DependencyCollector in declarative DSL using project() from the Dependencies class to add dependencies'() {
        given: "a plugin that creates a custom extension using a DependencyCollector"
        file("build-logic/src/main/java/com/example/restricted/DependenciesExtension.java") << defineDependenciesExtension()
        file("build-logic/src/main/java/com/example/restricted/LibraryExtension.java") << defineLibraryExtension()
        file("build-logic/src/main/java/com/example/restricted/SoftwareTypeRegistrationPlugin.java") << defineSettingsPluginRegisteringSoftwareTypeProvidingPlugin()
        file("build-logic/src/main/java/com/example/restricted/RestrictedPlugin.java") << """
            package com.example.restricted;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.artifacts.DependencyScopeConfiguration;
            import ${SoftwareType.class.name};

            public abstract class RestrictedPlugin implements Plugin<Project> {
                @SoftwareType(name = "library", modelPublicType = LibraryExtension.class)
                public abstract LibraryExtension getLibrary();

                @Override
                public void apply(Project project) {
                    // no plugin application, must create configurations manually
                    DependencyScopeConfiguration api = project.getConfigurations().dependencyScope("api").get();
                    DependencyScopeConfiguration implementation = project.getConfigurations().dependencyScope("implementation").get();

                    // create and wire the custom dependencies extension's dependencies to these global configurations
                    api.fromDependencyCollector(getLibrary().getDependencies().getApi());
                    implementation.fromDependencyCollector(getLibrary().getDependencies().getImplementation());
                }
            }
        """
        file("build-logic/build.gradle") << defineRestrictedPluginBuild()

        and: "a producer build that defines a required class"
        file("producer/src/main/java/com/example/Producer.java") << defineExampleProducerJavaClass()
        file("producer/build.gradle.dcl") << defineDeclarativeDSLProducerBuildScript()

        and: "a consumer build that requires the class in the producer project"
        file("consumer/src/main/java/com/example/Consumer.java") << defineExampleConsumerJavaClass()
        file("consumer/build.gradle.dcl") << defineDeclarativeDSLConsumerBuildScript()

        and: "a project including both the producer and consumer projects"
        file("settings.gradle") << defineSettings() + 'include("consumer", "producer")'

        expect: "the project dependency has been added to the api configuration"
        succeeds(":consumer:dependencies", "--configuration", "api")
        outputContains("\\--- project producer (n)")

        and: "the producer project can be built successfully"
        succeeds(":producer:build")
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
            public void apply(Settings target) {
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

            ${if (kotlin) {
                def majorJavaVersion = JavaVersion.current().majorVersion
                def jvmTarget = JvmTarget.fromString(majorJavaVersion)

                if (jvmTarget == null) {
                    if (JavaVersion.current() == JavaVersion.VERSION_1_8) {
                        jvmTarget = JvmTarget.JVM_1_8
                    } else {
                        jvmTarget = JvmTarget.entries.last()
                    }
                }
                def lastSupportedVersion = jvmTarget.description

                """
                java {
                    sourceCompatibility = "${lastSupportedVersion}"
                    targetCompatibility = "${lastSupportedVersion}"
                }
                tasks.compileKotlin {
                    kotlinOptions.jvmTarget = "${lastSupportedVersion}"
                }
                """
            } else {
                ''
            }}

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

    private String defineExampleProducerJavaClass() {
        return """
            package com.example;

            public class Producer {
                private final String name;

                public Producer(String name) {
                    this.name = name;
                }

                public String getName() {
                    return name;
                }
            }
        """
    }

    private String defineExampleConsumerJavaClass() {
        return """
            package com.example;

            public class Consumer {
                private final Producer producer;

                public Consumer(String name) {
                    producer = new Producer(name);
                }

                public Producer getProducer() {
                    return producer;
                }
            }
        """
    }

    private String defineDeclarativeDSLBuildScript() {
        return """
            library {
                dependencies {
                    api("com.google.guava:guava:30.1.1-jre")
                    implementation("org.apache.commons:commons-lang3:3.12.0")
                }
            }
        """
    }

    private String defineDeclarativeDSLProducerBuildScript() {
        return """
            library {}
        """
    }

    private String defineDeclarativeDSLConsumerBuildScript() {
        return """
            library {
                dependencies {
                    api(project(":producer"))
                }
            }
        """
    }

    private String defineSettings(boolean typeSafeProjectAccessors = false) {
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
            ${ if (typeSafeProjectAccessors) { 'enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")' }  }
        """
    }
}
