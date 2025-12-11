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
import org.gradle.api.internal.plugins.ProjectTypeBinding
import org.gradle.api.internal.plugins.software.RegistersProjectFeatures
import org.gradle.api.internal.plugins.software.RegistersSoftwareTypes
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.plugin.PluginBuilder

@SelfType(AbstractIntegrationSpec)
trait ProjectTypeFixture {
    PluginBuilder withProjectType(ProjectTypeDefinitionClassBuilder definitionBuilder, ProjectTypePluginClassBuilder projectTypeBuilder, SettingsPluginClassBuilder settingsBuilder) {
        def pluginBuilder = new PluginBuilder(file("plugins"))
        pluginBuilder.addPluginId("com.example.test-project-type-impl", projectTypeBuilder.projectTypePluginClassName)
        pluginBuilder.addPluginId("com.example.test-software-ecosystem", settingsBuilder.pluginClassName)

        definitionBuilder.build(pluginBuilder)
        projectTypeBuilder.build(pluginBuilder)
        settingsBuilder.build(pluginBuilder)

        return pluginBuilder
    }

    PluginBuilder withProjectType() {
        def definition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectType(
            definition,
            projectType,
            settingsBuilder
        )
    }

    PluginBuilder withProjectTypeWithNdoc() {
        def definition = new ProjectTypeDefinitionWithNdocClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder()
            .withoutConventions()
            .withUnsafeDefinition()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectType(
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

        return withProjectType(
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

        PluginBuilder pluginBuilder = withProjectType(
            mainDefinition,
            mainProjectType,
            settingsBuilder
        )

        pluginBuilder.addPluginId("com.example.another-software-type-impl", anotherProjectType.projectTypePluginClassName)
        anotherProjectType.build(pluginBuilder)
        anotherDefinition.build(pluginBuilder)

        return pluginBuilder
    }

    PluginBuilder withTwoProjectTypesThatHaveTheSameName() {
        def mainDefinition = new ProjectTypeDefinitionClassBuilder()
        def anotherDefinition = new AnotherProjectTypeDefinitionClassBuilder()
        def mainProjectType = new ProjectTypePluginClassBuilder()
        def anotherProjectType = new ProjectTypePluginClassBuilder()
            .definitionImplementationTypeClassName("AnotherProjectTypeDefinition")
            .definitionPublicTypeClassName("AnotherProjectTypeDefinition")
            .projectTypePluginClassName("AnotherProjectTypeImplPlugin")
            .withoutConventions()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(mainProjectType.projectTypePluginClassName)
            .registersProjectType(anotherProjectType.projectTypePluginClassName)

        PluginBuilder pluginBuilder = withProjectType(
            mainDefinition,
            mainProjectType,
            settingsBuilder
        )

        pluginBuilder.addPluginId("com.example.another-software-type-impl", anotherProjectType.projectTypePluginClassName)
        anotherProjectType.build(pluginBuilder)
        anotherDefinition.build(pluginBuilder)

        return pluginBuilder
    }

    PluginBuilder withProjectTypeThatHasDifferentPublicAndImplementationModelTypes() {
        def definition = new ProjectTypeDefinitionWithImplementationTypeClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder()
            .definitionPublicTypeClassName(definition.publicTypeClassName)
            .definitionImplementationTypeClassName(definition.defaultClassName)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        PluginBuilder pluginBuilder = withProjectType(
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
            .anotherDefinitionImplementationTypeClassName(anotherProjectTypeDefinition.defaultClassName)
            .definitionImplementationTypeClassName(definition.defaultClassName)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        PluginBuilder pluginBuilder = withProjectType(
            definition,
            projectType,
            settingsBuilder
        )

        anotherProjectTypeDefinition.build(pluginBuilder)

        return pluginBuilder
    }

    PluginBuilder withProjectTypeDefinitionWithDependencies() {
        def definitionWithClasses = new ProjectTypeDefinitionWithDependenciesClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder()
            .definitionImplementationTypeClassName(definitionWithClasses.defaultClassName)
            .withUnsafeDefinition()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        PluginBuilder pluginBuilder = withProjectType(
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
            .definitionImplementationTypeClassName(definition.defaultClassName)
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectType(
            definition,
            projectType,
            settingsBuilder
        )
    }

    PluginBuilder withUnsafeProjectTypeDefinitionDeclaredSafe() {
        def definition = new ProjectTypeDefinitionAbstractClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectType(
            definition,
            projectType,
            settingsBuilder
        )
    }

    PluginBuilder withUnsafeProjectTypeDefinitionDeclaredUnsafe() {
        def definition = new ProjectTypeDefinitionAbstractClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder().withUnsafeDefinition()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectType(
            definition,
            projectType,
            settingsBuilder
        )
    }

    PluginBuilder withSafeProjectTypeAndInjectableDefinition() {
        def definition = new ProjectTypeDefinitionClassBuilder().withInjectedServices()
        def projectType = new ProjectTypePluginClassBuilder()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectType(
            definition,
            projectType,
            settingsBuilder
        )
    }

    PluginBuilder withSafeProjectTypeAndNestedInjectableDefinition() {
        def definition = new ProjectTypeDefinitionClassBuilder().withNestedInjectedServices()
        def projectType = new ProjectTypePluginClassBuilder()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectType(
            definition,
            projectType,
            settingsBuilder
        )
    }

    PluginBuilder withSafeProjectTypeAndMultipleInjectableDefinition() {
        def definition = new ProjectTypeDefinitionClassBuilder().withInjectedServices().withNestedInjectedServices()
        def projectType = new ProjectTypePluginClassBuilder()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectType(
            definition,
            projectType,
            settingsBuilder
        )
    }

    PluginBuilder withSafeProjectTypeAndInheritedInjectableDefinition() {
        def definition = new ProjectTypeDefinitionWithInjectableParentClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectType(
            definition,
            projectType,
            settingsBuilder
        )
    }

    PluginBuilder withPolyUnsafeProjectTypeDefinitionDeclaredSafe() {
        def definition = new ProjectTypeDefinitionAbstractClassBuilder().withInjectedServices()
        def projectType = new ProjectTypePluginClassBuilder()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectType(
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
        List<String> bindingModifiers = []

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

        ProjectTypePluginClassBuilder withUnsafeDefinition() {
            this.bindingModifiers.add("withUnsafeDefinition()")
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
                import ${ProjectTypeBinding.class.name};
                import ${BindsProjectType.class.name};
                import ${ProjectTypeBindingBuilder.class.name};
                import javax.inject.Inject;

                @${BindsProjectType.class.simpleName}(${projectTypePluginClassName}.Binding.class)
                abstract public class ${projectTypePluginClassName} implements Plugin<Project> {

                    static class Binding implements ${ProjectTypeBinding.class.simpleName} {
                        public void bind(${ProjectTypeBindingBuilder.class.simpleName} builder) {
                            builder.bindProjectType("${name}", ${dslTypeClassName}.class, (context, definition, model) -> {
                                System.out.println("Binding " + ${dslTypeClassName}.class.getSimpleName());
                                ${conventions == null ? "" : conventions}
                                String projectName = context.getProject().getName();

                                $applyActionExtraStatements

                                context.getProject().getTasks().register("print${definitionImplementationTypeClassName}Configuration", DefaultTask.class, task -> {
                                    task.doLast("print restricted extension content", t -> {
                                        System.out.println(projectName + ": " + definition.propertyValues());
                                    });
                                });
                            })
                            ${maybeDeclareDefinitionImplementationType()}
                            ${maybeDeclareBindingModifiers()};
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
            return (definitionPublicTypeClassName && definitionPublicTypeClassName != definitionImplementationTypeClassName) ? ".withUnsafeDefinitionImplementationType(${definitionImplementationTypeClassName}.class)" : ""
        }

        String maybeDeclareBindingModifiers() {
            return bindingModifiers.isEmpty() ? "" : bindingModifiers.collect { ".${it}" }.join("")
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
                static class Binding implements ${ProjectTypeBinding.class.name} {
                    public void bind(${ProjectTypeBindingBuilder.class.name} builder) {
                        builder.bindProjectType("testProjectType", ${definitionImplementationTypeClassName}.class, (context, definition, model) -> {
                            System.out.println("Binding " + ${definitionImplementationTypeClassName}.class.getSimpleName());
                            definition.getId().convention("<no id>");
                            definition.getFoo().getBar().convention("bar");
                            String projectName = context.getProject().getName();
                            context.getProject().getTasks().register("printTestProjectTypeDefinitionConfiguration", DefaultTask.class, task -> {
                                task.doLast("print restricted extension content", t -> {
                                    System.out.println(projectName + ": " + definition.propertyValues());
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
                                    System.out.println(projectName + ": " + definition.propertyValues());
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
        String defaultClassName = "TestProjectTypeDefinition"

        boolean hasInjectedServices = false
        boolean hasNestedInjectedServices = false

        String getBuildModelClassName() {
            return defaultClassName + ".ModelType"
        }

        void build(PluginBuilder pluginBuilder) {
            pluginBuilder.file("src/main/java/org/gradle/test/${defaultClassName}.java") << getClassContent()
        }

        ProjectTypeDefinitionClassBuilder withInjectedServices() {
            this.hasInjectedServices = true
            return this
        }

        ProjectTypeDefinitionClassBuilder withNestedInjectedServices() {
            this.hasNestedInjectedServices = true
            return this
        }

        String getClassContent() {
            return defaultClassContent(defaultClassName)
        }

        String defaultClassContent(String effectiveClassName) {
            return """
                package org.gradle.test;

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
                public interface ${effectiveClassName} extends ${Definition.class.simpleName}<${effectiveClassName}.ModelType> {
                    @Restricted
                    abstract Property<String> getId();

                    @Nested
                    Foo getFoo();

                    @Configuring
                    default void foo(Action<? super Foo> action) {
                        action.execute(getFoo());
                    }

                    ${maybeInjectedServiceDeclaration}

                    interface Foo extends ${Definition.class.simpleName}<FooBuildModel> {
                        @Restricted
                        public abstract Property<String> getBar();

                        ${maybeNestedInjectedServiceDeclaration}
                    }

                    interface FooBuildModel extends BuildModel {
                        Property<String> getBarProcessed();
                    }

                    default String propertyValues() {
                        return "id = " + getId().get() + "\\nbar = " + getFoo().getBar().get();
                    }

                    interface ModelType extends BuildModel {
                        Property<String> getId();
                    }
                }
            """
        }

        String getMaybeInjectedServiceDeclaration() {
            return hasInjectedServices ? """
                @Inject
                ObjectFactory getObjects();
            """ : ""
        }

        String getMaybeNestedInjectedServiceDeclaration() {
            return hasNestedInjectedServices ? """
                @Inject
                ObjectFactory getObjects();
            """ : ""
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

                public abstract class ${defaultClassName} implements ${Definition.class.simpleName}<${defaultClassName}.ModelType> {
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

                    public String propertyValues() {
                        return getFoos().stream().map(Foo::toString).collect(Collectors.joining(", "));
                    }

                    public interface ModelType extends BuildModel {
                        Property<String> getId();
                    }
                }

            """
        }
    }

    static class ProjectTypeDefinitionWithImplementationTypeClassBuilder extends ProjectTypeDefinitionClassBuilder {
        String publicTypeClassName = "PublicTestProjectTypeDefinition"

        ProjectTypeDefinitionWithImplementationTypeClassBuilder() {
            this.defaultClassName = "TestProjectTypeDefinitionImpl"
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
                public abstract class ${defaultClassName} implements ${publicTypeClassName} {
                    private final Foo foo;

                    @Inject
                    public ${defaultClassName}(ObjectFactory objects) {
                        this.foo = objects.newInstance(Foo.class);
                    }

                    @Override
                    public Foo getFoo() {
                        return foo;
                    }

                    @Restricted
                    public abstract Property<String> getNonPublic();

                    public String propertyValues() {
                        return "id = " + getId().get() + "\\nbar = " + getFoo().getBar().get();
                    }
                }
            """
        }

        String getPublicClassContent() {
            super.defaultClassContent(publicTypeClassName)
        }

        @Override
        void build(PluginBuilder pluginBuilder) {
            super.build(pluginBuilder)
            pluginBuilder.file("src/main/java/org/gradle/test/${publicTypeClassName}.java") << getPublicClassContent()
        }
    }

    static class AnotherProjectTypeDefinitionClassBuilder extends ProjectTypeDefinitionClassBuilder {
        AnotherProjectTypeDefinitionClassBuilder() {
            defaultClassName = "AnotherProjectTypeDefinition"
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
                public interface ${defaultClassName} extends ${Definition.class.simpleName}<${defaultClassName}.ModelType> {

                    @Restricted
                    Property<String> getFoo();

                    @Nested
                    Bar getBar();

                    @Configuring
                    default void bar(Action<? super Bar> action) {
                        action.execute(getBar());
                    }

                    abstract interface Bar {
                        @Restricted
                        Property<String> getBaz();
                    }

                    default String propertyValues() {
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
        private String interfaceName = "TestProjectTypeDefinition"

        ProjectTypeDefinitionWithDependenciesClassBuilder() {
            this.defaultClassName = "TestProjectTypeDefinitionWithDependencies"
        }

        ProjectTypeDefinitionWithDependenciesClassBuilder parentClassName(String parentClassName) {
            this.interfaceName = parentClassName
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
                public abstract class ${defaultClassName} implements ${interfaceName} {
                    private boolean isBarConfigured = false;

                    @Inject
                    public ${defaultClassName}() { }

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
                        isBarConfigured = true;
                        action.execute(getBar());
                    }

                    public abstract static class Bar {

                        public abstract ListProperty<String> getBaz();

                        @Adding
                        public void addToBaz(String value) {
                            getBaz().add(value);
                        }
                    }

                    public String propertyValues() {
                        return ${interfaceName}.super.propertyValues() +
                            "\\nlist = " + printList(getList().get()) +
                            "\\nbaz = " + printList(getBar().getBaz().get()) +
                            "\\napi = " + printDependencies(getDependencies().getApi()) +
                            "\\nimplementation = " + printDependencies(getDependencies().getImplementation()) +
                            "\\nruntimeOnly = " + printDependencies(getDependencies().getRuntimeOnly()) +
                            "\\ncompileOnly = " + printDependencies(getDependencies().getCompileOnly()) +
                            (isBarConfigured ? "\\n(bar is configured)" : "");
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

    static class ProjectTypeDefinitionAbstractClassBuilder extends ProjectTypeDefinitionClassBuilder {
        @Override
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
                public abstract class ${defaultClassName} implements ${Definition.class.simpleName}<${defaultClassName}.ModelType> {
                    private final Foo foo;
                    private boolean isFooConfigured = false;

                    @Inject
                    public ${defaultClassName}(ObjectFactory objects) {
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

                    ${maybeInjectedServiceDeclaration}

                    public abstract static class Foo implements ${Definition.class.simpleName}<FooBuildModel> {
                        public Foo() { }

                        ${maybeNestedInjectedServiceDeclaration}

                        @Restricted
                        public abstract Property<String> getBar();
                    }

                    public interface FooBuildModel extends BuildModel {
                        Property<String> getBarProcessed();
                    }

                    public String propertyValues() {
                        return "id = " + getId().get() + "\\nbar = " + getFoo().getBar().get() + (isFooConfigured ? "\\n(foo is configured)" : "");
                    }

                    public interface ModelType extends BuildModel {
                        Property<String> getId();
                    }
                }
            """
        }

        @Override
        String getMaybeInjectedServiceDeclaration() {
            return hasInjectedServices ? """
                @Inject
                abstract ObjectFactory getObjects();
            """ : ""
        }

        @Override
        String getMaybeNestedInjectedServiceDeclaration() {
            return hasNestedInjectedServices ? """
                @Inject
                abstract ObjectFactory getObjects();
            """ : ""
        }
    }

    static class ProjectTypeDefinitionWithInjectableParentClassBuilder extends ProjectTypeDefinitionClassBuilder {
        String parentTypeClassName = "ParentTestProjectTypeDefinition"

        ProjectTypeDefinitionWithInjectableParentClassBuilder() {
            // Adds injected services to the parent
            withInjectedServices()
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
                public interface ${defaultClassName} extends ${parentTypeClassName} { }
            """
        }

        String getPublicClassContent() {
            super.defaultClassContent(parentTypeClassName)
        }

        @Override
        void build(PluginBuilder pluginBuilder) {
            super.build(pluginBuilder)
            pluginBuilder.file("src/main/java/org/gradle/test/${parentTypeClassName}.java") << getPublicClassContent()
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
