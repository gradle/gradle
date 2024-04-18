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

import org.gradle.api.internal.plugins.software.RegistersSoftwareTypes
import org.gradle.api.internal.plugins.software.SoftwareType
import org.gradle.test.fixtures.plugin.PluginBuilder

trait SoftwareTypeFixture {
    PluginBuilder withSoftwareTypePlugins(String extensionClassContents, String softwareTypeImplPluginContents, String softwareTypeRegistrationPluginContents) {
        def pluginBuilder = new PluginBuilder(file("plugins"))
        pluginBuilder.addPluginId("com.example.test-software-type-impl", "SoftwareTypeImplPlugin")
        pluginBuilder.addPluginId("com.example.test-software-type", "SoftwareTypeRegistrationPlugin")

        pluginBuilder.file("src/main/java/org/gradle/test/TestSoftwareTypeExtension.java") << extensionClassContents
        pluginBuilder.file("src/main/java/org/gradle/test/SoftwareTypeImplPlugin.java") << softwareTypeImplPluginContents
        pluginBuilder.file("src/main/java/org/gradle/test/SoftwareTypeRegistrationPlugin.java") << softwareTypeRegistrationPluginContents

        return pluginBuilder
    }

    PluginBuilder withSoftwareTypePlugins() {
        return withSoftwareTypePlugins(
            softwareTypeExtension,
            projectPluginThatProvidesSoftwareType,
            settingsPluginThatRegistersSoftwareType
        )
    }

    PluginBuilder withSoftwareTypePluginWithMismatchedModelTypes() {
        def pluginBuilder = withSoftwareTypePlugins(
            softwareTypeExtension,
            getProjectPluginThatProvidesSoftwareType("TestSoftwareTypeExtension", "AnotherSoftwareTypeExtension"),
            settingsPluginThatRegistersSoftwareType
        )

        pluginBuilder.file("src/main/java/org/gradle/test/AnotherSoftwareTypeExtension.java") << anotherSoftwareTypeExtension

        return pluginBuilder
    }

    PluginBuilder withSoftwareTypePluginThatDoesNotExposeSoftwareTypes() {
        return withSoftwareTypePlugins(
            softwareTypeExtension,
            projectPluginThatDoesNotProvideSoftwareType,
            settingsPluginThatRegistersSoftwareType
        )
    }

    PluginBuilder withSettingsPluginThatExposesMultipleSoftwareTypes() {
        PluginBuilder pluginBuilder = withSoftwareTypePlugins(
            softwareTypeExtension,
            projectPluginThatProvidesSoftwareType,
            getSettingsPluginThatRegistersSoftwareType(["SoftwareTypeImplPlugin", "AnotherSoftwareTypeImplPlugin"])
        )

        pluginBuilder.addPluginId("com.example.another-software-type-impl", "AnotherSoftwareTypeImplPlugin")
        pluginBuilder.file("src/main/java/org/gradle/test/AnotherSoftwareTypeExtension.java") << anotherSoftwareTypeExtension
        pluginBuilder.file("src/main/java/org/gradle/test/AnotherSoftwareTypeImplPlugin.java") << getProjectPluginThatProvidesSoftwareType(
            "AnotherSoftwareTypeExtension",
            "AnotherSoftwareTypeExtension",
            "AnotherSoftwareTypeImplPlugin",
            "anotherSoftwareType"
        )

        return pluginBuilder
    }

    PluginBuilder withSoftwareTypePluginThatHasDifferentPublicAndImplementationModelTypes() {
        PluginBuilder pluginBuilder = withSoftwareTypePlugins(
            publicModelType,
            getProjectPluginThatProvidesSoftwareType("TestSoftwareTypeExtensionImpl", "TestSoftwareTypeExtension"),
            settingsPluginThatRegistersSoftwareType
        )

        pluginBuilder.file("src/main/java/org/gradle/test/TestSoftwareTypeExtensionImpl.java") << getSoftwareTypeExtensionWithPublicType()

        return pluginBuilder
    }

