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

package org.gradle.internal.declarativedsl

import org.gradle.api.internal.plugins.BindsProjectType
import org.gradle.api.internal.plugins.BuildModel
import org.gradle.api.internal.plugins.Definition
import org.gradle.api.internal.plugins.ProjectTypeBinding
import org.gradle.api.internal.plugins.ProjectTypeBindingBuilder
import org.gradle.api.internal.plugins.software.RegistersSoftwareTypes
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.junit.Before
import org.junit.Test

import static org.hamcrest.Matchers.containsString

class ErrorHandlingOnReflectiveCallsSpec extends AbstractKotlinIntegrationTest {

    @Before
    void clearDefaultSettings() {
        if (!file("settings.gradle.kts").delete()) {
            throw new RuntimeException("Failed to delete default settings script")
        }
    }

    @Test
    void 'can disambiguate between methods based on parameters'() {
        given:

        file("build-logic/build.gradle.kts") << defineBuildLogic([
            "id(\"java-gradle-plugin\")",
            "`kotlin-dsl`"
        ])

        file("build-logic/src/main/kotlin/com/example/restricted/Extension.kt") << """
            package com.example.restricted;

            import org.gradle.api.provider.Property
            import org.gradle.declarative.dsl.model.annotations.Restricted
            import ${BuildModel.class.name}
            import ${Definition.class.name}

            @Restricted
            abstract class Extension : ${Definition.class.simpleName}<Extension.Model> {

                @get:Restricted
                abstract val prop: Property<String>

                @Restricted
                fun print(data: Int): String {
                    throw RuntimeException("Boom Int")
                }

                @Restricted
                fun print(data: String): String {
                    throw RuntimeException("Boom String")
                }

                interface Model : ${BuildModel.class.simpleName} {
                }
            }
        """

        file("build-logic/src/main/java/com/example/restricted/SoftwareTypeRegistrationPlugin.java") <<
            defineSettingsPluginRegisteringSoftwareTypeProvidingPlugin()

        file("build-logic/src/main/java/com/example/restricted/RestrictedPlugin.java") << defineProjectPlugin()

        file("settings.gradle.dcl") << defineSettingsLogic()

        file("build.gradle.dcl") << """
            restricted {
                prop = print(1)
            }
        """

        when:
        def failure = buildAndFail(":help")

        then:
        failure.assertThatCause(containsString("Boom Int"))
    }

    @Test
    void 'can disambiguate between annotated and non-annotated methods'() {
        given:

        file("build-logic/build.gradle.kts") << defineBuildLogic([
            "id(\"java-gradle-plugin\")",
            "`kotlin-dsl`"
        ])

        file("build-logic/src/main/kotlin/com/example/restricted/Extension.kt") << """
            package com.example.restricted;

            import org.gradle.api.Action
            import org.gradle.api.model.ObjectFactory
            import org.gradle.api.provider.Property
            import org.gradle.declarative.dsl.model.annotations.Configuring
            import org.gradle.declarative.dsl.model.annotations.Restricted
            import javax.inject.Inject
            import ${BuildModel.class.name}
            import ${Definition.class.name};


            @Restricted
            abstract class Extension @Inject constructor(private val objects: ObjectFactory) : ${Definition.class.simpleName}<Extension.Model> {
                val access: Access

                init {
                    this.access = objects.newInstance(Access::class.java)
                }

                @Configuring
                fun access(configure: Action<Access>) {
                    throw RuntimeException("Boom Action")
                }

                fun access(configure: (Access) -> Unit) {
                    throw RuntimeException("Boom Lambda")
                }

                abstract class Access {
                    @get:Restricted
                    abstract val name: Property<String>?
                }

                interface Model : ${BuildModel.class.simpleName} {
                }
            }
        """

        file("build-logic/src/main/java/com/example/restricted/SoftwareTypeRegistrationPlugin.java") <<
            defineSettingsPluginRegisteringSoftwareTypeProvidingPlugin()

        file("build-logic/src/main/java/com/example/restricted/RestrictedPlugin.java") << defineProjectPlugin()

        file("settings.gradle.dcl") << defineSettingsLogic()

        file("build.gradle.dcl") << """
            restricted {
                access {
                    name = "something"
                }
            }
        """

        when:
        def failure = buildAndFail(":help")

        then:
        failure.assertThatCause(containsString("Boom Action"))
    }

