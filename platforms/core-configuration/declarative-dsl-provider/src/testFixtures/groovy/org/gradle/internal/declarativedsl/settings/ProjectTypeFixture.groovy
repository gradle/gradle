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
import org.gradle.api.internal.plugins.BindsProjectType
import org.gradle.api.internal.plugins.BuildModel
import org.gradle.api.internal.plugins.Definition
import org.gradle.api.internal.plugins.ProjectTypeBindingBuilder
import org.gradle.api.internal.plugins.ProjectTypeBindingRegistration
import org.gradle.api.internal.plugins.software.RegistersProjectFeatures
import org.gradle.api.internal.plugins.software.RegistersSoftwareTypes
import org.gradle.api.internal.plugins.software.SoftwareType // codenarc-disable-line UnusedImport
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.plugin.PluginBuilder

@SelfType(AbstractIntegrationSpec)
trait ProjectTypeFixture {
    PluginBuilder withProjectTypePlugins(ProjectTypeDefinitionClassBuilder definitionBuilder, ProjectTypePluginClassBuilder projectTypeBuilder, SettingsPluginClassBuilder settingsBuilder) {
        def pluginBuilder = new PluginBuilder(file("plugins"))
        pluginBuilder.addPluginId("com.example.test-project-type-impl", projectTypeBuilder.projectTypePluginClassName)
        pluginBuilder.addPluginId("com.example.test-software-ecosystem", settingsBuilder.pluginClassName)

        definitionBuilder.build(pluginBuilder)
        projectTypeBuilder.build(pluginBuilder)
        settingsBuilder.build(pluginBuilder)

        return pluginBuilder
    }

    PluginBuilder withProjectTypePlugins() {
        def definition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectTypePlugins(
            definition,
            projectType,
            settingsBuilder
        )
    }

    PluginBuilder withProjectTypePluginWithNdoc() {
        def definition = new ProjectTypeDefinitionWithNdocClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder()
            .withoutConventions()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectTypePlugins(
            definition,
            projectType,
            settingsBuilder
        )
    }

    PluginBuilder withProjectTypePluginThatDoesNotExposeProjectTypes() {
        def definition = new ProjectTypeDefinitionWithNdocClassBuilder()
        def projectType = new ProjectPluginThatDoesNotExposeProjectTypesBuilder()
            .projectTypePluginClassName("NotAProjectTypePlugin")
            .withoutConventions()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectTypePlugins(
            definition,
            projectType,
            settingsBuilder
        )
    }

    PluginBuilder withSettingsPluginThatExposesMultipleProjectTypes() {
        def mainDefinition = new ProjectTypeDefinitionClassBuilder()
        def anotherDefinition = new AnotherProjectTypeDefinitionClassBuilder()
        def mainProjectType = new ProjectTypePluginClassBuilder()
        def anotherProjectType = new ProjectTypePluginClassBuilder()
            .definitionImplementationTypeClassName("AnotherProjectTypeDefinition")
            .definitionPublicTypeClassName("AnotherProjectTypeDefinition")
            .projectTypePluginClassName("AnotherProjectTypeImplPlugin")
            .withoutConventions()
            .name("anotherProjectType")
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(mainProjectType.projectTypePluginClassName)
            .registersProjectType(anotherProjectType.projectTypePluginClassName)

        PluginBuilder pluginBuilder = withProjectTypePlugins(
            mainDefinition,
            mainProjectType,
            settingsBuilder
        )

        pluginBuilder.addPluginId("com.example.another-software-type-impl", anotherProjectType.projectTypePluginClassName)
        anotherProjectType.build(pluginBuilder)
        anotherDefinition.build(pluginBuilder)

        return pluginBuilder
    }

    PluginBuilder withProjectTypePluginThatHasDifferentPublicAndImplementationModelTypes() {
        def definition = new ProjectTypeDefinitionWithPublicTypeClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder()
            .definitionPublicTypeClassName(definition.publicTypeClassName)
            .definitionImplementationTypeClassName(definition.implementationTypeClassName)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        PluginBuilder pluginBuilder = withProjectTypePlugins(
            definition,
            projectType,
            settingsBuilder
        )

        return pluginBuilder
    }

