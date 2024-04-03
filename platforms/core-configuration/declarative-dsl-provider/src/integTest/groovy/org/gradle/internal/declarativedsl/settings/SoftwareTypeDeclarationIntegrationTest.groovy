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

package org.gradle.internal.declarativedsl.settings

import groovy.test.NotYetImplemented
import org.gradle.api.internal.plugins.software.SoftwareType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.fixtures.server.http.MavenHttpPluginRepository
import org.junit.Rule

class SoftwareTypeDeclarationIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    MavenHttpPluginRepository pluginPortal = MavenHttpPluginRepository.asGradlePluginPortal(executer, mavenRepo)

    @Rule
    MavenHttpPluginRepository mavenHttpRepo = new MavenHttpPluginRepository(mavenRepo)

    def 'can declare and configure a custom software type from included build'() {
        given:
        withSoftwareTypePlugins().prepareToExecute()

        file("settings.gradle.something") << """
            pluginManagement {
                includeBuild("plugins")
            }
            plugins {
                id("com.example.test-software-type")
            }
        """

        file("build.gradle.something") << declarativeScriptThatConfiguresSoftwareType

        when:
        run(":printConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()
    }

    def 'can declare and configure a custom software type from published plugin'() {
        given:
        def pluginBuilder = withSoftwareTypePlugins()
        pluginBuilder.publishAs("com", "example", "1.0", pluginPortal, createExecuter()).allowAll()

        file("settings.gradle.something") << """
            plugins {
                id("com.example.test-software-type") version("1.0")
            }
        """

        file("build.gradle.something") << declarativeScriptThatConfiguresSoftwareType

        when:
        run(":printConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()
    }

    /**
     * This test is not yet implemented because it requires a custom repository to be set up which is not possible yet with the declarative dsl.
     */
    @NotYetImplemented
    def 'can declare and configure a custom software type from plugin published to a custom repository'() {
        given:
        def pluginBuilder = withSoftwareTypePlugins()
        pluginBuilder.publishAs("com", "example", "1.0", mavenHttpRepo, createExecuter()).allowAll()

        file("settings.gradle.something") << """
            pluginManagement {
                repositories {
                    maven { url("$mavenHttpRepo.uri") }
                }
            }
            plugins {
                id("com.example.test-software-type") version("1.0")
            }
        """

        file("build.gradle.something") << declarativeScriptThatConfiguresSoftwareType

        when:
        run(":printConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()
    }

    static String getDeclarativeScriptThatConfiguresSoftwareType() {
        return """
            testSoftwareType {
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
    }

    void assertThatDeclaredValuesAreSetProperly() {
        outputContains("""id = test
referencePoint = (1, 2)
primaryAccess = { primary, false, false}
secondaryAccess { two, true, false}
secondaryAccess { three, true, true}"""
        )
    }

    PluginBuilder withSoftwareTypePlugins() {
        def pluginBuilder = new PluginBuilder(file("plugins"))
        pluginBuilder.addPluginId("com.example.test-software-type-impl", "SoftwareTypeImplPlugin")
        pluginBuilder.addPluginId("com.example.test-software-type", "SoftwareTypeRegistrationPlugin")

        pluginBuilder.file("src/main/java/org/gradle/test/TestSoftwareTypeExtension.java") << """
            package org.gradle.test;

            import org.gradle.declarative.dsl.model.annotations.Adding;
            import org.gradle.declarative.dsl.model.annotations.Configuring;
            import org.gradle.declarative.dsl.model.annotations.Restricted;
            import org.gradle.api.Action;
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;

            import javax.inject.Inject;

            @Restricted
            public abstract class TestSoftwareTypeExtension {
                private final Access primaryAccess;
                public abstract ListProperty<Access> getSecondaryAccess();
                private final ObjectFactory objects;

                public Access getPrimaryAccess() {
                    return primaryAccess;
                }

                @Inject
                public TestSoftwareTypeExtension(ObjectFactory objects) {
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
                    public final int x;
                    public final int y;

                    public Point(int x, int y) {
                        this.x = x;
                        this.y = y;
                    }
                }
            }
        """

        pluginBuilder.file("src/main/java/org/gradle/test/SoftwareTypeImplPlugin.java") << """
            package org.gradle.test;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;
            import ${SoftwareType.class.name};
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.api.tasks.Nested;
            import javax.inject.Inject;

            abstract public class SoftwareTypeImplPlugin implements Plugin<Project> {
                @Inject
                abstract protected ObjectFactory getObjectFactory();

                @SoftwareType(name="testSoftwareType", modelPublicType=TestSoftwareTypeExtension.class)
                abstract public TestSoftwareTypeExtension getTestSoftwareTypeExtension();

                @Override
                public void apply(Project target) {
                    TestSoftwareTypeExtension extension = getTestSoftwareTypeExtension();
                    target.getTasks().register("printConfiguration", DefaultTask.class, task -> {
                        Property<TestSoftwareTypeExtension.Point> referencePoint = extension.getReferencePoint();
                        TestSoftwareTypeExtension.Access acc = extension.getPrimaryAccess();
                        ListProperty<TestSoftwareTypeExtension.Access> secondaryAccess = extension.getSecondaryAccess();

                        task.doLast("print restricted extension content", t -> {
                            System.out.println("id = " + extension.getId().get());
                            TestSoftwareTypeExtension.Point point = referencePoint.getOrElse(extension.point(-1, -1));
                            System.out.println("referencePoint = (" + point.x + ", " + point.y + ")");
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

        pluginBuilder.file("src/main/java/org/gradle/test/SoftwareTypeRegistrationPlugin.java") << """
            package org.gradle.test;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.initialization.Settings;
            import org.gradle.api.internal.SettingsInternal;
            import org.gradle.plugin.software.internal.SoftwareTypeRegistry;

            abstract public class SoftwareTypeRegistrationPlugin implements Plugin<Settings> {
                @Override
                public void apply(Settings target) {
                    ((SettingsInternal)target).getServices().get(SoftwareTypeRegistry.class).register(SoftwareTypeImplPlugin.class);
                }
            }
        """

        return pluginBuilder
    }
}
