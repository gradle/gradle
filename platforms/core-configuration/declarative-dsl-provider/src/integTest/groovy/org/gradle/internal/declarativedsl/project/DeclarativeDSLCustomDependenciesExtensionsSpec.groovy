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

import org.gradle.api.DefaultTask
import org.gradle.api.JavaVersion
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.internal.plugins.BindsProjectType
import org.gradle.api.internal.plugins.Definition
import org.gradle.api.internal.plugins.BuildModel
import org.gradle.api.internal.plugins.ProjectTypeBinding
import org.gradle.api.internal.plugins.ProjectTypeBindingBuilder
import org.gradle.api.internal.plugins.software.RegistersSoftwareTypes
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.versions.KotlinGradlePluginVersions
import org.jetbrains.kotlin.config.JvmTarget

final class DeclarativeDSLCustomDependenciesExtensionsSpec extends AbstractIntegrationSpec {
    def 'can configure an extension using DependencyCollector in declarative DSL'() {
        given: "a plugin that creates a custom extension using a DependencyCollector"
        file("build-logic/src/main/java/com/example/restricted/DependenciesExtension.java") << defineDependenciesExtension()
        file("build-logic/src/main/java/com/example/restricted/LibraryExtension.java") << defineLibraryExtension()
        file("build-logic/src/main/java/com/example/restricted/SoftwareTypeRegistrationPlugin.java") << defineSettingsPluginRegisteringSoftwareTypeProvidingPlugin()
        file("build-logic/src/main/java/com/example/restricted/ResolveTask.java") << defineResolveTask()
        file("build-logic/src/main/java/com/example/restricted/RestrictedPlugin.java") << defineRestrictedPluginWithResolveTasks()
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
            import org.gradle.api.artifacts.DependencyScopeConfiguration;
            import ${Definition.class.name};
            import ${BuildModel.class.name};

            import javax.inject.Inject;

            @Restricted
            public abstract class LibraryExtension implements ${Definition.class.simpleName}<LibraryExtension.Model> {
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

                ${defineModelClass()}
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
            import org.gradle.api.artifacts.DependencyScopeConfiguration;
            import ${BindsProjectType.class.name};
            import ${ProjectTypeBinding.class.name};
            import ${ProjectTypeBindingBuilder.class.name};

            @${BindsProjectType.class.simpleName}(RestrictedPlugin.Binding.class)
            public abstract class RestrictedPlugin implements Plugin<Project> {
                static class Binding implements ${ProjectTypeBinding.class.simpleName} {
                    public void bind(${ProjectTypeBindingBuilder.class.simpleName} builder) {
                        builder.bindProjectType("library",  LibraryExtension.class, (context, definition, model) -> {
                            // no plugin application, must create configurations manually
                            DependencyScopeConfiguration conf = context.getProject().getConfigurations().dependencyScope("conf").get();

                            // Add the dependency scopes to the model
                            model.setApi(conf);

                            // create and wire the custom dependencies extension's dependencies to these global configurations
                            model.getApi().fromDependencyCollector(definition.getSub().getConf());
                        })
                        .withUnsafeDefinition();
                    }
                }

                @Override
                public void apply(Project project) { }
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

            import org.gradle.api.NamedDomainObjectProvider;
            import org.gradle.api.logging.Logger;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.artifacts.DependencyScopeConfiguration;
            import org.gradle.api.artifacts.ResolvableConfiguration;
            import ${BindsProjectType.class.name};
            import ${ProjectTypeBinding.class.name};
            import ${ProjectTypeBindingBuilder.class.name};

            @${BindsProjectType.class.simpleName}(RestrictedPlugin.Binding.class)
            public abstract class RestrictedPlugin implements Plugin<Project> {
                static class Binding implements ${ProjectTypeBinding.class.simpleName} {
                    public void bind(${ProjectTypeBindingBuilder.class.simpleName} builder) {
                        builder.bindProjectType("library",  LibraryExtension.class, (context, definition, model) -> {
                            // no plugin application, must create configurations manually
                            DependencyScopeConfiguration myConf = context.getProject().getConfigurations().dependencyScope("myConf").get();
                            DependencyScopeConfiguration myOtherConf = context.getProject().getConfigurations().dependencyScope("myOtherConf").get();

                            // Add the dependency scopes to the model
                            model.setApi(myConf);
                            model.setImplementation(myOtherConf);

                            // create and wire the custom dependencies extension's dependencies to these global configurations
                            model.getApi().fromDependencyCollector(definition.getDependencies().getSomething());
                            model.getImplementation().fromDependencyCollector(definition.getDependencies().getSomethingElse());
                        })
                        .withUnsafeDefinition();
                    }
                }

                public void apply(Project project) { }
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
        file("build-logic/src/main/java/com/example/restricted/ResolveTask.java") << defineResolveTask()
        file("build-logic/src/main/java/com/example/restricted/RestrictedPlugin.java") << defineRestrictedPluginWithResolveTasks()
        file("build-logic/build.gradle") << defineRestrictedPluginBuild()

        and: "a build script that adds dependencies using the custom extension"
        file("build.gradle.dcl") << defineDeclarativeDSLBuildScript()
        file("settings.gradle") << defineSettings()

        expect: "the build fails"
        fails("dependencies", "--configuration", "api")
        failure.assertHasCause("Failed to interpret the declarative DSL file '${testDirectory.file("build.gradle.dcl").path}'")
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
                val api: DependencyCollector
                val implementation: DependencyCollector
            }
        """
        file("build-logic/src/main/kotlin/com/example/restricted/LibraryExtension.kt") << defineLibraryExtensionKotlin()
        file("build-logic/src/main/java/com/example/restricted/SoftwareTypeRegistrationPlugin.java") << defineSettingsPluginRegisteringSoftwareTypeProvidingPlugin()
        file("build-logic/src/main/kotlin/com/example/restricted/RestrictedPlugin.kt") << defineKotlinRestrictedPlugin()
        file("build-logic/build.gradle") << defineRestrictedPluginBuild(true)

        and: "a build script that adds dependencies using the custom extension"
        file("build.gradle.dcl") << """
            library {
                dependencies {
                    api("com.google.guava:guava:30.1.1-jre")
                    implementation("org.apache.commons:commons-lang3:3.12.0")
                }
            }
        """
        file("settings.gradle") << defineSettings()

        expect: "a dependency has been added to the something configuration"
        succeeds("dependencies", "--configuration", "api")
        outputContains("com.google.guava:guava:30.1.1-jre")

        and: "a dependency has been added to the somethingElse configuration"
        succeeds("dependencies", "--configuration", "implementation")
        outputContains("org.apache.commons:commons-lang3:3.12.0")
    }

    def 'can configure an extension using DependencyCollector in declarative DSL using project() from the Dependencies class to add dependencies'() {
        given: "a plugin that creates a custom extension using a DependencyCollector"
        file("build-logic/src/main/java/com/example/restricted/DependenciesExtension.java") << defineDependenciesExtension()
        file("build-logic/src/main/java/com/example/restricted/LibraryExtension.java") << defineLibraryExtension()
        file("build-logic/src/main/java/com/example/restricted/SoftwareTypeRegistrationPlugin.java") << defineSettingsPluginRegisteringSoftwareTypeProvidingPlugin()
        file("build-logic/src/main/java/com/example/restricted/ResolveTask.java") << defineResolveTask()
        file("build-logic/src/main/java/com/example/restricted/RestrictedPlugin.java") << defineRestrictedPluginWithResolveTasks()
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

    def "can configure a platform using DependencyCollector in declarative DSL from a platform project"() {
        given: "a plugin that creates a custom extension using a DependencyCollector and PlatformDependencyModifiers"
        file("build-logic/src/main/java/com/example/restricted/DependenciesExtension.java") << defineDependenciesExtensionWithPlatformModifiers()
        file("build-logic/src/main/java/com/example/restricted/LibraryExtension.java") << defineLibraryExtension()
        file("build-logic/src/main/java/com/example/restricted/SoftwareTypeRegistrationPlugin.java") << defineSettingsPluginRegisteringSoftwareTypeProvidingPlugin()
        file("build-logic/src/main/java/com/example/restricted/ResolveTask.java") << defineResolveTask()
        file("build-logic/src/main/java/com/example/restricted/RestrictedPlugin.java") << defineRestrictedPluginWithResolveTasks()
        file("build-logic/build.gradle") << defineRestrictedPluginBuild()

        and: "a project that defines a platform"
        file("platform/build.gradle") << """
            plugins {
                id 'java-platform'
            }

            dependencies {
                constraints {
                    api("org.apache.commons:commons-lang3:3.8.1")
                }
            }
        """

        and: "a lib project that uses the platform"
        file("lib/build.gradle.dcl") << """
            library {
                dependencies {
                    implementation(platform(project(":platform")))
                    implementation("org.apache.commons:commons-lang3")
                }
            }
        """

        and: "a root project including both of these projects"
        file("settings.gradle") << defineSettings() + 'include("lib", "platform")'

        expect:
        succeeds(":lib:resolveImplementation")
        outputContains("commons-lang3-3.8.1.jar")
    }

    def "can configure a platform using DependencyCollector in declarative DSL by converting a BOM"() {
        given: "a plugin that creates a custom extension using a DependencyCollector and PlatformDependencyModifiers"
        file("build-logic/src/main/java/com/example/restricted/DependenciesExtension.java") << defineDependenciesExtensionWithPlatformModifiers()
        file("build-logic/src/main/java/com/example/restricted/LibraryExtension.java") << defineLibraryExtension()
        file("build-logic/src/main/java/com/example/restricted/SoftwareTypeRegistrationPlugin.java") << defineSettingsPluginRegisteringSoftwareTypeProvidingPlugin()
        file("build-logic/src/main/java/com/example/restricted/ResolveTask.java") << defineResolveTask()
        file("build-logic/src/main/java/com/example/restricted/RestrictedPlugin.java") << defineRestrictedPluginWithResolveTasks()
        file("build-logic/build.gradle") << defineRestrictedPluginBuild()

        and: "a lib project that uses a BOM as a platform"
        file("lib/build.gradle.dcl") << """
            library {
                dependencies {
                    implementation(platform("io.micronaut:micronaut-bom:3.10.4"))
                    implementation("io.micronaut:micronaut-core")
                }
            }
        """

        file("settings.gradle") << defineSettings() + 'include("lib")'

        expect:
        succeeds(":lib:resolveImplementation")
        outputContains("micronaut-core-3.10.4.jar")
    }

    def "calling the platform method with an invalid type (#invalidType) produces a sensible error message"() {
        given: "a plugin that creates a custom extension using a DependencyCollector and PlatformDependencyModifiers"
        file("build-logic/src/main/java/com/example/restricted/DependenciesExtension.java") << defineDependenciesExtensionWithPlatformModifiers()
        file("build-logic/src/main/java/com/example/restricted/LibraryExtension.java") << defineLibraryExtension()
        file("build-logic/src/main/java/com/example/restricted/SoftwareTypeRegistrationPlugin.java") << defineSettingsPluginRegisteringSoftwareTypeProvidingPlugin()
        file("build-logic/src/main/java/com/example/restricted/ResolveTask.java") << defineResolveTask()
        file("build-logic/src/main/java/com/example/restricted/RestrictedPlugin.java") << defineRestrictedPluginWithResolveTasks()
        file("build-logic/build.gradle") << defineRestrictedPluginBuild()

        and: "a lib project that misuses platform()"
        file("lib/build.gradle.dcl") << """
            library {
                dependencies {
                    implementation(platform($invalidType))
                }
            }
        """

        file("settings.gradle") << defineSettings() + 'include("lib")'

        expect:
        fails(":lib:resolveImplementation")
        errorOutput.contains("Failed to interpret the declarative DSL file '${file("lib/build.gradle.dcl").path}':")
        errorOutput.contains("unresolved function call signature for 'platform'")

        where:
        invalidType << ["layout", "1", "true", "null"]
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

    private String defineDependenciesExtensionWithPlatformModifiers() {
        return """
            package com.example.restricted;

            import org.gradle.api.artifacts.dsl.DependencyCollector;
            import org.gradle.api.artifacts.dsl.Dependencies;
            import org.gradle.api.plugins.jvm.PlatformDependencyModifiers;
            import org.gradle.declarative.dsl.model.annotations.Restricted;

            @Restricted
            public interface DependenciesExtension extends Dependencies, PlatformDependencyModifiers {
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
            import org.gradle.api.artifacts.DependencyScopeConfiguration;
            import ${Definition.class.name};
            import ${BuildModel.class.name};

            import javax.inject.Inject;

            @Restricted
            public abstract class LibraryExtension implements ${Definition.class.simpleName}<LibraryExtension.Model> {
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

                ${defineModelClass()}
            }
        """
    }

    private String defineModelClass() {
        return """
                public static abstract class Model implements ${BuildModel.class.simpleName} {
                    private DependencyScopeConfiguration api;
                    private DependencyScopeConfiguration implementation;

                    DependencyScopeConfiguration getApi() {
                        return api;
                    }

                    void setApi(DependencyScopeConfiguration api) {
                        this.api = api;
                    }

                    DependencyScopeConfiguration getImplementation() {
                        return implementation;
                    }

                    void setImplementation(DependencyScopeConfiguration implementation) {
                        this.implementation = implementation;
                    }
                }
            """
    }

    private String defineResolveTask() {
        return """
            package com.example.restricted;

            import ${DefaultTask.name};
            import ${ConfigurableFileCollection.name};
            import ${InputFiles.name};
            import ${PathSensitive.name};
            import ${PathSensitivity.name};
            import ${TaskAction.name};

            public abstract class ResolveTask extends DefaultTask {
                @InputFiles
                @PathSensitive(PathSensitivity.NONE)
                public abstract ConfigurableFileCollection getResolvedFiles();

                @TaskAction
                public void action() {
                    getResolvedFiles().getFiles().forEach(file -> System.out.println(file.getName()));
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
            import org.gradle.api.internal.plugins.Definition
            import org.gradle.api.internal.plugins.BuildModel

            import javax.inject.Inject

            @Restricted
            abstract class LibraryExtension : Definition<LibraryExtension.Model> {
                val dependencies: DependenciesExtension = objectFactory.newInstance(DependenciesExtension::class.java)

                @Configuring
                fun dependencies(configure: Action<DependenciesExtension>) {
                    configure.execute(dependencies)
                }

                @get:Inject
                abstract val objectFactory: ObjectFactory

                abstract class Model : BuildModel {
                    var api: org.gradle.api.artifacts.DependencyScopeConfiguration? = null
                    var implementation: org.gradle.api.artifacts.DependencyScopeConfiguration? = null
                }
            }
        """
    }

    private String defineRestrictedPluginWithResolveTasks() {
        return """
            package com.example.restricted;

            import org.gradle.api.NamedDomainObjectProvider;
            import org.gradle.api.logging.Logger;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.artifacts.DependencyScopeConfiguration;
            import org.gradle.api.artifacts.ResolvableConfiguration;
            import ${BindsProjectType.class.name};
            import ${ProjectTypeBinding.class.name};
            import ${ProjectTypeBindingBuilder.class.name};

            @${BindsProjectType.class.simpleName}(RestrictedPlugin.Binding.class)
            public abstract class RestrictedPlugin implements Plugin<Project> {
                static class Binding implements ${ProjectTypeBinding.class.simpleName} {
                    public void bind(${ProjectTypeBindingBuilder.class.simpleName} builder) {
                        builder.bindProjectType("library",  LibraryExtension.class, (context, definition, model) -> {
                            // no plugin application, must create configurations manually
                            DependencyScopeConfiguration api = context.getProject().getConfigurations().dependencyScope("api").get();
                            DependencyScopeConfiguration implementation = context.getProject().getConfigurations().dependencyScope("implementation").get();

                            // Add the dependency scopes to the model
                            model.setApi(api);
                            model.setImplementation(implementation);

                            // create and wire the custom dependencies extension's dependencies to these global configurations
                            model.getApi().fromDependencyCollector(definition.getDependencies().getApi());
                            model.getImplementation().fromDependencyCollector(definition.getDependencies().getImplementation());

                            // and create and wire a configuration that can resolve that one
                            NamedDomainObjectProvider<ResolvableConfiguration> resolveApi = context.getProject().getConfigurations().resolvable("resolveApi");
                            resolveApi.get().extendsFrom(api);

                            context.getProject().getTasks().register("resolveApi", ResolveTask.class, task -> {
                                task.getResolvedFiles().from(resolveApi);
                            });

                            NamedDomainObjectProvider<ResolvableConfiguration> resolveImplementation = context.getProject().getConfigurations().resolvable("resolveImplementation");
                            resolveImplementation.get().extendsFrom(implementation);

                            context.getProject().getTasks().register("resolveImplementation", ResolveTask.class, task -> {
                                task.getResolvedFiles().from(resolveImplementation);
                            });
                        })
                        .withUnsafeDefinition();
                    }
                }

                @Override
                public void apply(Project project) { }
            }
        """
    }

    private String defineKotlinRestrictedPlugin() {
        return """
            package com.example.restricted

            import org.gradle.api.logging.Logger
            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import org.gradle.api.artifacts.DependencyScopeConfiguration
            import ${BindsProjectType.class.name}
            import ${ProjectTypeBinding.class.name}
            import ${ProjectTypeBindingBuilder.class.name}

            @${BindsProjectType.class.simpleName}(RestrictedPlugin.Binding::class)
            class RestrictedPlugin : Plugin<Project> {
                class Binding : ${ProjectTypeBinding.class.simpleName} {
                    override fun bind(builder: ${ProjectTypeBindingBuilder.class.simpleName}) {
                        builder.bindProjectType("library",  LibraryExtension::class.java) { context, definition, model ->
                            // no plugin application, must create configurations manually
                            val api: DependencyScopeConfiguration = context.getProject().getConfigurations().dependencyScope("api").get()
                            val implementation: DependencyScopeConfiguration = context.getProject().getConfigurations().dependencyScope("implementation").get()

                            // Add the dependency scopes to the model
                            model.api = api
                            model.implementation = implementation

                            // create and wire the custom dependencies extension's dependencies to these global configurations
                            model.api!!.fromDependencyCollector(definition.dependencies.api)
                            model.implementation!!.fromDependencyCollector(definition.dependencies.implementation)
                        }
                        .withUnsafeDefinition()
                    }
                }

                override fun apply(project: Project) = Unit
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
