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

        file("build.gradle.something") << declarativeScriptThatConfiguresOnlyTestSoftwareType

        when:
        run(":printConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        and:
        outputContains("Applying SoftwareTypeImplPlugin")
        outputDoesNotContain("Applying AnotherSoftwareTypeImplPlugin")
    }

    def 'can declare and configure a custom software type from published plugin'() {
        given:
        def pluginBuilder = withSoftwareTypePlugins()
        pluginBuilder.publishAs("com", "example", "1.0", pluginPortal, createExecuter()).allowAll()

        file("settings.gradle.something") << """
            plugins {
                id("com.example.test-software-type").version("1.0")
            }
        """

        file("build.gradle.something") << declarativeScriptThatConfiguresOnlyTestSoftwareType

        when:
        run(":printConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        and:
        outputContains("Applying SoftwareTypeImplPlugin")
        outputDoesNotContain("Applying AnotherSoftwareTypeImplPlugin")
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
                id("com.example.test-software-type").version("1.0")
            }
        """

        file("build.gradle.something") << declarativeScriptThatConfiguresOnlyTestSoftwareType

        when:
        run(":printConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        and:
        outputContains("Applying SoftwareTypeImplPlugin")
        outputDoesNotContain("Applying AnotherSoftwareTypeImplPlugin")
    }

    static String getDeclarativeScriptThatConfiguresOnlyTestSoftwareType() {
        return """
            testSoftwareType {
                id = "test"

                foo {
                    bar = "baz"
                }
            }
        """
    }

    void assertThatDeclaredValuesAreSetProperly() {
        outputContains("""id = test\nbar = baz""")
    }

    PluginBuilder withSoftwareTypePlugins() {
        def pluginBuilder = new PluginBuilder(file("plugins"))
        pluginBuilder.addPluginId("com.example.test-software-type-impl", "SoftwareTypeImplPlugin")
        pluginBuilder.addPluginId("com.example.another-software-type-impl", "AnotherSoftwareTypeImplPlugin")
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
                private final Foo foo;

                @Inject
                public TestSoftwareTypeExtension(ObjectFactory objects) {
                    this.foo = objects.newInstance(Foo.class);
                    this.foo.getBar().set("bar");

                    getId().convention("<no id>");
                }

                @Restricted
                public abstract Property<String> getId();

                public Foo getFoo() {
                    return foo;
                }

                @Configuring
                public void foo(Action<? super Foo> action) {
                    action.execute(foo);
                }

                public abstract static class Foo {
                    public Foo() {
                        this.getBar().convention("nothing");
                    }

                    @Restricted
                    public abstract Property<String> getBar();
                }
            }
        """

        pluginBuilder.file("src/main/java/org/gradle/test/AnotherSoftwareTypeExtension.java") << """
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
            public abstract class AnotherSoftwareTypeExtension extends TestSoftwareTypeExtension {
                @Inject
                public AnotherSoftwareTypeExtension(ObjectFactory objects) {
                    super(objects);
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
                    System.out.println("Applying " + getClass().getSimpleName());
                    TestSoftwareTypeExtension extension = getTestSoftwareTypeExtension();
                    target.getTasks().register("printConfiguration", DefaultTask.class, task -> {
                        task.doLast("print restricted extension content", t -> {
                            System.out.println("id = " + extension.getId().get());
                            System.out.println("bar = " + extension.getFoo().getBar().get());
                        });
                    });
                }
            }
        """
        pluginBuilder.file("src/main/java/org/gradle/test/AnotherSoftwareTypeImplPlugin.java") << """
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

            abstract public class AnotherSoftwareTypeImplPlugin implements Plugin<Project> {
                @Inject
                abstract protected ObjectFactory getObjectFactory();

                @SoftwareType(name="anotherSoftwareType", modelPublicType=AnotherSoftwareTypeExtension.class)
                abstract public AnotherSoftwareTypeExtension getTestSoftwareTypeExtension();

                @Override
                public void apply(Project target) {
                    System.out.println("Applying " + getClass().getSimpleName());
                    AnotherSoftwareTypeExtension extension = getTestSoftwareTypeExtension();
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
                    ((SettingsInternal)target).getServices().get(SoftwareTypeRegistry.class).register(AnotherSoftwareTypeImplPlugin.class);
                }
            }
        """

        return pluginBuilder
    }
}