    @Test
    void 'fails disambiguating between two annotated, semantically equivalent methods'() {
        given:
        file("build-logic/build.gradle.kts") << defineBuildLogic([
            "id(\"java-gradle-plugin\")",
            "`kotlin-dsl`"
        ])

        file("build-logic/src/main/kotlin/com/example/restricted/Extension.kt") << """
            package com.example.restricted;

            import org.gradle.api.Action
            import org.gradle.api.model.ObjectFactory
            import org.gradle.api.provider.Property
            import org.gradle.declarative.dsl.model.annotations.Configuring
            import org.gradle.declarative.dsl.model.annotations.Restricted
            import javax.inject.Inject
            import ${BuildModel.class.name}
            import ${Definition.class.name}


            @Restricted
            abstract class Extension @Inject constructor(private val objects: ObjectFactory) : ${Definition.class.simpleName}<Extension.Model> {
                val access: Access

                init {
                    this.access = objects.newInstance(Access::class.java)
                }

                @Configuring
                fun access(configure: Action<Access>) {
                    throw RuntimeException("Boom Action")
                }

                @Configuring
                fun access(configure: (Access) -> Unit) {
                    throw RuntimeException("Boom Lambda")
                }

                abstract class Access {
                    @get:Restricted
                    abstract val name: Property<String>?
                }

                interface Model : ${BuildModel.class.simpleName} {
                }
            }
        """

        file("build-logic/src/main/java/com/example/restricted/SoftwareTypeRegistrationPlugin.java") <<
            defineSettingsPluginRegisteringSoftwareTypeProvidingPlugin()

        file("build-logic/src/main/java/com/example/restricted/RestrictedPlugin.java") << defineProjectPlugin()

        file("settings.gradle.dcl") << defineSettingsLogic()

        file("build.gradle.dcl") << """
            restricted {
                access {
                    name = "something"
                }
            }
        """

        when:
        def failure = buildAndFail(":help")

        then:
        failure.assertThatCause(containsString("Failed disambiguating between following functions (matches 2):"))
        failure.assertThatCause(containsString("fun com.example.restricted.Extension.access(org.gradle.api.Action<com.example.restricted.Extension.Access>): kotlin.Unit"))
        failure.assertThatCause(containsString("fun com.example.restricted.Extension.access((com.example.restricted.Extension.Access) -> kotlin.Unit): kotlin.Unit"))
    }

    @Test
    void 'when reflective invocation fails the cause is identified correctly'() {
        given:
        file("build-logic/build.gradle.kts") << defineBuildLogic(["id(\"java-gradle-plugin\")"])

        file("build-logic/src/main/java/com/example/restricted/Extension.java") << """
            package com.example.restricted;

            import org.gradle.declarative.dsl.model.annotations.Configuring;
            import org.gradle.declarative.dsl.model.annotations.Restricted;
            import org.gradle.api.Action;
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.api.provider.Property;
            import ${BuildModel.class.name};
            import ${Definition.class.name};

            import javax.inject.Inject;

            @Restricted
            public abstract class Extension implements ${Definition.class.simpleName}<Extension.Model> {
                private final Access access;
                private final ObjectFactory objects;

                public Access getAccess() {
                    return access;
                }

                @Inject
                public Extension(ObjectFactory objects) {
                    this.objects = objects;
                    this.access = objects.newInstance(Access.class);
                }

                @Configuring
                public void access(Action<? super Access> configure) {
                    throw new RuntimeException("Boom");
                }

                public abstract static class Access {
                    @Restricted
                    public abstract Property<String> getName();
                }

                interface Model extends ${BuildModel.class.simpleName} {
                }
            }
        """

        file("build-logic/src/main/java/com/example/restricted/SoftwareTypeRegistrationPlugin.java") <<
            defineSettingsPluginRegisteringSoftwareTypeProvidingPlugin()

        file("build-logic/src/main/java/com/example/restricted/RestrictedPlugin.java") << defineProjectPlugin()

        file("settings.gradle.dcl") << defineSettingsLogic()

        file("build.gradle.dcl") << """
            restricted {
                access {
                    name = "something"
                }
            }
        """

        when:
        def failure = buildAndFail(":help")

        then:
        failure.assertThatCause(containsString("Boom"))
    }

    private static String defineBuildLogic(ArrayList<String> plugins) {
        """
            plugins {
                ${plugins.join("\n")}
            }
            repositories {
                mavenCentral()
            }
            gradlePlugin {
                plugins {
                    create("restrictedPlugin") {
                        id = "com.example.restricted"
                        implementationClass = "com.example.restricted.RestrictedPlugin"
                    }
                    create("restrictedEcosystem") {
                        id = "com.example.restricted.ecosystem"
                        implementationClass = "com.example.restricted.SoftwareTypeRegistrationPlugin"
                    }
                }
            }
        """
    }

    private static String defineSettingsLogic() {
        """
            pluginManagement {
                includeBuild("build-logic")
            }

            plugins {
                id("com.example.restricted.ecosystem")
            }
        """
    }

    private static String defineSettingsPluginRegisteringSoftwareTypeProvidingPlugin() {
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

    private static String defineProjectPlugin() {
        """
            package com.example.restricted;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import ${BindsProjectType.class.name};
            import ${ProjectTypeBinding.class.name};
            import ${ProjectTypeBindingBuilder.class.name};

            @${BindsProjectType.class.simpleName}(RestrictedPlugin.Binding.class)
            public abstract class RestrictedPlugin implements Plugin<Project> {
                public static class Binding implements ${ProjectTypeBinding.class.simpleName} {
                    public void bind(${ProjectTypeBindingBuilder.class.simpleName} builder) {
                        builder.bindProjectType("restricted",  Extension.class, (context, definition, model) -> { }).withUnsafeDefinition();
                    }
                }

                @Override
                public void apply(Project target) {
                }
            }
        """
    }

}