    PluginBuilder withSoftwareTypePluginThatExposesSoftwareTypeFromParentClass() {
        PluginBuilder pluginBuilder = withSoftwareTypePlugins(
            softwareTypeExtension,
            projectPluginThatProvidesSoftwareTypeFromParentClass,
            settingsPluginThatRegistersSoftwareType
        )

        pluginBuilder.file("src/main/java/org/gradle/test/ExposesSoftwareType.java") << getExposesSoftwareType()

        return pluginBuilder
    }

    PluginBuilder withSoftwareTypePluginThatHasUnannotatedMethods() {
        return withSoftwareTypePlugins(
            softwareTypeExtension,
            projectPluginThatProvidesSoftwareTypeThatHasUnannotatedMethods,
            settingsPluginThatRegistersSoftwareType
        )
    }

    PluginBuilder withSoftwareTypePluginThatExposesMultipleSoftwareTypes() {
        PluginBuilder pluginBuilder = withSoftwareTypePlugins(
            softwareTypeExtension,
            projectPluginThatProvidesMultipleSoftwareTypes,
            settingsPluginThatRegistersSoftwareType
        )

        pluginBuilder.file("src/main/java/org/gradle/test/AnotherSoftwareTypeExtension.java") << anotherSoftwareTypeExtension

        return pluginBuilder
    }

    PluginBuilder withSoftwareTypePluginThatExposesPrivateSoftwareType() {
        return withSoftwareTypePlugins(
            softwareTypeExtension,
            projectPluginThatProvidesPrivateSoftwareType,
            settingsPluginThatRegistersSoftwareType
        )
    }

