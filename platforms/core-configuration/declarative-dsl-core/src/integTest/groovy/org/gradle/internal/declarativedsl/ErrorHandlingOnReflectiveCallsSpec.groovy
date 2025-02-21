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

import org.gradle.api.internal.plugins.software.RegistersSoftwareTypes
import org.gradle.api.internal.plugins.software.SoftwareType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.versions.KotlinGradlePluginVersions

class ErrorHandlingOnReflectiveCallsSpec extends AbstractIntegrationSpec {

    def 'can disambiguate between methods based on parameters'() {
        given:

        file("build-logic/build.gradle") << defineBuildLogic([
            "id(\"java-gradle-plugin\")",
            "id(\"org.jetbrains.kotlin.jvm\").version(\"${new KotlinGradlePluginVersions().latest}\")"
        ])

        file("build-logic/src/main/kotlin/com/example/restricted/Extension.kt") << """
            package com.example.restricted;

            import org.gradle.api.provider.Property
            import org.gradle.declarative.dsl.model.annotations.Restricted

            @Restricted
            abstract class Extension {

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
        fails(":help")

        then:
        failureCauseContains("Boom Int")
    }



    def 'can disambiguate between annotated and non-annotated methods'() {
        given:

        file("build-logic/build.gradle") << defineBuildLogic([
            "id(\"java-gradle-plugin\")",
            "id(\"org.jetbrains.kotlin.jvm\").version(\"${new KotlinGradlePluginVersions().latest}\")"
        ])

        file("build-logic/src/main/kotlin/com/example/restricted/Extension.kt") << """
            package com.example.restricted;

            import org.gradle.api.Action
            import org.gradle.api.model.ObjectFactory
            import org.gradle.api.provider.Property
            import org.gradle.declarative.dsl.model.annotations.Configuring
            import org.gradle.declarative.dsl.model.annotations.Restricted
            import javax.inject.Inject


            @Restricted
            abstract class Extension @Inject constructor(private val objects: ObjectFactory) {
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
                    abstract val name: Property<String?>?
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
        fails(":help")

        then:
        failureCauseContains("Boom Action")
    }

    def 'fails disambiguating between two annotated, semantically equivalent methods'() {
        given:

        file("build-logic/build.gradle") << defineBuildLogic([
            "id(\"java-gradle-plugin\")",
            "id(\"org.jetbrains.kotlin.jvm\").version(\"${new KotlinGradlePluginVersions().latest}\")"
        ])

        file("build-logic/src/main/kotlin/com/example/restricted/Extension.kt") << """
            package com.example.restricted;

            import org.gradle.api.Action
            import org.gradle.api.model.ObjectFactory
            import org.gradle.api.provider.Property
            import org.gradle.declarative.dsl.model.annotations.Configuring
            import org.gradle.declarative.dsl.model.annotations.Restricted
            import javax.inject.Inject


            @Restricted
            abstract class Extension @Inject constructor(private val objects: ObjectFactory) {
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
                    abstract val name: Property<String?>?
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
        fails(":help")


        then:
        failureCauseContains("Failed disambiguating between following functions (matches 2):")
        failureCauseContains("fun com.example.restricted.Extension.access(org.gradle.api.Action<com.example.restricted.Extension.Access>): kotlin.Unit")
        failureCauseContains("fun com.example.restricted.Extension.access((com.example.restricted.Extension.Access) -> kotlin.Unit): kotlin.Unit")
    }

    def 'when reflective invocation fails the cause is identified correctly'() {
        given:
        file("build-logic/build.gradle") << defineBuildLogic(["id('java-gradle-plugin')"])

        file("build-logic/src/main/java/com/example/restricted/Extension.java") << """
            package com.example.restricted;

            import org.gradle.declarative.dsl.model.annotations.Configuring;
            import org.gradle.declarative.dsl.model.annotations.Restricted;
            import org.gradle.api.Action;
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.api.provider.Property;

            import javax.inject.Inject;

            @Restricted
            public abstract class Extension {
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
        fails(":help")

        then:
        failureCauseContains("Boom")
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
            import ${SoftwareType.class.name};

            public abstract class RestrictedPlugin implements Plugin<Project> {
                @SoftwareType(name = "restricted", modelPublicType = Extension.class)
                public abstract Extension getExtension();

                @Override
                public void apply(Project target) {
                }
            }
        """
    }

}