    PluginBuilder withProjectTypePluginThatExposesMultipleProjectTypes() {
        def definition = new ProjectTypeDefinitionClassBuilder()
        def anotherProjectTypeDefinition = new AnotherProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectPluginThatProvidesMultipleProjectTypesBuilder()
            .anotherDefinitionImplementationTypeClassName(anotherProjectTypeDefinition.implementationTypeClassName)
            .definitionImplementationTypeClassName(definition.implementationTypeClassName)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        PluginBuilder pluginBuilder = withProjectTypePlugins(
            definition,
            projectType,
            settingsBuilder
        )

        anotherProjectTypeDefinition.build(pluginBuilder)

        return pluginBuilder
    }

    PluginBuilder withProjectTypePluginThatExposesExtensionWithDependencies() {
        def definitionWithClasses = new ProjectTypeDefinitionWithDependenciesClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder()
            .definitionImplementationTypeClassName(definitionWithClasses.implementationTypeClassName)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        PluginBuilder pluginBuilder = withProjectTypePlugins(
            definitionWithClasses,
            projectType,
            settingsBuilder
        )

        return pluginBuilder
    }

    PluginBuilder withSettingsPluginThatConfiguresModelDefaults() {
        def definition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder()
        def settingsBuilder = new SettingsPluginThatConfiguresProjectTypeConventionsBuilder()
            .definitionImplementationTypeClassName(definition.implementationTypeClassName)
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectTypePlugins(
            definition,
            projectType,
            settingsBuilder
        )
    }

    static class ProjectTypePluginClassBuilder {
        String definitionImplementationTypeClassName = "TestProjectTypeDefinition"
        String definitionPublicTypeClassName = null
        String projectTypePluginClassName = "ProjectTypeImplPlugin"
        String name = "testProjectType"
        String conventions = """
            definition.getId().convention("<no id>");
            definition.getFoo().getBar().convention("bar");
        """
        String applyActionExtraStatements = ""

        ProjectTypePluginClassBuilder definitionImplementationTypeClassName(String implementationTypeClassName) {
            this.definitionImplementationTypeClassName = implementationTypeClassName
            return this
        }

        ProjectTypePluginClassBuilder definitionPublicTypeClassName(String publicTypeClassName) {
            this.definitionPublicTypeClassName = publicTypeClassName
            return this
        }

        ProjectTypePluginClassBuilder projectTypePluginClassName(String projectTypePluginClassName) {
            this.projectTypePluginClassName = projectTypePluginClassName
            return this
        }

        ProjectTypePluginClassBuilder name(String name) {
            this.name = name
            return this
        }

        ProjectTypePluginClassBuilder conventions(String conventions) {
            this.conventions = conventions
            return this
        }

        ProjectTypePluginClassBuilder withoutConventions() {
            this.conventions = null
            return this
        }

        ProjectTypePluginClassBuilder applyActionExtraStatements(String statements) {
            this.applyActionExtraStatements = statements
            return this
        }

