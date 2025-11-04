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

import org.gradle.api.internal.plugins.ProjectTypeBindingBuilder // codenarc-disable-line UnusedImport
import org.gradle.api.internal.plugins.ProjectTypeBindingRegistration // codenarc-disable-line UnusedImport
import org.gradle.api.internal.plugins.software.SoftwareType
import org.gradle.test.fixtures.plugin.PluginBuilder

trait LegacyProjectTypeFixture extends ProjectTypeFixture {
    PluginBuilder withLegacyProjectTypePlugins() {
        def definition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new LegacyProjectTypePluginClassBuilder()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectTypePlugins(
            definition,
            projectType,
            settingsBuilder
        )
    }

    PluginBuilder withLegacyProjectTypePluginWithMismatchedModelTypes() {
        def definition = new ProjectTypeDefinitionClassBuilder()
        def anotherProjectTypeDefinition = new AnotherProjectTypeDefinitionClassBuilder()
        def projectType = new LegacyProjectTypePluginClassBuilder()
            .definitionPublicTypeClassName(anotherProjectTypeDefinition.implementationTypeClassName)
            .withoutConventions()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        def pluginBuilder = withProjectTypePlugins(
            definition,
            projectType,
            settingsBuilder
        )

        anotherProjectTypeDefinition.build(pluginBuilder)

        return pluginBuilder
    }

    PluginBuilder withLegacyProjectTypePluginThatExposesProjectTypeFromParentClass() {
        def definition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new LegacyProjectPluginThatProvidesProjectTypeFromParentClassBuilder()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectTypePlugins(
            definition,
            projectType,
            settingsBuilder
        )
    }

    PluginBuilder withLegacyProjectTypePluginThatHasUnannotatedMethods() {
        def definition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new LegacyProjectPluginThatProvidesProjectTypeThatHasUnannotatedMethodsBuilder()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectTypePlugins(
            definition,
            projectType,
            settingsBuilder
        )
    }

    PluginBuilder withLegacyProjectTypePluginThatExposesPrivateProjectType() {
        def definition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new LegacyProjectTypePluginThatProvidesPrivateProjectTypeBuilder()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectTypePlugins(
            definition,
            projectType,
            settingsBuilder
        )
    }

    PluginBuilder withLegacyProjectTypePluginThatRegistersItsOwnExtension() {
        def definition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new LegacyProjectTypePluginThatRegistersItsOwnExtensionBuilder()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectTypePlugins(
            definition,
            projectType,
            settingsBuilder
        )
    }

    PluginBuilder withLegacyProjectTypePluginThatFailsToRegistersItsOwnExtension() {
        def definition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new LegacyProjectTypePluginThatRegistersItsOwnExtensionBuilder()
            .shouldRegisterExtension(false)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectTypePlugins(
            definition,
            projectType,
            settingsBuilder
        )
    }

    PluginBuilder withLegacyProjectTypePluginThatRegistersTheWrongExtension() {
        def definition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new LegacyProjectTypePluginThatRegistersItsOwnExtensionBuilder()
            .extensionFactory("new String()")
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectTypePlugins(
            definition,
            projectType,
            settingsBuilder
        )
    }

    static class LegacyProjectTypePluginClassBuilder extends ProjectTypeFixture.ProjectTypePluginClassBuilder {
        LegacyProjectTypePluginClassBuilder() {
            this.conventions("""
                extension.getFoo().getBar().convention("bar");
                extension.getId().convention("<no id>");
            """)
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
                import ${SoftwareType.class.name};
                import ${ProjectTypeBindingRegistration.class.name};
                import ${ProjectTypeBindingBuilder.class.name};
                import javax.inject.Inject;


                abstract public class ${projectTypePluginClassName} implements Plugin<Project> {

                    @SoftwareType(${SoftwareTypeArgumentBuilder.name(name)
                        .modelPublicType(definitionPublicTypeClassName)
                        .build()})
                    abstract public ${definitionImplementationTypeClassName} getTestProjectTypeDefinition();

                    @Override
                    public void apply(Project target) {
                        System.out.println("Applying " + getClass().getSimpleName());
                        ${definitionImplementationTypeClassName} extension = getTestProjectTypeDefinition();

                        ${conventions == null ? "" : conventions}
                        String projectName = target.getName();
                        target.getTasks().register("print${definitionImplementationTypeClassName}Configuration", DefaultTask.class, task -> {
                            task.doLast("print restricted extension content", t -> {
                                System.out.println(projectName + ": " + extension);
                            });
                        });
                    }
                }
            """
        }
    }

    static class LegacyProjectTypePluginThatRegistersItsOwnExtensionBuilder extends LegacyProjectTypePluginClassBuilder {
        boolean shouldRegisterExtension = true
        String extensionFactory = "extension"

        LegacyProjectTypePluginThatRegistersItsOwnExtensionBuilder shouldRegisterExtension(boolean shouldRegisterExtension) {
            this.shouldRegisterExtension = shouldRegisterExtension
            return this
        }

        LegacyProjectTypePluginThatRegistersItsOwnExtensionBuilder extensionFactory(String extension) {
            this.extensionFactory = extension
            return this
        }

        @Override
        protected String getClassContent() {
            String extensionRegistration = shouldRegisterExtension ? """target.getExtensions().add("${name}", ${extensionFactory});""" : ""
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

                abstract public class ${projectTypePluginClassName} implements Plugin<Project> {

                    @SoftwareType(${SoftwareTypeArgumentBuilder.name(name)
                .disableModelManagement(true)
                .build()})
                    abstract public ${definitionImplementationTypeClassName} getTestProjectTypeDefinition();

                    @Override
                    public void apply(Project target) {
                        System.out.println("Applying " + getClass().getSimpleName());
                        ${definitionImplementationTypeClassName} extension = getTestProjectTypeDefinition();
                        ${extensionRegistration}

                        ${conventions == null ? "" : conventions}
                        String projectName = target.getName();
                        target.getTasks().register("print${definitionImplementationTypeClassName}Configuration", DefaultTask.class, task -> {
                            task.doLast("print restricted extension content", t -> {
                                System.out.println(projectName + ": " + extension);
                            });
                        });
                    }
                }
            """
        }
    }

