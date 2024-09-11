/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.internal.plugins.software.RegistersSoftwareTypes
import org.gradle.api.internal.plugins.software.SoftwareType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.intellij.lang.annotations.Language

class DeclarativeDslProjectBuildFileIntegrationSpec extends AbstractIntegrationSpec {

    def 'schema is written during project files interpretation with declarative DSL'() {
        given:
        settingsFile("""
            include(":sub")
        """)

        file("build.gradle.dcl") << ""

        file("sub/build.gradle.dcl") << ""

        when:
        run(":help")

        then:
        [
            file(".gradle/declarative-schema/project.dcl.schema"),
        ].every { it.isFile() && it.text != "" }
    }

    def 'can configure a custom plugin extension in declarative DSL for a plugin written in #language'() {
        given:
        file("build-logic/build.gradle.kts") << """
            plugins {
                `java-gradle-plugin`
                ${if (language == "kotlin") { "`kotlin-dsl`" } else { "" }}
            }
            ${if (language == "kotlin") { "repositories { mavenCentral() }" } else { "" }}
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

        file(extensionFile) << extensionCode

        file("build-logic/src/main/java/com/example/restricted/SoftwareTypeRegistrationPlugin.java") << defineSettingsPluginRegisteringSoftwareTypeProvidingPlugin()
        file("build-logic/src/main/java/com/example/restricted/RestrictedPlugin.java") << """
            package com.example.restricted;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;
            import ${SoftwareType.class.name};

            public abstract class RestrictedPlugin implements Plugin<Project> {
                @SoftwareType(name = "restricted", modelPublicType = Extension.class)
                public abstract Extension getRestricted();

                @Override
                public void apply(Project target) {
                    target.getTasks().register("printConfiguration", DefaultTask.class, task -> {
                        Property<Extension.Point> referencePoint = getRestricted().getReferencePoint();
                        Extension.Access acc = getRestricted().getPrimaryAccess();
                        ListProperty<Extension.Access> secondaryAccess = getRestricted().getSecondaryAccess();

                        task.doLast("print restricted extension content", t -> {
                            System.out.println("id = " + getRestricted().getId().get());
                            Extension.Point point = referencePoint.getOrElse(getRestricted().point(-1, -1));
                            System.out.println("referencePoint = (" + point.getX() + ", " + point.getY() + ")");
                            System.out.println("primaryAccess = { " +
                                    acc.getName().get() + ", " + acc.getRead().get() + ", " + acc.getWrite().get() + "}"
                            );
                            secondaryAccess.get().forEach(it -> {
                                System.out.println("secondaryAccess { " +
                                        it.getName().get() + ", " + it.getRead().get() + ", " + it.getWrite().get() +
                                        "}"
                                );
                            });
                        });
                    });
                }
            }
        """

        file("settings.gradle.dcl") << """
            pluginManagement {
                includeBuild("build-logic")
            }
            plugins {
                id("com.example.restricted.ecosystem")
            }
        """

        file("build.gradle.dcl") << """
            restricted {
                id = "test"

                referencePoint = point(1, 2)

                primaryAccess {
                    read = false
                    write = false
                }

                secondaryAccess {
                    name = "two"
                    read = true
                    write = false
                }

                secondaryAccess {
                    name = "three"
                    read = true
                    write = true
                }
            }
        """

        when:
        run(":printConfiguration")

        then:
        outputContains("""id = test
referencePoint = (1, 2)
primaryAccess = { primary, false, false}
secondaryAccess { two, true, false}
secondaryAccess { three, true, true}"""
        )

        where:
        language | extensionFile                    | extensionCode
        "java"   | JAVA_PLUGIN_EXTENSION_FILENAME   | JAVA_PLUGIN_EXTENSION
        "kotlin" | KOTLIN_PLUGIN_EXTENSION_FILENAME | KOTLIN_PLUGIN_EXTENSION
    }

    private static final JAVA_PLUGIN_EXTENSION_FILENAME = "build-logic/src/main/java/com/example/restricted/Extension.java"
    private static final KOTLIN_PLUGIN_EXTENSION_FILENAME = "build-logic/src/main/kotlin/com/example/restricted/Extension.kt"

    @Language("java")
    private static final JAVA_PLUGIN_EXTENSION = """
        package com.example.restricted;

        import org.gradle.declarative.dsl.model.annotations.Adding;
        import org.gradle.declarative.dsl.model.annotations.Configuring;
        import org.gradle.declarative.dsl.model.annotations.Restricted;
        import org.gradle.api.Action;
        import org.gradle.api.model.ObjectFactory;
        import org.gradle.api.provider.ListProperty;
        import org.gradle.api.provider.Property;

        import javax.inject.Inject;

        @Restricted
        public abstract class Extension {
            private final Access primaryAccess;
            public abstract ListProperty<Access> getSecondaryAccess();
            private final ObjectFactory objects;

            public Access getPrimaryAccess() {
                return primaryAccess;
            }

            @Inject
            public Extension(ObjectFactory objects) {
                this.objects = objects;
                this.primaryAccess = objects.newInstance(Access.class);
                this.primaryAccess.getName().set("primary");

                getId().convention("<no id>");
                getReferencePoint().convention(point(-1, -1));
            }

            @Restricted
            public abstract Property<String> getId();

            @Restricted
            public abstract Property<Point> getReferencePoint();

            @Configuring
            public void primaryAccess(Action<? super Access> configure) {
                configure.execute(primaryAccess);
            }

            @Adding
            public Access secondaryAccess(Action<? super Access> configure) {
                Access newAccess = objects.newInstance(Access.class);
                newAccess.getName().convention("<no name>");
                configure.execute(newAccess);
                getSecondaryAccess().add(newAccess);
                return newAccess;
            }

            @Restricted
            public Point point(int x, int y) {
                return new Point(x, y);
            }

            public abstract static class Access {
                public Access() {
                    getName().convention("<no name>");
                    getRead().convention(false);
                    getWrite().convention(false);
                }

                @Restricted
                public abstract Property<String> getName();

                @Restricted
                public abstract Property<Boolean> getRead();

                @Restricted
                public abstract Property<Boolean> getWrite();
            }

            public static class Point {
                public final int xCoord;
                public final int yCoord;

                public Point(int x, int y) {
                    this.xCoord = x;
                    this.yCoord = y;
                }

                public int getX() {
                    return xCoord;
                }

                public int getY() {
                    return yCoord;
                }
            }
        }
    """.stripIndent()

    @Language("kotlin")
    private static final KOTLIN_PLUGIN_EXTENSION = """
        package com.example.restricted

        import org.gradle.api.model.ObjectFactory
        import org.gradle.api.provider.ListProperty
        import org.gradle.api.provider.Property
        import org.gradle.declarative.dsl.model.annotations.Adding
        import org.gradle.declarative.dsl.model.annotations.Configuring
        import org.gradle.declarative.dsl.model.annotations.Restricted
        import javax.inject.Inject

        @Restricted
        abstract class Extension @Inject constructor(private val objects: ObjectFactory) {
            val primaryAccess: Access
            abstract val secondaryAccess: ListProperty<Access?>

            init {
                this.primaryAccess = objects.newInstance(Access::class.java)
                primaryAccess.name.set("primary")

                id.convention("<no id>")
                referencePoint.convention(point(-1, -1))
            }

            @get:Restricted
            abstract val id: Property<String?>

            @get:Restricted
            abstract val referencePoint: Property<Point?>

            @Configuring
            fun primaryAccess(configure: Access.() -> Unit) {
                configure(primaryAccess)
            }

            @Adding
            fun secondaryAccess(configure: Access.() -> Unit): Access {
                val newAccess = objects.newInstance(Access::class.java)
                newAccess.name.convention("<no name>")
                configure(newAccess)
                secondaryAccess.add(newAccess)
                return newAccess
            }

            @Restricted
            fun point(x: Int, y: Int): Point {
                return Point(x, y)
            }

            abstract class Access {
                init {
                    name.convention("<no name>")
                    read.convention(false)
                    write.convention(false)
                }

                @get:Restricted
                abstract val name: Property<String?>

                @get:Restricted
                abstract val read: Property<Boolean?>

                @get:Restricted
                abstract val write: Property<Boolean?>
            }

            class Point(val x: Int, val y: Int)
        }
    """

    def 'reports #kind errors in project file'() {
        given:
        file("build.gradle.dcl") << bodyCode

        when:
        def failure = fails(":help")

        then:
        failure.assertHasErrorOutput(expectedMessage)

        where:
        kind               | bodyCode              | expectedMessage
        "syntax"           | "..."                 | "1:1: parsing error: Expecting an element"
        "language feature" | "@A dependencies { }" | "1:1: unsupported language feature: AnnotationUsage"
        "semantic"         | "x = 1"               | "1:1: unresolved reference 'x'"
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
}