    static String getSoftwareTypeExtension() {
        return """
            package org.gradle.test;

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
    }

    static String getPublicModelType() {
        return """
            package org.gradle.test;

            import org.gradle.declarative.dsl.model.annotations.Configuring;
            import org.gradle.declarative.dsl.model.annotations.Restricted;
            import org.gradle.api.provider.Property;
            import org.gradle.api.Action;

            @Restricted
            public interface TestSoftwareTypeExtension {
                @Restricted
                Property<String> getId();

                Foo getFoo();

                @Configuring
                void foo(Action<? super Foo> action);

                public abstract static class Foo {
                    public Foo() {
                        this.getBar().convention("nothing");
                    }

                    @Restricted
                    public abstract Property<String> getBar();
                }
            }
        """
    }

    static String getSoftwareTypeExtensionWithPublicType() {
        return """
            package org.gradle.test;

            import org.gradle.declarative.dsl.model.annotations.Restricted;
            import org.gradle.api.Action;
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;

            import javax.inject.Inject;

            @Restricted
            public abstract class TestSoftwareTypeExtensionImpl implements TestSoftwareTypeExtension {
                private final Foo foo;

                @Inject
                public TestSoftwareTypeExtensionImpl(ObjectFactory objects) {
                    this.foo = objects.newInstance(Foo.class);
                    this.foo.getBar().set("bar");

                    getId().convention("<no id>");
                }

                @Override
                public Foo getFoo() {
                    return foo;
                }

                @Override
                public void foo(Action<? super Foo> action) {
                    action.execute(foo);
                }

                @Restricted
                public abstract Property<String> getNonPublic();
            }
        """
    }

    static String getSettingsPluginThatRegistersSoftwareType(List<String> softwareTypeImplPluginClassName = ["SoftwareTypeImplPlugin"]) {
        return """
            package org.gradle.test;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.initialization.Settings;
            import org.gradle.api.internal.SettingsInternal;
            import org.gradle.plugin.software.internal.SoftwareTypeRegistry;
            import ${RegistersSoftwareTypes.class.name};

            @RegistersSoftwareTypes({ ${softwareTypeImplPluginClassName.collect { it + ".class" }.join(", ")} })
            abstract public class SoftwareTypeRegistrationPlugin implements Plugin<Settings> {
                @Override
                public void apply(Settings target) {

                }
            }
        """
    }

    static String getProjectPluginThatProvidesSoftwareType(
        String implementationTypeClassName = "TestSoftwareTypeExtension",
        String publicTypeClassName = "TestSoftwareTypeExtension",
        String softwareTypePluginClassName = "SoftwareTypeImplPlugin",
        String softwareType = "testSoftwareType"
    ) {
        return """
            package org.gradle.test;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;
            import org.gradle.api.tasks.Nested;
            import ${SoftwareType.class.name};
            import javax.inject.Inject;

            abstract public class ${softwareTypePluginClassName} implements Plugin<Project> {

                @SoftwareType(name="${softwareType}", modelPublicType=${publicTypeClassName}.class)
                abstract public ${implementationTypeClassName} getTestSoftwareTypeExtension();

                @Override
                public void apply(Project target) {
                    System.out.println("Applying " + getClass().getSimpleName());
                    ${implementationTypeClassName} extension = getTestSoftwareTypeExtension();
                    target.getTasks().register("print${implementationTypeClassName}Configuration", DefaultTask.class, task -> {
                        task.doLast("print restricted extension content", t -> {
                            System.out.println("id = " + extension.getId().get());
                            System.out.println("bar = " + extension.getFoo().getBar().get());
                        });
                    });
                }
            }
        """
    }

    static String getProjectPluginThatDoesNotProvideSoftwareType(String implementationTypeClassName = "TestSoftwareTypeExtension", String softwareTypePluginClassName = "SoftwareTypeImplPlugin") {
        return """
            package org.gradle.test;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;
            import org.gradle.api.tasks.Nested;
            import javax.inject.Inject;

            abstract public class ${softwareTypePluginClassName} implements Plugin<Project> {

                abstract public ${implementationTypeClassName} getTestSoftwareTypeExtension();

                @Override
                public void apply(Project target) {
                    ${implementationTypeClassName} extension = getTestSoftwareTypeExtension();
                    target.getTasks().register("print${implementationTypeClassName}Configuration", DefaultTask.class, task -> {
                        task.doLast("print restricted extension content", t -> {
                            System.out.println("id = " + extension.getId().get());
                            System.out.println("bar = " + extension.getFoo().getBar().get());
                        });
                    });
                }
            }
        """
    }

    static String getAnotherSoftwareTypeExtension() {
        return """
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
            public abstract class AnotherSoftwareTypeExtension {
                private final Foo foo;

                @Inject
                public AnotherSoftwareTypeExtension(ObjectFactory objects) {
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
    }

    static String getProjectPluginThatProvidesSoftwareTypeFromParentClass(
        String implementationTypeClassName = "TestSoftwareTypeExtension",
        String softwareTypePluginClassName = "SoftwareTypeImplPlugin"
    ) {
        return """
            package org.gradle.test;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;
            import org.gradle.api.Project;
            import org.gradle.api.tasks.Nested;
            import ${SoftwareType.class.name};
            import javax.inject.Inject;

            abstract public class ${softwareTypePluginClassName} implements ExposesSoftwareType {

                @Override
                public void apply(Project target) {
                    ${implementationTypeClassName} extension = getTestSoftwareTypeExtension();
                    target.getTasks().register("print${implementationTypeClassName}Configuration", DefaultTask.class, task -> {
                        task.doLast("print restricted extension content", t -> {
                            System.out.println("id = " + extension.getId().get());
                            System.out.println("bar = " + extension.getFoo().getBar().get());
                        });
                    });
                }
            }
        """
    }

    static String getExposesSoftwareType(String softwareType = "testSoftwareType", implementationTypeClassName = "TestSoftwareTypeExtension", publicTypeClassName = "TestSoftwareTypeExtension") {
        return """
            package org.gradle.test;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import ${SoftwareType.class.name};

            public interface ExposesSoftwareType extends Plugin<Project> {
                @SoftwareType(name="${softwareType}", modelPublicType=${publicTypeClassName}.class)
                abstract public ${implementationTypeClassName} getTestSoftwareTypeExtension();
            }
        """
    }

    static String getProjectPluginThatProvidesSoftwareTypeThatHasUnannotatedMethods(
        String implementationTypeClassName = "TestSoftwareTypeExtension",
        String publicTypeClassName = "TestSoftwareTypeExtension",
        String softwareTypePluginClassName = "SoftwareTypeImplPlugin",
        String softwareType = "testSoftwareType"
    ) {
        return """
            package org.gradle.test;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;
            import org.gradle.api.tasks.Nested;
            import ${SoftwareType.class.name};
            import javax.inject.Inject;

            abstract public class ${softwareTypePluginClassName} implements Plugin<Project> {

                @SoftwareType(name="${softwareType}", modelPublicType=${publicTypeClassName}.class)
                abstract public ${implementationTypeClassName} getTestSoftwareTypeExtension();

                String getFoo() {
                    return "foo";
                }

                @Override
                public void apply(Project target) {
                    ${implementationTypeClassName} extension = getTestSoftwareTypeExtension();
                    target.getTasks().register("print${implementationTypeClassName}Configuration", DefaultTask.class, task -> {
                        task.doLast("print restricted extension content", t -> {
                            System.out.println("id = " + extension.getId().get());
                            System.out.println("bar = " + extension.getFoo().getBar().get());
                        });
                    });
                    System.out.println(getFoo());
                }
            }
        """
    }

    static String getProjectPluginThatProvidesMultipleSoftwareTypes(
        String softwareTypePluginClassName = "SoftwareTypeImplPlugin"
    ) {
        return """
            package org.gradle.test;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;
            import org.gradle.api.tasks.Nested;
            import ${SoftwareType.class.name};
            import javax.inject.Inject;

            abstract public class ${softwareTypePluginClassName} implements Plugin<Project> {

                @SoftwareType(name="testSoftwareType", modelPublicType=TestSoftwareTypeExtension.class)
                abstract public TestSoftwareTypeExtension getTestSoftwareTypeExtension();

                @SoftwareType(name="anotherSoftwareType", modelPublicType=AnotherSoftwareTypeExtension.class)
                abstract public AnotherSoftwareTypeExtension getAnotherSoftwareTypeExtension();

                @Override
                public void apply(Project target) {
                    System.out.println("Applying " + getClass().getSimpleName());
                    TestSoftwareTypeExtension extension = getTestSoftwareTypeExtension();
                    target.getTasks().register("printTestSoftwareTypeExtensionConfiguration", DefaultTask.class, task -> {
                        task.doLast("print restricted extension content", t -> {
                            System.out.println("id = " + extension.getId().get());
                            System.out.println("bar = " + extension.getFoo().getBar().get());
                        });
                    });
                    AnotherSoftwareTypeExtension another = getAnotherSoftwareTypeExtension();
                    target.getTasks().register("printAnotherSoftwareTypeExtensionConfiguration", DefaultTask.class, task -> {
                        task.doLast("print restricted extension content", t -> {
                            System.out.println("id = " + another.getId().get());
                            System.out.println("bar = " + another.getFoo().getBar().get());
                        });
                    });
                }
            }
        """
    }

    static String getProjectPluginThatProvidesPrivateSoftwareType(
        String softwareTypePluginClassName = "SoftwareTypeImplPlugin"
    ) {
        return """
            package org.gradle.test;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;
            import org.gradle.api.tasks.Nested;
            import ${SoftwareType.class.name};
            import org.gradle.declarative.dsl.model.annotations.Adding;
            import org.gradle.declarative.dsl.model.annotations.Configuring;
            import org.gradle.declarative.dsl.model.annotations.Restricted;
            import org.gradle.api.Action;
            import org.gradle.api.model.ObjectFactory;

            import javax.inject.Inject;

            abstract public class ${softwareTypePluginClassName} implements Plugin<Project> {

                @SoftwareType(name="testSoftwareType", modelPublicType=AnotherSoftwareTypeExtension.class)
                abstract public AnotherSoftwareTypeExtension getTestSoftwareTypeExtension();

                @Override
                public void apply(Project target) {
                    System.out.println("Applying " + getClass().getSimpleName());
                    AnotherSoftwareTypeExtension extension = getTestSoftwareTypeExtension();
                    target.getTasks().register("printTestSoftwareTypeExtensionConfiguration", DefaultTask.class, task -> {
                        task.doLast("print restricted extension content", t -> {
                            System.out.println("id = " + extension.getId().get());
                            System.out.println("bar = " + extension.getFoo().getBar().get());
                        });
                    });
                }

                @Restricted
                private static abstract class AnotherSoftwareTypeExtension {
                    private final Foo foo;

                    @Inject
                    public AnotherSoftwareTypeExtension(ObjectFactory objects) {
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

                    public static abstract class Foo {
                        public Foo() {
                            this.getBar().convention("nothing");
                        }

                        @Restricted
                        public abstract Property<String> getBar();
                    }
                }
            }
        """
    }
}
