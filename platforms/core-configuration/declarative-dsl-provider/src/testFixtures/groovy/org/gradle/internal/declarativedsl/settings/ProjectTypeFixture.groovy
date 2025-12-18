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
import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.declarative.dsl.model.annotations.Configuring
import org.gradle.declarative.dsl.model.annotations.Restricted
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
        def projectType = new ProjectTypePluginClassBuilder(definition)
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
        def projectType = new ProjectTypePluginClassBuilder(definition)
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
        def projectType = new ProjectPluginThatDoesNotExposeProjectTypesBuilder(definition)
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
        def mainProjectType = new ProjectTypePluginClassBuilder(mainDefinition)
        def anotherProjectType = new ProjectTypePluginClassBuilder(anotherDefinition)
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
        def mainProjectType = new ProjectTypePluginClassBuilder(mainDefinition)
        def anotherProjectType = new ProjectTypePluginClassBuilder(anotherDefinition)
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

    PluginBuilder withProjectTypeThatHasDifferentPublicAndImplementationTypes() {
        def definition = new ProjectTypeDefinitionWithPublicAndImplementationTypesClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(definition)
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
        def projectType = new ProjectPluginThatProvidesMultipleProjectTypesBuilder(definition, anotherProjectTypeDefinition)
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
        def projectType = new ProjectTypePluginClassBuilder(definitionWithClasses)
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
        def projectType = new ProjectTypePluginClassBuilder(definition)
        def settingsBuilder = new SettingsPluginThatConfiguresProjectTypeConventionsBuilder()
            .definitionImplementationTypeClassName(definition.publicTypeClassName)
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectType(
            definition,
            projectType,
            settingsBuilder
        )
    }

    PluginBuilder withUnsafeProjectTypeDefinitionDeclaredSafe() {
        def definition = new ProjectTypeDefinitionAbstractClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(definition)
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
        def projectType = new ProjectTypePluginClassBuilder(definition).withUnsafeDefinition()
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
        def projectType = new ProjectTypePluginClassBuilder(definition)
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
        def projectType = new ProjectTypePluginClassBuilder(definition)
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
        def projectType = new ProjectTypePluginClassBuilder(definition)
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
        def projectType = new ProjectTypePluginClassBuilder(definition)
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
        def projectType = new ProjectTypePluginClassBuilder(definition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectType(
            definition,
            projectType,
            settingsBuilder
        )
    }

    static class ProjectTypePluginClassBuilder {
        final ProjectTypeDefinitionClassBuilder definition
        String projectTypePluginClassName = "ProjectTypeImplPlugin"
        String name = "testProjectType"
        String conventions = """
            definition.getId().convention("<no id>");
            definition.getFoo().getBar().convention("bar");
        """

        ProjectTypePluginClassBuilder(ProjectTypeDefinitionClassBuilder definition) {
            this.definition = definition
        }

        List<String> bindingModifiers = []

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

        ProjectTypePluginClassBuilder withUnsafeDefinition() {
            this.bindingModifiers.add("withUnsafeDefinition()")
            return this
        }

        void build(PluginBuilder pluginBuilder) {
            pluginBuilder.file("src/main/java/org/gradle/test/${projectTypePluginClassName}.java") << getClassContent()
        }

        protected String getClassContent() {
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
                            builder.bindProjectType("${name}", ${definition.publicTypeClassName}.class, (context, definition, model) -> {
                                System.out.println("Binding " + ${definition.publicTypeClassName}.class.getSimpleName());
                                ${conventions == null ? "" : conventions}
                                String projectName = context.getProject().getName();

                                ${definition.buildModelMapping}

                                context.getProject().getTasks().register("print${definition.publicTypeClassName}Configuration", DefaultTask.class, task -> {
                                    task.doLast("print restricted extension content", t -> {
                                        ${definition.displayDefinitionPropertyValues()}
                                        ${definition.displayModelPropertyValues()}
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
            return definition.hasImplementationType ? ".withUnsafeDefinitionImplementationType(${definition.implementationTypeClassName}.class)" : ""
        }

        String maybeDeclareBindingModifiers() {
            return bindingModifiers.isEmpty() ? "" : bindingModifiers.collect { ".${it}" }.join("")
        }
    }

    static class ProjectPluginThatProvidesMultipleProjectTypesBuilder extends ProjectTypePluginClassBuilder {
        final private ProjectTypeDefinitionClassBuilder anotherProjectTypeDefinition

        ProjectPluginThatProvidesMultipleProjectTypesBuilder(ProjectTypeDefinitionClassBuilder definition, ProjectTypeDefinitionClassBuilder anotherDefinition) {
            super(definition)
            this.anotherProjectTypeDefinition = anotherDefinition
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
                            builder.bindProjectType("testProjectType", ${definition.publicTypeClassName}.class, (context, definition, model) -> {
                                System.out.println("Binding " + ${definition.publicTypeClassName}.class.getSimpleName());
                                definition.getId().convention("<no id>");
                                definition.getFoo().getBar().convention("bar");
                                model.getId().set(definition.getId());
                                String projectName = context.getProject().getName();
                                context.getProject().getTasks().register("printTestProjectTypeDefinitionConfiguration", DefaultTask.class, task -> {
                                    task.doLast("print restricted extension content", t -> {
                                        ${definition.displayDefinitionPropertyValues()}
                                        ${definition.displayModelPropertyValues()}
                                    });
                                });
                            });
                            builder.bindProjectType("anotherProjectType", ${anotherProjectTypeDefinition.publicTypeClassName}.class, (context, definition, model) -> {
                                System.out.println("Binding " + ${anotherProjectTypeDefinition.publicTypeClassName}.class.getSimpleName());
                                definition.getFoo().convention("foo");
                                definition.getBar().getBaz().convention("baz");
                                model.getId().set(definition.getId());
                                String projectName = context.getProject().getName();
                                context.getProject().getTasks().register("printAnotherProjectTypeDefinitionConfiguration", DefaultTask.class, task -> {
                                    task.doLast("print restricted extension content", t -> {
                                        ${anotherProjectTypeDefinition.displayDefinitionPropertyValues()}
                                        ${anotherProjectTypeDefinition.displayModelPropertyValues()}
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
        ProjectPluginThatDoesNotExposeProjectTypesBuilder(ProjectTypeDefinitionClassBuilder definition) {
            super(definition)
        }

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
        String publicTypeClassName = "TestProjectTypeDefinition"
        String implementationTypeClassName = "TestProjectTypeDefinitionImpl"

        boolean hasImplementationType = false
        boolean hasInjectedServices = false
        boolean hasNestedInjectedServices = false

        String getBuildModelClassName() {
            return publicTypeClassName + ".ModelType"
        }

        void build(PluginBuilder pluginBuilder) {
            pluginBuilder.file("src/main/java/org/gradle/test/${publicTypeClassName}.java") << getPublicTypeClassContent()
            if (hasImplementationType) {
                pluginBuilder.file("src/main/java/org/gradle/test/${implementationTypeClassName}.java") << getImplementationTypeClassContent()
            }
        }

        ProjectTypeDefinitionClassBuilder withInjectedServices() {
            this.hasInjectedServices = true
            return this
        }

        ProjectTypeDefinitionClassBuilder withNestedInjectedServices() {
            this.hasNestedInjectedServices = true
            return this
        }

        String getFullyQualifiedPublicTypeClassName() {
            return "org.gradle.test." + publicTypeClassName
        }

        String getPublicTypeClassContent() {
            return defaultClassContent(publicTypeClassName)
        }

        String getImplementationTypeClassContent() {
            return null
        }

        String defaultClassContent(String effectiveClassName) {
            return """
                package org.gradle.test;

                import ${Configuring.class.name};
                import ${Restricted.class.name};

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
                    @${Restricted.class.simpleName}
                    Property<String> getId();

                    @Nested
                    Foo getFoo();

                    @${Configuring.class.simpleName}
                    default void foo(Action<? super Foo> action) {
                        action.execute(getFoo());
                    }

                    ${maybeInjectedServiceDeclaration}

                    interface Foo extends ${Definition.class.simpleName}<FooBuildModel> {
                        @${Restricted.class.simpleName}
                        public abstract Property<String> getBar();

                        ${maybeNestedInjectedServiceDeclaration}
                    }

                    interface FooBuildModel extends BuildModel {
                        Property<String> getBarProcessed();
                    }

                    interface ModelType extends BuildModel {
                        Property<String> getId();
                    }
                }
            """
        }

        String getBuildModelMapping() {
            return """
                    model.getId().set(definition.getId());
                """
        }

        String displayDefinitionPropertyValues() {
            return """
                ${displayProperty("definition", "id", "definition.getId().get()")}
                ${displayProperty("definition", "foo.bar", "definition.getFoo().getBar().get()")}
            """
        }

        String displayModelPropertyValues() {
            return """
                ${displayProperty("model", "id", "model.getId().get()")}
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

        static String displayProperty(String objectType, String propertyName, String propertyValueExpression) {
            // Note that this assumes that "projectName" variable has been set in some outer scope in order to avoid
            // accessing the project object at execution time.
            return """
                System.out.println(projectName + ": ${objectType} ${propertyName} = " + ${propertyValueExpression});
            """
        }
    }

    static class ProjectTypeDefinitionThatRegistersANestedBindingLocationClassBuilder extends ProjectTypeDefinitionClassBuilder {
        @Override
        String getBuildModelMapping() {
            return """
                    context.registerBuildModel(definition.getFoo())
                        .getBarProcessed().set(definition.getFoo().getBar().map(it -> it.toUpperCase()));
                """
        }
    }

    static class ProjectTypeDefinitionWithNdocClassBuilder extends ProjectTypeDefinitionClassBuilder {
        @Override
        String getPublicTypeClassContent() {
            return """
                package org.gradle.test;

                import ${Restricted.class.name};

                import org.gradle.api.Named;
                import org.gradle.api.NamedDomainObjectContainer;
                import org.gradle.api.provider.Property;
                import ${Definition.class.name};
                import ${BuildModel.class.name};

                public abstract class ${publicTypeClassName} implements ${Definition.class.simpleName}<${publicTypeClassName}.ModelType> {
                    @${Restricted.class.simpleName}
                    public abstract Property<String> getId();

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

                        @${Restricted.class.simpleName}
                        public abstract Property<Integer> getX();

                        @${Restricted.class.simpleName}
                        public abstract Property<Integer> getY();

                        @Override
                        public String toString() {
                            return "Foo(name = " + name + ", x = " + getX().get() + ", y = " + getY().get() + ")";
                        }
                    }

                    public interface ModelType extends BuildModel {
                        Property<String> getId();
                    }
                }

            """
        }

        @Override
        String displayDefinitionPropertyValues() {
            return """
                ${displayProperty("definition", "id", "definition.getId().get()")}
                ${displayProperty("definition", "foos", 'definition.getFoos().stream().map(Object::toString).collect(java.util.stream.Collectors.joining(", "))')}
            """
        }
    }

    static class ProjectTypeDefinitionWithPublicAndImplementationTypesClassBuilder extends ProjectTypeDefinitionClassBuilder {
        ProjectTypeDefinitionWithPublicAndImplementationTypesClassBuilder() {
            this.hasImplementationType = true
        }

        @Override
        String getImplementationTypeClassContent() {
            return """
                package org.gradle.test;

                import ${Restricted.class.name};
                import org.gradle.api.Action;
                import org.gradle.api.model.ObjectFactory;
                import org.gradle.api.provider.ListProperty;
                import org.gradle.api.provider.Property;

                import javax.inject.Inject;

                @${Restricted.class.simpleName}
                public abstract class ${implementationTypeClassName} implements ${publicTypeClassName} {
                    private final Foo foo;

                    @Inject
                    public ${implementationTypeClassName}(ObjectFactory objects) {
                        this.foo = objects.newInstance(Foo.class);
                    }

                    @Override
                    public Foo getFoo() {
                        return foo;
                    }

                    @${Restricted.class.simpleName}
                    public abstract Property<String> getNonPublic();
                }
            """
        }
    }

    static class AnotherProjectTypeDefinitionClassBuilder extends ProjectTypeDefinitionClassBuilder {
        AnotherProjectTypeDefinitionClassBuilder() {
            publicTypeClassName = "AnotherProjectTypeDefinition"
        }

        @Override
        String getPublicTypeClassContent() {
            return """
                package org.gradle.test;

                import ${Adding.class.name};
                import ${Configuring.class.name};
                import ${Restricted.class.name};

                import org.gradle.api.Action;
                import org.gradle.api.model.ObjectFactory;
                import org.gradle.api.provider.ListProperty;
                import org.gradle.api.provider.Property;
                import org.gradle.api.tasks.Nested;
                import ${Definition.class.name};
                import ${BuildModel.class.name};

                import javax.inject.Inject;

                @${Restricted.class.simpleName}
                public interface ${publicTypeClassName} extends ${Definition.class.simpleName}<${publicTypeClassName}.ModelType> {
                    @${Restricted.class.simpleName}
                    Property<String> getId();

                    @${Restricted.class.simpleName}
                    Property<String> getFoo();

                    @Nested
                    Bar getBar();

                    @${Configuring.class.simpleName}
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

        @Override
        String displayDefinitionPropertyValues() {
            return """
                ${displayProperty("definition", "id", "definition.getId().get()")}
                ${displayProperty("definition", "foo", "definition.getFoo().get()")}
                ${displayProperty("definition", "bar.baz", "definition.getBar().getBaz().get()")}
            """
        }
    }

    static class ProjectTypeDefinitionWithDependenciesClassBuilder extends ProjectTypeDefinitionClassBuilder {
        private String interfaceName = "TestProjectTypeDefinition"

        ProjectTypeDefinitionWithDependenciesClassBuilder() {
            this.publicTypeClassName = "TestProjectTypeDefinitionWithDependencies"
        }

        ProjectTypeDefinitionWithDependenciesClassBuilder parentClassName(String parentClassName) {
            this.interfaceName = parentClassName
            return this
        }

        @Override
        String getPublicTypeClassContent() {
            return """
                package org.gradle.test;

                import org.gradle.api.Action;
                import org.gradle.api.model.ObjectFactory;
                import org.gradle.api.provider.Property;
                import org.gradle.api.provider.ListProperty;
                import org.gradle.api.artifacts.dsl.DependencyCollector;
                import ${Configuring.class.name};
                import ${Restricted.class.name};
                import ${Adding.class.name};
                import org.gradle.api.tasks.Nested;

                import java.util.List;
                import javax.inject.Inject;

                @${Restricted.class.simpleName}
                public abstract class ${publicTypeClassName} implements ${interfaceName} {
                    private boolean isBarConfigured = false;

                    @Inject
                    public ${publicTypeClassName}() { }

                    @Nested
                    abstract public LibraryDependencies getDependencies();

                    @${Configuring.class.simpleName}
                    public void dependencies(Action<? super LibraryDependencies> action) {
                        action.execute(getDependencies());
                    }

                    public abstract ListProperty<String> getList();

                    @${Adding.class.simpleName}
                    public void addToList(String value) {
                        getList().add(value);
                    }

                    @Nested
                    public abstract Bar getBar();

                    @${Configuring.class.simpleName}
                    public void bar(Action<? super Bar> action) {
                        isBarConfigured = true;
                        action.execute(getBar());
                    }

                    public abstract static class Bar {

                        public abstract ListProperty<String> getBaz();

                        @${Adding.class.simpleName}
                        public void addToBaz(String value) {
                            getBaz().add(value);
                        }
                    }

                    public String printDependencies(DependencyCollector collector) {
                        return collector.getDependencies().get().stream().map(Object::toString).collect(java.util.stream.Collectors.joining(", "));
                    }

                    public String printList(List<?> list) {
                        return list.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(", "));
                    }

                    public String maybeBarConfigure() {
                        return isBarConfigured ? "(bar is configured)" : "";
                    }
                }
            """
        }

        @Override
        String displayDefinitionPropertyValues() {
            return super.displayDefinitionPropertyValues() +"""
                ${displayProperty("definition", "list", "definition.printList(definition.getList().get())")}
                ${displayProperty("definition", "bar.baz", "definition.printList(definition.getBar().getBaz().get())")}
                ${displayProperty("definition", "api", "definition.printDependencies(definition.getDependencies().getApi())")}
                ${displayProperty("definition", "implementation", "definition.printDependencies(definition.getDependencies().getImplementation())")}
                ${displayProperty("definition", "runtimeOnly", "definition.printDependencies(definition.getDependencies().getRuntimeOnly())")}
                ${displayProperty("definition", "compileOnly", "definition.printDependencies(definition.getDependencies().getCompileOnly())")}
                System.out.println("definition " + definition.maybeBarConfigure());
            """
        }

        static String getLibraryDependencies() {
            return """
                package org.gradle.test;

                import org.gradle.api.artifacts.dsl.Dependencies;
                import org.gradle.api.artifacts.dsl.DependencyCollector;
                import ${Restricted.class.name};


                @${Restricted.class.simpleName}
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
        String getPublicTypeClassContent() {
            return """
                package org.gradle.test;

                import ${Configuring.class.name};
                import ${Restricted.class.name};

                import org.gradle.api.Action;
                import org.gradle.api.model.ObjectFactory;
                import org.gradle.api.provider.ListProperty;
                import org.gradle.api.provider.Property;
                import ${Definition.class.name};
                import ${BuildModel.class.name};

                import javax.inject.Inject;

                @${Restricted.class.simpleName}
                public abstract class ${publicTypeClassName} implements ${Definition.class.simpleName}<${publicTypeClassName}.ModelType> {
                    private final Foo foo;
                    private boolean isFooConfigured = false;

                    @Inject
                    public ${publicTypeClassName}(ObjectFactory objects) {
                        this.foo = objects.newInstance(Foo.class);
                    }

                    @${Restricted.class.simpleName}
                    public abstract Property<String> getId();

                    public Foo getFoo() {
                        return foo;
                    }

                    @${Configuring.class.simpleName}
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

                    public String maybeFooConfigured() {
                        return isFooConfigured ? "(foo is configured)" : "";
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

        @Override
        String displayDefinitionPropertyValues() {
            return super.displayDefinitionPropertyValues() + """
                System.out.println("definition " + definition.maybeFooConfigured());
            """
        }
    }

    static class ProjectTypeDefinitionWithInjectableParentClassBuilder extends ProjectTypeDefinitionClassBuilder {
        String parentTypeClassName = "ParentTestProjectTypeDefinition"

        ProjectTypeDefinitionWithInjectableParentClassBuilder() {
            // Adds injected services to the parent
            withInjectedServices()
        }

        @Override
        String getPublicTypeClassContent() {
            return """
                package org.gradle.test;

                import ${Restricted.class.name};
                import org.gradle.api.Action;
                import org.gradle.api.model.ObjectFactory;
                import org.gradle.api.provider.ListProperty;
                import org.gradle.api.provider.Property;

                import javax.inject.Inject;

                @${Restricted.class.simpleName}
                public interface ${publicTypeClassName} extends ${parentTypeClassName} { }
            """
        }

        String getParentClassContent() {
            super.defaultClassContent(parentTypeClassName)
        }

        @Override
        void build(PluginBuilder pluginBuilder) {
            super.build(pluginBuilder)
            pluginBuilder.file("src/main/java/org/gradle/test/${parentTypeClassName}.java") << getParentClassContent()
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
