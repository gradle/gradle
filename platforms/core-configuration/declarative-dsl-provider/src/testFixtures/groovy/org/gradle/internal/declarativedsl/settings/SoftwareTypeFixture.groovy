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

    PluginBuilder withSoftwareTypePluginWithNdoc() {
        return withSoftwareTypePlugins(
            softwareTypeExtensionWithNdoc,
            getProjectPluginThatProvidesSoftwareType("TestSoftwareTypeExtension", null, "SoftwareTypeImplPlugin", "testSoftwareType", ""),
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
            "anotherSoftwareType",
            anotherSoftwareTypeExtensionConventions
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

    PluginBuilder withSettingsPluginThatConfiguresModelDefaults() {
        return withSoftwareTypePlugins(
            softwareTypeExtension,
            projectPluginThatProvidesSoftwareType,
            settingsPluginThatConfiguresSoftwareTypeConventions
        )
    }

    PluginBuilder withSoftwareTypePluginThatExposesExtensionWithDependencies() {
        PluginBuilder pluginBuilder = withSoftwareTypePlugins(
            softwareTypeExtension,
            getProjectPluginThatProvidesSoftwareType("TestSoftwareTypeExtensionWithDependencies", "TestSoftwareTypeExtensionWithDependencies"),
            settingsPluginThatRegistersSoftwareType
        )

        pluginBuilder.file("src/main/java/org/gradle/test/TestSoftwareTypeExtensionWithDependencies.java") << softwareTypeExtensionWithAddersAndDependencies
        pluginBuilder.file("src/main/java/org/gradle/test/LibraryDependencies.java") << libraryDependencies

        return pluginBuilder
    }

    PluginBuilder withSoftwareTypePluginThatRegistersItsOwnExtension() {
        return withSoftwareTypePlugins(
            softwareTypeExtension,
            projectPluginThatRegistersItsOwnExtension,
            settingsPluginThatRegistersSoftwareType
        )
    }

    PluginBuilder withSoftwareTypePluginThatFailsToRegistersItsOwnExtension() {
        return withSoftwareTypePlugins(
            softwareTypeExtension,
            getProjectPluginThatRegistersItsOwnExtension(false),
            settingsPluginThatRegistersSoftwareType
        )
    }

    PluginBuilder withSoftwareTypePluginThatRegistersTheWrongExtension() {
        return withSoftwareTypePlugins(
            softwareTypeExtension,
            getProjectPluginThatRegistersItsOwnExtension(true, "new String()"),
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
                private boolean isFooConfigured = false;

                @Inject
                public TestSoftwareTypeExtension(ObjectFactory objects) {
                    this.foo = objects.newInstance(Foo.class);
                }

                @Restricted
                public abstract Property<String> getId();


                public Foo getFoo() {
                    return foo;
                }

                @Configuring
                public void foo(Action<? super Foo> action) {
                    isFooConfigured = true;
                    action.execute(foo);
                }

                public abstract static class Foo {
                    public Foo() { }

                    @Restricted
                    public abstract Property<String> getBar();
                }

                @Override
                public String toString() {
                    return "id = " + getId().get() + "\\nbar = " + getFoo().getBar().get() + (isFooConfigured ? "\\n(foo is configured)" : "");
                }
            }
        """
    }

    static String getSoftwareTypeExtensionWithNdoc() {
        return """
            package org.gradle.test;

            import org.gradle.declarative.dsl.model.annotations.Restricted;

            import org.gradle.api.Named;
            import org.gradle.api.NamedDomainObjectContainer;
            import org.gradle.api.provider.Property;

            import java.util.stream.Collectors;

            public abstract class TestSoftwareTypeExtension {
                public abstract NamedDomainObjectContainer<Foo> getFoos();

                public abstract static class Foo implements Named {
                    private String name;

                    public Foo(String name) {
                        this.name = name;
                    }

                    @Override
                    public String getName() {
                        return name;
                    }

                    @Restricted
                    public abstract Property<Integer> getX();

                    @Restricted
                    public abstract Property<Integer> getY();

                    @Override
                    public String toString() {
                        return "Foo(name = " + name + ", x = " + getX().get() + ", y = " + getY().get() + ")";
                    }
                }

                @Override
                public String toString() {
                    return getFoos().stream().map(Foo::toString).collect(Collectors.joining(", "));
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
                default void foo(Action<? super Foo> action) {
                    action.execute(getFoo());
                }

                public abstract static class Foo {
                    public Foo() { }

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
                }

                @Override
                public Foo getFoo() {
                    return foo;
                }

                @Restricted
                public abstract Property<String> getNonPublic();

                @Override
                public String toString() {
                    return "id = " + getId().get() + "\\nbar = " + getFoo().getBar().get();
                }
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
                public void apply(Settings target) { }
            }
        """
    }

    static String getProjectPluginThatProvidesSoftwareType(
        String implementationTypeClassName = "TestSoftwareTypeExtension",
        String publicTypeClassName = null,
        String softwareTypePluginClassName = "SoftwareTypeImplPlugin",
        String softwareType = "testSoftwareType",
        String conventions = testSoftwareTypeExtensionConventions
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

                @SoftwareType(${SoftwareTypeArgumentBuilder.name(softwareType)
                                    .modelPublicType(publicTypeClassName)
                                    .build()})
                abstract public ${implementationTypeClassName} getTestSoftwareTypeExtension();

                @Override
                public void apply(Project target) {
                    System.out.println("Applying " + getClass().getSimpleName());
                    ${implementationTypeClassName} extension = getTestSoftwareTypeExtension();

                    ${conventions}
                    target.getTasks().register("print${implementationTypeClassName}Configuration", DefaultTask.class, task -> {
                        task.doLast("print restricted extension content", t -> {
                            System.out.println(extension);
                        });
                    });
                }
            }
        """
    }

    static String getProjectPluginThatRegistersItsOwnExtension(
        boolean shouldRegisterExtension = true,
        String extension = "extension"
    ) {
        String implementationTypeClassName = "TestSoftwareTypeExtension"
        String softwareTypePluginClassName = "SoftwareTypeImplPlugin"
        String softwareType = "testSoftwareType"
        String conventions = testSoftwareTypeExtensionConventions
        String extensionRegistration = shouldRegisterExtension ? """target.getExtensions().add("${softwareType}", ${extension});""" : ""
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

                @SoftwareType(${SoftwareTypeArgumentBuilder.name(softwareType)
                                    .disableModelManagement(true)
                                    .build()})
                abstract public ${implementationTypeClassName} getTestSoftwareTypeExtension();

                @Override
                public void apply(Project target) {
                    System.out.println("Applying " + getClass().getSimpleName());
                    ${implementationTypeClassName} extension = getTestSoftwareTypeExtension();
                    ${extensionRegistration}

                    ${conventions}
                    target.getTasks().register("print${implementationTypeClassName}Configuration", DefaultTask.class, task -> {
                        task.doLast("print restricted extension content", t -> {
                            System.out.println(extension);
                        });
                    });
                }
            }
        """
    }

    private static class SoftwareTypeArgumentBuilder {
        String name
        String modelPublicType
        boolean disableModelManagement

        static SoftwareTypeArgumentBuilder name(String name) {
            SoftwareTypeArgumentBuilder builder = new SoftwareTypeArgumentBuilder()
            builder.name = name
            return builder
        }

        SoftwareTypeArgumentBuilder modelPublicType(String modelPublicType) {
            this.modelPublicType = modelPublicType
            return this
        }

        SoftwareTypeArgumentBuilder disableModelManagement(boolean disableModelManagement) {
            this.disableModelManagement = disableModelManagement
            return this
        }

        String build() {
            return "name=\"${name}\"" +
                (modelPublicType ? ", modelPublicType=${modelPublicType}.class" : "") +
                (disableModelManagement ? ", disableModelManagement=true" : "")
        }
    }

    static String getTestSoftwareTypeExtensionConventions() {
        return """
            extension.getId().convention("<no id>");
            extension.getFoo().getBar().convention("bar");
        """
    }

    static String getAnotherSoftwareTypeExtensionConventions(String variableName = "extension") {
        return """
            ${variableName}.getFoo().convention("foo");
            ${variableName}.getBar().getBaz().convention("baz");
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
                            System.out.println(extension);
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
            import org.gradle.api.tasks.Nested;

            import javax.inject.Inject;

            @Restricted
            public abstract class AnotherSoftwareTypeExtension {
                @Inject
                public AnotherSoftwareTypeExtension() { }

                @Restricted
                public abstract Property<String> getFoo();

                @Nested
                public abstract Bar getBar();

                @Configuring
                public void bar(Action<? super Bar> action) {
                    action.execute(getBar());
                }

                public abstract static class Bar {
                    public Bar() { }

                    @Restricted
                    public abstract Property<String> getBaz();
                }

                public String toString() {
                    return "foo = " + getFoo().get() + "\\nbaz = " + getBar().getBaz().get();
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
                    extension.getFoo().getBar().convention("bar");
                    extension.getId().convention("<no id>");
                    target.getTasks().register("print${implementationTypeClassName}Configuration", DefaultTask.class, task -> {
                        task.doLast("print restricted extension content", t -> {
                            System.out.println(extension);
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
                            System.out.println(extension);
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
                    ${testSoftwareTypeExtensionConventions}
                    target.getTasks().register("printTestSoftwareTypeExtensionConfiguration", DefaultTask.class, task -> {
                        task.doLast("print restricted extension content", t -> {
                            System.out.println(extension);
                        });
                    });
                    AnotherSoftwareTypeExtension another = getAnotherSoftwareTypeExtension();
                    ${getAnotherSoftwareTypeExtensionConventions("another")}
                    target.getTasks().register("printAnotherSoftwareTypeExtensionConfiguration", DefaultTask.class, task -> {
                        task.doLast("print restricted extension content", t -> {
                            System.out.println(another);
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
                            System.out.println(extension);
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
                        public Foo() { }

                        @Restricted
                        public abstract Property<String> getBar();
                    }
                }
            }
        """
    }

    static String getSettingsPluginThatConfiguresSoftwareTypeConventions(List<String> softwareTypeImplPluginClassName = ["SoftwareTypeImplPlugin"]) {
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
                    TestSoftwareTypeExtension convention = (TestSoftwareTypeExtension) target.getExtensions().getByName("testSoftwareType");
                    convention.getId().convention("plugin");
                    convention.getFoo().getBar().convention("plugin");
                }
            }
        """
    }

    static String getLibraryDependencies() {
        return """
            package org.gradle.test;

            import org.gradle.api.artifacts.dsl.Dependencies;
            import org.gradle.api.artifacts.dsl.DependencyCollector;
            import org.gradle.declarative.dsl.model.annotations.Restricted;


            @Restricted
            public interface LibraryDependencies extends Dependencies {

                DependencyCollector getApi();

                DependencyCollector getImplementation();

                DependencyCollector getRuntimeOnly();

                DependencyCollector getCompileOnly();

                // CompileOnlyApi is not included here, since both Android and KMP do not support it.
                // Does that mean we should also reconsider if we should support it? Or, should we
                // talk to Android and KMP about adding support
            }
        """
    }

    static String getSoftwareTypeExtensionWithAddersAndDependencies() {
        return """
            package org.gradle.test;

            import org.gradle.api.Action;
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.api.provider.Property;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.artifacts.dsl.DependencyCollector;
            import org.gradle.declarative.dsl.model.annotations.Configuring;
            import org.gradle.declarative.dsl.model.annotations.Restricted;
            import org.gradle.declarative.dsl.model.annotations.Adding;
            import org.gradle.api.tasks.Nested;

            import java.util.List;
            import javax.inject.Inject;

            @Restricted
            public abstract class TestSoftwareTypeExtensionWithDependencies extends TestSoftwareTypeExtension {
                @Inject
                public TestSoftwareTypeExtensionWithDependencies(ObjectFactory objects) {
                    super(objects);
                }

                @Nested
                abstract public LibraryDependencies getDependencies();

                @Configuring
                public void dependencies(Action<? super LibraryDependencies> action) {
                    action.execute(getDependencies());
                }


                public abstract ListProperty<String> getList();

                @Adding
                public void addToList(String value) {
                    getList().add(value);
                }

                @Nested
                public abstract Bar getBar();

                @Configuring
                public void bar(Action<? super Bar> action) {
                    action.execute(getBar());
                }

                public abstract static class Bar {

                    public abstract ListProperty<String> getBaz();

                    @Adding
                    public void addToBaz(String value) {
                        getBaz().add(value);
                    }
                }

                @Override
                public String toString() {
                    return super.toString() +
                        "\\nlist = " + printList(getList().get()) +
                        "\\nbaz = " + printList(getBar().getBaz().get()) +
                        "\\napi = " + printDependencies(getDependencies().getApi()) +
                        "\\nimplementation = " + printDependencies(getDependencies().getImplementation()) +
                        "\\nruntimeOnly = " + printDependencies(getDependencies().getRuntimeOnly()) +
                        "\\ncompileOnly = " + printDependencies(getDependencies().getCompileOnly());
                }

                private String printDependencies(DependencyCollector collector) {
                    return collector.getDependencies().get().stream().map(Object::toString).collect(java.util.stream.Collectors.joining(", "));
                }

                private String printList(List<?> list) {
                    return list.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(", "));
                }
            }
        """
    }
}
