/*
 * Copyright 2025 the original author or authors.
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

import groovy.transform.SelfType
import org.gradle.api.internal.plugins.BindsSoftwareType
import org.gradle.api.internal.plugins.BuildModel
import org.gradle.api.internal.plugins.HasBuildModel
import org.gradle.api.internal.plugins.SoftwareTypeBindingBuilder
import org.gradle.api.internal.plugins.SoftwareTypeBindingRegistration
import org.gradle.api.internal.plugins.software.RegistersSoftwareFeatures
import org.gradle.api.internal.plugins.software.RegistersSoftwareTypes
import org.gradle.api.internal.plugins.software.SoftwareType // codenarc-disable-line UnusedImport
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.plugin.PluginBuilder

@SelfType(AbstractIntegrationSpec)
trait SoftwareTypeFixture {
    PluginBuilder withSoftwareTypePlugins(SoftwareTypeDefinitionClassBuilder definitionBuilder, SoftwareTypePluginClassBuilder softwareTypeBuilder, SettingsPluginClassBuilder settingsBuilder) {
        def pluginBuilder = new PluginBuilder(file("plugins"))
        pluginBuilder.addPluginId("com.example.test-software-type-impl", softwareTypeBuilder.softwareTypePluginClassName)
        pluginBuilder.addPluginId("com.example.test-software-ecosystem", settingsBuilder.pluginClassName)

        definitionBuilder.build(pluginBuilder)
        softwareTypeBuilder.build(pluginBuilder)
        settingsBuilder.build(pluginBuilder)

        return pluginBuilder
    }

    PluginBuilder withSoftwareTypePlugins() {
        def definition = new SoftwareTypeDefinitionClassBuilder()
        def softwareType = new SoftwareTypePluginClassBuilder()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersSoftwareType(softwareType.softwareTypePluginClassName)

        return withSoftwareTypePlugins(
            definition,
            softwareType,
            settingsBuilder
        )
    }

    PluginBuilder withSoftwareTypePluginWithNdoc() {
        def definition = new SoftwareTypeDefinitionWithNdocClassBuilder()
        def softwareType = new SoftwareTypePluginClassBuilder()
            .withoutConventions()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersSoftwareType(softwareType.softwareTypePluginClassName)

        return withSoftwareTypePlugins(
            definition,
            softwareType,
            settingsBuilder
        )
    }

    PluginBuilder withSoftwareTypePluginThatDoesNotExposeSoftwareTypes() {
        def definition = new SoftwareTypeDefinitionWithNdocClassBuilder()
        def softwareType = new ProjectPluginThatDoesNotExposeSoftwareTypesBuilder()
            .softwareTypePluginClassName("NotASoftwareTypePlugin")
            .withoutConventions()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersSoftwareType(softwareType.softwareTypePluginClassName)

        return withSoftwareTypePlugins(
            definition,
            softwareType,
            settingsBuilder
        )
    }

    PluginBuilder withSettingsPluginThatExposesMultipleSoftwareTypes() {
        def mainDefinition = new SoftwareTypeDefinitionClassBuilder()
        def anotherDefinition = new AnotherSoftwareTypeDefinitionClassBuilder()
        def mainSoftwareType = new SoftwareTypePluginClassBuilder()
        def anotherSoftwareType = new SoftwareTypePluginClassBuilder()
            .definitionImplementationTypeClassName("AnotherSoftwareTypeExtension")
            .definitionPublicTypeClassName("AnotherSoftwareTypeExtension")
            .softwareTypePluginClassName("AnotherSoftwareTypeImplPlugin")
            .withoutConventions()
            .name("anotherSoftwareType")
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersSoftwareType(mainSoftwareType.softwareTypePluginClassName)
            .registersSoftwareType(anotherSoftwareType.softwareTypePluginClassName)

        PluginBuilder pluginBuilder = withSoftwareTypePlugins(
            mainDefinition,
            mainSoftwareType,
            settingsBuilder
        )

        pluginBuilder.addPluginId("com.example.another-software-type-impl", anotherSoftwareType.softwareTypePluginClassName)
        anotherSoftwareType.build(pluginBuilder)
        anotherDefinition.build(pluginBuilder)

        return pluginBuilder
    }

    PluginBuilder withSoftwareTypePluginThatHasDifferentPublicAndImplementationModelTypes() {
        def definition = new SoftwareTypeDefinitionWithPublicTypeClassBuilder()
        def softwareType = new SoftwareTypePluginClassBuilder()
            .definitionPublicTypeClassName(definition.publicTypeClassName)
            .definitionImplementationTypeClassName(definition.implementationTypeClassName)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersSoftwareType(softwareType.softwareTypePluginClassName)

        PluginBuilder pluginBuilder = withSoftwareTypePlugins(
            definition,
            softwareType,
            settingsBuilder
        )

        return pluginBuilder
    }

    PluginBuilder withSoftwareTypePluginThatExposesMultipleSoftwareTypes() {
        def definition = new SoftwareTypeDefinitionClassBuilder()
        def anotherSoftwareTypeDefinition = new AnotherSoftwareTypeDefinitionClassBuilder()
        def softwareType = new ProjectPluginThatProvidesMultipleSoftwareTypesBuilder()
            .anotherDefinitionImplementationTypeClassName(anotherSoftwareTypeDefinition.implementationTypeClassName)
            .definitionImplementationTypeClassName(definition.implementationTypeClassName)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersSoftwareType(softwareType.softwareTypePluginClassName)

        PluginBuilder pluginBuilder = withSoftwareTypePlugins(
            definition,
            softwareType,
            settingsBuilder
        )

        anotherSoftwareTypeDefinition.build(pluginBuilder)

        return pluginBuilder
    }

    PluginBuilder withSoftwareTypePluginThatExposesExtensionWithDependencies() {
        def definitionWithClasses = new SoftwareTypeDefinitionWithDependenciesClassBuilder()
        def softwareType = new SoftwareTypePluginClassBuilder()
            .definitionImplementationTypeClassName(definitionWithClasses.implementationTypeClassName)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersSoftwareType(softwareType.softwareTypePluginClassName)

        PluginBuilder pluginBuilder = withSoftwareTypePlugins(
            definitionWithClasses,
            softwareType,
            settingsBuilder
        )

        return pluginBuilder
    }

    PluginBuilder withSettingsPluginThatConfiguresModelDefaults() {
        def definition = new SoftwareTypeDefinitionClassBuilder()
        def softwareType = new SoftwareTypePluginClassBuilder()
        def settingsBuilder = new SettingsPluginThatConfiguresSoftwareTypeConventionsBuilder()
            .definitionImplementationTypeClassName(definition.implementationTypeClassName)
            .registersSoftwareType(softwareType.softwareTypePluginClassName)

        return withSoftwareTypePlugins(
            definition,
            softwareType,
            settingsBuilder
        )
    }

    static class SoftwareTypePluginClassBuilder {
        String definitionImplementationTypeClassName = "TestSoftwareTypeExtension"
        String definitionPublicTypeClassName = null
        String softwareTypePluginClassName = "SoftwareTypeImplPlugin"
        String name = "testSoftwareType"
        String conventions = """
            definition.getId().convention("<no id>");
            definition.getFoo().getBar().convention("bar");
        """

        SoftwareTypePluginClassBuilder definitionImplementationTypeClassName(String implementationTypeClassName) {
            this.definitionImplementationTypeClassName = implementationTypeClassName
            return this
        }

        SoftwareTypePluginClassBuilder definitionPublicTypeClassName(String publicTypeClassName) {
            this.definitionPublicTypeClassName = publicTypeClassName
            return this
        }

        SoftwareTypePluginClassBuilder softwareTypePluginClassName(String softwareTypePluginClassName) {
            this.softwareTypePluginClassName = softwareTypePluginClassName
            return this
        }

        SoftwareTypePluginClassBuilder name(String name) {
            this.name = name
            return this
        }

        SoftwareTypePluginClassBuilder conventions(String conventions) {
            this.conventions = conventions
            return this
        }

        SoftwareTypePluginClassBuilder withoutConventions() {
            this.conventions = null
            return this
        }

        void build(PluginBuilder pluginBuilder) {
            pluginBuilder.file("src/main/java/org/gradle/test/${softwareTypePluginClassName}.java") << getClassContent()
        }

        protected String getClassContent() {
            def dslTypeClassName = definitionPublicTypeClassName ?: definitionImplementationTypeClassName
            return """
                package org.gradle.test;

                import org.gradle.api.DefaultTask;
                import org.gradle.api.Plugin;
                import org.gradle.api.Project;
                import org.gradle.api.provider.ListProperty;
                import org.gradle.api.provider.Property;
                import org.gradle.api.tasks.Nested;
                import ${SoftwareType.class.name};
                import ${SoftwareTypeBindingRegistration.class.name};
                import ${BindsSoftwareType.class.name};
                import ${SoftwareTypeBindingBuilder.class.name};
                import javax.inject.Inject;

                @BindsSoftwareType(${softwareTypePluginClassName}.Binding.class)
                abstract public class ${softwareTypePluginClassName} implements Plugin<Project> {

                    static class Binding implements SoftwareTypeBindingRegistration {
                        public void register(SoftwareTypeBindingBuilder builder) {
                            builder.bindSoftwareType("${name}", ${dslTypeClassName}.class, ${dslTypeClassName}.ModelType.class, (context, definition, model) -> {
                                System.out.println("Binding " + ${dslTypeClassName}.class.getSimpleName());
                                ${conventions == null ? "" : conventions}
                                String projectName = context.getProject().getName();
                                context.getProject().getTasks().register("print${definitionImplementationTypeClassName}Configuration", DefaultTask.class, task -> {
                                    task.doLast("print restricted extension content", t -> {
                                        System.out.println(projectName + ": " + definition);
                                    });
                                });
                            })
                            ${maybeDeclareDefinitionImplementationType()};
                        }
                    }

                    @Override
                    public void apply(Project target) {
                        System.out.println("Applying " + getClass().getSimpleName());
                    }
                }
            """
        }

        String maybeDeclareDefinitionImplementationType() {
            return (definitionPublicTypeClassName && definitionPublicTypeClassName != definitionImplementationTypeClassName) ? ".withDefinitionImplementationType(${definitionImplementationTypeClassName}.class);" : ""
        }
    }

    static class ProjectPluginThatProvidesMultipleSoftwareTypesBuilder extends SoftwareTypePluginClassBuilder {
        private String anotherDefinitionImplementationTypeClassName = "AnotherSoftwareTypeExtension"

        ProjectPluginThatProvidesMultipleSoftwareTypesBuilder anotherDefinitionImplementationTypeClassName(String anotherDefinitionImplementationTypeClassName) {
            this.anotherDefinitionImplementationTypeClassName = anotherDefinitionImplementationTypeClassName
            return this
        }

        @Override
        protected String getClassContent() {
            return """
            package org.gradle.test;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;
            import org.gradle.api.tasks.Nested;
            import ${BindsSoftwareType.class.name};
            import javax.inject.Inject;

            @BindsSoftwareType(${softwareTypePluginClassName}.Binding.class)
            abstract public class ${softwareTypePluginClassName} implements Plugin<Project> {
                static class Binding implements ${SoftwareTypeBindingRegistration.class.name} {
                    public void register(${SoftwareTypeBindingBuilder.class.name} builder) {
                        builder.bindSoftwareType("testSoftwareType", ${definitionImplementationTypeClassName}.class, (context, definition, model) -> {
                            System.out.println("Binding " + ${definitionImplementationTypeClassName}.class.getSimpleName());
                            definition.getId().convention("<no id>");
                            definition.getFoo().getBar().convention("bar");
                            String projectName = context.getProject().getName();
                            context.getProject().getTasks().register("printTestSoftwareTypeExtensionConfiguration", DefaultTask.class, task -> {
                                task.doLast("print restricted extension content", t -> {
                                    System.out.println(projectName + ": " + definition);
                                });
                            });
                        });
                        builder.bindSoftwareType("anotherSoftwareType", ${anotherDefinitionImplementationTypeClassName}.class, (context, definition, model) -> {
                            System.out.println("Binding " + ${anotherDefinitionImplementationTypeClassName}.class.getSimpleName());
                            definition.getFoo().convention("foo");
                            definition.getBar().getBaz().convention("baz");
                            String projectName = context.getProject().getName();
                            context.getProject().getTasks().register("printAnotherSoftwareTypeExtensionConfiguration", DefaultTask.class, task -> {
                                task.doLast("print restricted extension content", t -> {
                                    System.out.println(projectName + ": " + definition);
                                });
                            });
                        });
                    }
                }

                @Override
                public void apply(Project target) {
                    System.out.println("Applying " + getClass().getSimpleName());
                }
            }
        """
        }
    }

    static class ProjectPluginThatDoesNotExposeSoftwareTypesBuilder extends SoftwareTypePluginClassBuilder {
        @Override
        protected String getClassContent() {
            return """
            package org.gradle.test;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;

            abstract public class ${softwareTypePluginClassName} implements Plugin<Project> {
                @Override
                public void apply(Project target) {

                }
            }
        """
        }
    }

    static class SettingsPluginClassBuilder {
        String pluginClassName = "SoftwareTypeRegistrationPlugin"
        List<String> softwareTypePluginClasses = []
        List<String> softwareFeaturePluginClasses = []

        SettingsPluginClassBuilder registersSoftwareType(String softwareTypePluginClass) {
            this.softwareTypePluginClasses.add(softwareTypePluginClass)
            return this
        }

        SettingsPluginClassBuilder registersSoftwareFeature(String softwareFeaturePluginClass) {
            this.softwareFeaturePluginClasses.add(softwareFeaturePluginClass)
            return this
        }

        void build(PluginBuilder pluginBuilder) {
            pluginBuilder.file("src/main/java/org/gradle/test/${pluginClassName}.java") << """
                package org.gradle.test;

                import org.gradle.api.DefaultTask;
                import org.gradle.api.Plugin;
                import org.gradle.api.initialization.Settings;
                import org.gradle.api.internal.SettingsInternal;
                import ${RegistersSoftwareTypes.class.name};
                import ${RegistersSoftwareFeatures.class.name};

                @RegistersSoftwareTypes({ ${softwareTypePluginClasses.collect { it + ".class" }.join(", ")} })
                @RegistersSoftwareFeatures({ ${softwareFeaturePluginClasses.collect { it + ".class" }.join(", ")} })
                abstract public class ${pluginClassName} implements Plugin<Settings> {
                    @Override
                    public void apply(Settings target) { }
                }
            """
        }
    }

    static class SettingsPluginThatConfiguresSoftwareTypeConventionsBuilder extends SettingsPluginClassBuilder {
        private String definitionImplementationTypeClassName = "TestSoftwareTypeExtension"

        SettingsPluginThatConfiguresSoftwareTypeConventionsBuilder definitionImplementationTypeClassName(String definitionImplementationTypeClassName) {
            this.definitionImplementationTypeClassName = definitionImplementationTypeClassName
            return this
        }

        @Override
        void build(PluginBuilder pluginBuilder) {
            pluginBuilder.file("src/main/java/org/gradle/test/${pluginClassName}.java") << """
                package org.gradle.test;

                import org.gradle.api.DefaultTask;
                import org.gradle.api.Plugin;
                import org.gradle.api.initialization.Settings;
                import org.gradle.api.internal.SettingsInternal;
                import org.gradle.plugin.software.internal.SoftwareTypeRegistry;
                import ${RegistersSoftwareTypes.class.name};

                @RegistersSoftwareTypes({ ${softwareFeaturePluginClasses.collect { it + ".class" }.join(", ")} })
                @RegistersSoftwareFeatures({ ${softwareFeaturePluginClasses.collect { it + ".class" }.join(", ")} })
                abstract public class ${pluginClassName} implements Plugin<Settings> {
                    @Override
                    public void apply(Settings target) {
                        ${definitionImplementationTypeClassName} convention = (${definitionImplementationTypeClassName}) target.getExtensions().getByName("testSoftwareType");
                        convention.getId().convention("plugin");
                        convention.getFoo().getBar().convention("plugin");
                    }
                }
            """
        }
    }

    static class SoftwareTypeDefinitionClassBuilder {
        String implementationTypeClassName = "TestSoftwareTypeExtension"
        String publicTypeClassName = null

        SoftwareTypeDefinitionClassBuilder implementationTypeClassName(String implementationTypeClassName) {
            this.implementationTypeClassName = implementationTypeClassName
            return this
        }

        SoftwareTypeDefinitionClassBuilder publicTypeClassName(String publicTypeClassName) {
            this.publicTypeClassName = publicTypeClassName
            return this
        }

        String getBuildModelClassName() {
            return implementationTypeClassName + ".ModelType"
        }

        void build(PluginBuilder pluginBuilder) {
            pluginBuilder.file("src/main/java/org/gradle/test/${implementationTypeClassName}.java") << getClassContent()
        }

        String getClassContent() {
            return """
                package org.gradle.test;

                import org.gradle.declarative.dsl.model.annotations.Configuring;
                import org.gradle.declarative.dsl.model.annotations.Restricted;

                import org.gradle.api.Action;
                import org.gradle.api.model.ObjectFactory;
                import org.gradle.api.provider.ListProperty;
                import org.gradle.api.provider.Property;
                import ${HasBuildModel.class.name};
                import ${BuildModel.class.name};

                import javax.inject.Inject;

                @Restricted
                public abstract class ${implementationTypeClassName} implements HasBuildModel<${implementationTypeClassName}.ModelType> ${maybeImplementsPublicType()} {
                    private final Foo foo;
                    private boolean isFooConfigured = false;

                    @Inject
                    public ${implementationTypeClassName}(ObjectFactory objects) {
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

                    public interface ModelType extends BuildModel {
                        Property<String> getId();
                    }
                }
            """
        }

        String maybeImplementsPublicType() {
            return publicTypeClassName ? "implements ${publicTypeClassName}" : ""
        }
    }

    static class SoftwareTypeDefinitionWithNdocClassBuilder extends SoftwareTypeDefinitionClassBuilder {
        @Override
        String getClassContent() {
            return """
                package org.gradle.test;

                import org.gradle.declarative.dsl.model.annotations.Restricted;

                import org.gradle.api.Named;
                import org.gradle.api.NamedDomainObjectContainer;
                import org.gradle.api.provider.Property;
                import ${HasBuildModel.class.name};
                import ${BuildModel.class.name};

                import java.util.stream.Collectors;

                public abstract class ${implementationTypeClassName} implements HasBuildModel<TestSoftwareTypeExtension.ModelType> ${maybeImplementsPublicType()} {
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

                    public interface ModelType extends BuildModel {
                        Property<String> getId();
                    }
                }

            """
        }
    }

    static class SoftwareTypeDefinitionWithPublicTypeClassBuilder extends SoftwareTypeDefinitionClassBuilder {
        SoftwareTypeDefinitionWithPublicTypeClassBuilder() {
            this.implementationTypeClassName = "TestSoftwareTypeExtensionImpl"
            this.publicTypeClassName = "TestSoftwareTypeExtension"
        }

        @Override
        String getClassContent() {
            return """
                package org.gradle.test;

                import org.gradle.declarative.dsl.model.annotations.Restricted;
                import org.gradle.api.Action;
                import org.gradle.api.model.ObjectFactory;
                import org.gradle.api.provider.ListProperty;
                import org.gradle.api.provider.Property;

                import javax.inject.Inject;

                @Restricted
                public abstract class ${implementationTypeClassName} ${maybeImplementsPublicType()} {
                    private final Foo foo;

                    @Inject
                    public ${implementationTypeClassName}(ObjectFactory objects) {
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

        String getPublicClassContent() {
            return """
                package org.gradle.test;

                import org.gradle.declarative.dsl.model.annotations.Configuring;
                import org.gradle.declarative.dsl.model.annotations.Restricted;

                import org.gradle.api.provider.Property;
                import org.gradle.api.Action;
                import ${HasBuildModel.class.name};
                import ${BuildModel.class.name};

                @Restricted
                public interface ${publicTypeClassName} extends HasBuildModel<TestSoftwareTypeExtension.ModelType> {
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

                    public interface ModelType extends BuildModel {
                        Property<String> getId();
                    }
                }
            """
        }

        @Override
        void build(PluginBuilder pluginBuilder) {
            super.build(pluginBuilder)
            pluginBuilder.file("src/main/java/org/gradle/test/${publicTypeClassName}.java") << getPublicClassContent()
        }
    }

    static class AnotherSoftwareTypeDefinitionClassBuilder extends SoftwareTypeDefinitionClassBuilder {
        AnotherSoftwareTypeDefinitionClassBuilder() {
            implementationTypeClassName = "AnotherSoftwareTypeExtension"
        }

        @Override
        String getClassContent() {
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
                import ${HasBuildModel.class.name};
                import ${BuildModel.class.name};

                import javax.inject.Inject;

                @Restricted
                public abstract class ${implementationTypeClassName} implements HasBuildModel<AnotherSoftwareTypeExtension.ModelType> ${maybeImplementsPublicType()} {
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

                    public interface ModelType extends BuildModel {
                        Property<String> getId();
                    }
                }
            """
        }
    }

    static class SoftwareTypeDefinitionWithDependenciesClassBuilder extends SoftwareTypeDefinitionClassBuilder {
        private String parentClassName = "TestSoftwareTypeExtension"

        SoftwareTypeDefinitionWithDependenciesClassBuilder() {
            this.implementationTypeClassName = "TestSoftwareTypeExtensionWithDependencies"
        }

        SoftwareTypeDefinitionWithDependenciesClassBuilder parentClassName(String parentClassName) {
            this.parentClassName = parentClassName
            return this
        }

        @Override
        String getClassContent() {
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
                public abstract class ${implementationTypeClassName} extends ${parentClassName} {
                    @Inject
                    public ${implementationTypeClassName}(ObjectFactory objects) {
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

        @Override
        void build(PluginBuilder pluginBuilder) {
            super.build(pluginBuilder)
            pluginBuilder.file("src/main/java/org/gradle/test/LibraryDependencies.java") << libraryDependencies
            new SoftwareTypeDefinitionClassBuilder().build(pluginBuilder)
        }
    }

    static String getPluginsFromIncludedBuild() {
        return """
            pluginManagement {
                includeBuild("plugins")
            }
            plugins {
                id("com.example.test-software-ecosystem")
            }
        """
    }
}