        void build(PluginBuilder pluginBuilder) {
            pluginBuilder.file("src/main/java/org/gradle/test/${projectTypePluginClassName}.java") << getClassContent()
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
                import ${ProjectTypeBindingRegistration.class.name};
                import ${BindsProjectType.class.name};
                import ${ProjectTypeBindingBuilder.class.name};
                import javax.inject.Inject;

                @${BindsProjectType.class.simpleName}(${projectTypePluginClassName}.Binding.class)
                abstract public class ${projectTypePluginClassName} implements Plugin<Project> {

                    static class Binding implements ProjectTypeBindingRegistration {
                        public void register(ProjectTypeBindingBuilder builder) {
                            builder.bindProjectType("${name}", ${dslTypeClassName}.class, ${dslTypeClassName}.ModelType.class, (context, definition, model) -> {
                                System.out.println("Binding " + ${dslTypeClassName}.class.getSimpleName());
                                ${conventions == null ? "" : conventions}
                                String projectName = context.getProject().getName();

                                $applyActionExtraStatements

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

    static class ProjectPluginThatProvidesMultipleProjectTypesBuilder extends ProjectTypePluginClassBuilder {
        private String anotherDefinitionImplementationTypeClassName = "AnotherProjectTypeDefinition"

        ProjectPluginThatProvidesMultipleProjectTypesBuilder anotherDefinitionImplementationTypeClassName(String anotherDefinitionImplementationTypeClassName) {
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
            import ${BindsProjectType.class.name};
            import javax.inject.Inject;

            @${BindsProjectType.class.simpleName}(${projectTypePluginClassName}.Binding.class)
            abstract public class ${projectTypePluginClassName} implements Plugin<Project> {
                static class Binding implements ${ProjectTypeBindingRegistration.class.name} {
                    public void register(${ProjectTypeBindingBuilder.class.name} builder) {
                        builder.bindProjectType("testProjectType", ${definitionImplementationTypeClassName}.class, (context, definition, model) -> {
                            System.out.println("Binding " + ${definitionImplementationTypeClassName}.class.getSimpleName());
                            definition.getId().convention("<no id>");
                            definition.getFoo().getBar().convention("bar");
                            String projectName = context.getProject().getName();
                            context.getProject().getTasks().register("printTestProjectTypeDefinitionConfiguration", DefaultTask.class, task -> {
                                task.doLast("print restricted extension content", t -> {
                                    System.out.println(projectName + ": " + definition);
                                });
                            });
                        });
                        builder.bindProjectType("anotherProjectType", ${anotherDefinitionImplementationTypeClassName}.class, (context, definition, model) -> {
                            System.out.println("Binding " + ${anotherDefinitionImplementationTypeClassName}.class.getSimpleName());
                            definition.getFoo().convention("foo");
                            definition.getBar().getBaz().convention("baz");
                            String projectName = context.getProject().getName();
                            context.getProject().getTasks().register("printAnotherProjectTypeDefinitionConfiguration", DefaultTask.class, task -> {
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

    static class ProjectPluginThatDoesNotExposeProjectTypesBuilder extends ProjectTypePluginClassBuilder {
        @Override
        protected String getClassContent() {
            return """
            package org.gradle.test;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;

            abstract public class ${projectTypePluginClassName} implements Plugin<Project> {
                @Override
                public void apply(Project target) {

                }
            }
        """
        }
    }

    static class SettingsPluginClassBuilder {
        String pluginClassName = "ProjectTypeRegistrationPlugin"
        List<String> projectTypePluginClasses = []
        List<String> projectFeaturePluginClasses = []

        SettingsPluginClassBuilder registersProjectType(String projectTypePluginClass) {
            this.projectTypePluginClasses.add(projectTypePluginClass)
            return this
        }

        SettingsPluginClassBuilder registersProjectFeature(String projectFeaturePluginClass) {
            this.projectFeaturePluginClasses.add(projectFeaturePluginClass)
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
                import ${RegistersProjectFeatures.class.name};

                @RegistersSoftwareTypes({ ${projectTypePluginClasses.collect { it + ".class" }.join(", ")} })
                @${RegistersProjectFeatures.class.simpleName}({ ${projectFeaturePluginClasses.collect { it + ".class" }.join(", ")} })
                abstract public class ${pluginClassName} implements Plugin<Settings> {
                    @Override
                    public void apply(Settings target) { }
                }
            """
        }
    }

    static class SettingsPluginThatConfiguresProjectTypeConventionsBuilder extends SettingsPluginClassBuilder {
        private String definitionImplementationTypeClassName = "TestProjectTypeDefinition"

        SettingsPluginThatConfiguresProjectTypeConventionsBuilder definitionImplementationTypeClassName(String definitionImplementationTypeClassName) {
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
                import org.gradle.plugin.software.internal.ProjectFeatureRegistry;
                import ${RegistersSoftwareTypes.class.name};
                import ${RegistersProjectFeatures.class.name};

                @RegistersSoftwareTypes({ ${projectFeaturePluginClasses.collect { it + ".class" }.join(", ")} })
                @${RegistersProjectFeatures.class.simpleName}({ ${projectFeaturePluginClasses.collect { it + ".class" }.join(", ")} })
                abstract public class ${pluginClassName} implements Plugin<Settings> {
                    @Override
                    public void apply(Settings target) {
                        ${definitionImplementationTypeClassName} convention = (${definitionImplementationTypeClassName}) target.getExtensions().getByName("testProjectType");
                        convention.getId().convention("plugin");
                        convention.getFoo().getBar().convention("plugin");
                    }
                }
            """
        }
    }

    static class ProjectTypeDefinitionClassBuilder {
        String implementationTypeClassName = "TestProjectTypeDefinition"
        String publicTypeClassName = null

        ProjectTypeDefinitionClassBuilder implementationTypeClassName(String implementationTypeClassName) {
            this.implementationTypeClassName = implementationTypeClassName
            return this
        }

        ProjectTypeDefinitionClassBuilder publicTypeClassName(String publicTypeClassName) {
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
                import ${Definition.class.name};
                import ${BuildModel.class.name};

                import javax.inject.Inject;

                @Restricted
                public abstract class ${implementationTypeClassName} implements ${Definition.class.simpleName}<${implementationTypeClassName}.ModelType> ${maybeImplementsPublicType()} {
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

                    public abstract static class Foo implements ${Definition.class.simpleName}<FooBuildModel> {
                        public Foo() { }

                        @Restricted
                        public abstract Property<String> getBar();
                    }

                    public interface FooBuildModel extends BuildModel {
                        Property<String> getBarProcessed();
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

    static class ProjectTypeDefinitionWithNdocClassBuilder extends ProjectTypeDefinitionClassBuilder {
        @Override
        String getClassContent() {
            return """
                package org.gradle.test;

                import org.gradle.declarative.dsl.model.annotations.Restricted;

                import org.gradle.api.Named;
                import org.gradle.api.NamedDomainObjectContainer;
                import org.gradle.api.provider.Property;
                import ${Definition.class.name};
                import ${BuildModel.class.name};

                import java.util.stream.Collectors;

                public abstract class ${implementationTypeClassName} implements ${Definition.class.simpleName}<${implementationTypeClassName}.ModelType> ${maybeImplementsPublicType()} {
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

    static class ProjectTypeDefinitionWithPublicTypeClassBuilder extends ProjectTypeDefinitionClassBuilder {
        ProjectTypeDefinitionWithPublicTypeClassBuilder() {
            this.implementationTypeClassName = "TestProjectTypeDefinitionImpl"
            this.publicTypeClassName = "TestProjectTypeDefinition"
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
                import ${Definition.class.name};
                import ${BuildModel.class.name};

                @Restricted
                public interface ${publicTypeClassName} extends ${Definition.class.simpleName}<${publicTypeClassName}.ModelType> {
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

    static class AnotherProjectTypeDefinitionClassBuilder extends ProjectTypeDefinitionClassBuilder {
        AnotherProjectTypeDefinitionClassBuilder() {
            implementationTypeClassName = "AnotherProjectTypeDefinition"
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
                import ${Definition.class.name};
                import ${BuildModel.class.name};

                import javax.inject.Inject;

                @Restricted
                public abstract class ${implementationTypeClassName} implements ${Definition.class.simpleName}<${implementationTypeClassName}.ModelType> ${maybeImplementsPublicType()} {
                    @Inject
                    public ${implementationTypeClassName}() { }

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

    static class ProjectTypeDefinitionWithDependenciesClassBuilder extends ProjectTypeDefinitionClassBuilder {
        private String parentClassName = "TestProjectTypeDefinition"

        ProjectTypeDefinitionWithDependenciesClassBuilder() {
            this.implementationTypeClassName = "TestProjectTypeDefinitionWithDependencies"
        }

        ProjectTypeDefinitionWithDependenciesClassBuilder parentClassName(String parentClassName) {
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
            new ProjectTypeDefinitionClassBuilder().build(pluginBuilder)
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