    static class LegacyProjectPluginThatProvidesProjectTypeFromParentClassBuilder extends LegacyProjectTypePluginClassBuilder {
        @Override
        protected String getClassContent() {
            return """
                package org.gradle.test;

                import org.gradle.api.DefaultTask;
                import org.gradle.api.provider.ListProperty;
                import org.gradle.api.provider.Property;
                import org.gradle.api.Project;
                import org.gradle.api.tasks.Nested;
                import ${SoftwareType.class.name};
                import javax.inject.Inject;

                abstract public class ${projectTypePluginClassName} implements ExposesSoftwareType {

                    @Override
                    public void apply(Project target) {
                        ${definitionImplementationTypeClassName} extension = getTestProjectTypeDefinition();
                        extension.getFoo().getBar().convention("bar");
                        extension.getId().convention("<no id>");
                        target.getTasks().register("print${definitionImplementationTypeClassName}Configuration", DefaultTask.class, task -> {
                            task.doLast("print restricted extension content", t -> {
                                System.out.println(extension);
                            });
                        });
                    }
                }
            """
        }

        String getExposesSoftwareType() {
            return """
                package org.gradle.test;

                import org.gradle.api.Plugin;
                import org.gradle.api.Project;
                import ${SoftwareType.class.name};

                public interface ExposesSoftwareType extends Plugin<Project> {
                    @SoftwareType(${SoftwareTypeArgumentBuilder.name(name)
                    .modelPublicType(definitionPublicTypeClassName)
                    .build()})
                    abstract public ${definitionImplementationTypeClassName} getTestProjectTypeDefinition();
                }
            """
        }

        @Override
        void build(PluginBuilder pluginBuilder) {
            super.build(pluginBuilder)

            pluginBuilder.file("src/main/java/org/gradle/test/ExposesSoftwareType.java") << exposesSoftwareType
        }
    }

    static class LegacyProjectPluginThatProvidesProjectTypeThatHasUnannotatedMethodsBuilder extends LegacyProjectTypePluginClassBuilder {
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
                import ${SoftwareType.class.name};
                import javax.inject.Inject;

                abstract public class ${projectTypePluginClassName} implements Plugin<Project> {

                    @SoftwareType(${SoftwareTypeArgumentBuilder.name(name)
                .modelPublicType(definitionPublicTypeClassName)
                .build()})
                    abstract public ${definitionImplementationTypeClassName} getTestProjectTypeDefinition();

                    String getFoo() {
                        return "foo";
                    }

                    @Override
                    public void apply(Project target) {
                        ${definitionImplementationTypeClassName} extension = getTestProjectTypeDefinition();
                        target.getTasks().register("print${definitionImplementationTypeClassName}Configuration", DefaultTask.class, task -> {
                            task.doLast("print restricted extension content", t -> {
                                System.out.println(extension);
                            });
                        });
                        System.out.println(getFoo());
                    }
                }
            """
        }
    }
    static class LegacyProjectTypePluginThatProvidesPrivateProjectTypeBuilder extends LegacyProjectTypePluginClassBuilder {
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
            import ${SoftwareType.class.name};
            import org.gradle.declarative.dsl.model.annotations.Adding;
            import org.gradle.declarative.dsl.model.annotations.Configuring;
            import org.gradle.declarative.dsl.model.annotations.Restricted;
            import org.gradle.api.Action;
            import org.gradle.api.model.ObjectFactory;

            import javax.inject.Inject;

            abstract public class ${projectTypePluginClassName} implements Plugin<Project> {

                @SoftwareType(name="${name}", modelPublicType=AnotherProjectTypeDefinition.class)
                abstract public AnotherProjectTypeDefinition getTestProjectTypeDefinition();

                @Override
                public void apply(Project target) {
                    System.out.println("Applying " + getClass().getSimpleName());
                    AnotherProjectTypeDefinition extension = getTestProjectTypeDefinition();
                    target.getTasks().register("printTestProjectTypeDefinitionConfiguration", DefaultTask.class, task -> {
                        task.doLast("print restricted extension content", t -> {
                            System.out.println(extension);
                        });
                    });
                }

                @Restricted
                private static abstract class AnotherProjectTypeDefinition {
                    private final Foo foo;

                    @Inject
                    public AnotherProjectTypeDefinition(ObjectFactory objects) {
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
    }

    static class SoftwareTypeArgumentBuilder {
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
}
