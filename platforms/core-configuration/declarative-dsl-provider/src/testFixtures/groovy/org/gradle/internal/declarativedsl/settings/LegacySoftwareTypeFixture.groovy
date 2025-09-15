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

import org.gradle.api.internal.plugins.SoftwareTypeBindingBuilder // codenarc-disable-line UnusedImport
import org.gradle.api.internal.plugins.SoftwareTypeBindingRegistration // codenarc-disable-line UnusedImport
import org.gradle.api.internal.plugins.software.SoftwareType
import org.gradle.test.fixtures.plugin.PluginBuilder

trait LegacySoftwareTypeFixture extends SoftwareTypeFixture {
    PluginBuilder withLegacySoftwareTypePlugins() {
        def definition = new SoftwareTypeDefinitionClassBuilder()
        def softwareType = new LegacySoftwareTypePluginClassBuilder()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersSoftwareType(softwareType.softwareTypePluginClassName)

        return withSoftwareTypePlugins(
            definition,
            softwareType,
            settingsBuilder
        )
    }

    PluginBuilder withLegacySoftwareTypePluginWithMismatchedModelTypes() {
        def definition = new SoftwareTypeDefinitionClassBuilder()
        def anotherSoftwareTypeDefinition = new AnotherSoftwareTypeDefinitionClassBuilder()
        def softwareType = new LegacySoftwareTypePluginClassBuilder()
            .definitionPublicTypeClassName(anotherSoftwareTypeDefinition.implementationTypeClassName)
            .withoutConventions()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersSoftwareType(softwareType.softwareTypePluginClassName)

        def pluginBuilder = withSoftwareTypePlugins(
            definition,
            softwareType,
            settingsBuilder
        )

        anotherSoftwareTypeDefinition.build(pluginBuilder)

        return pluginBuilder
    }

    PluginBuilder withLegacySoftwareTypePluginThatExposesSoftwareTypeFromParentClass() {
        def definition = new SoftwareTypeDefinitionClassBuilder()
        def softwareType = new LegacyProjectPluginThatProvidesSoftwareTypeFromParentClassBuilder()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersSoftwareType(softwareType.softwareTypePluginClassName)

        return withSoftwareTypePlugins(
            definition,
            softwareType,
            settingsBuilder
        )
    }

    PluginBuilder withLegacySoftwareTypePluginThatHasUnannotatedMethods() {
        def definition = new SoftwareTypeDefinitionClassBuilder()
        def softwareType = new LegacyProjectPluginThatProvidesSoftwareTypeThatHasUnannotatedMethodsBuilder()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersSoftwareType(softwareType.softwareTypePluginClassName)

        return withSoftwareTypePlugins(
            definition,
            softwareType,
            settingsBuilder
        )
    }

    PluginBuilder withLegacySoftwareTypePluginThatExposesPrivateSoftwareType() {
        def definition = new SoftwareTypeDefinitionClassBuilder()
        def softwareType = new LegacySoftwareTypePluginThatProvidesPrivateSoftwareTypeBuilder()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersSoftwareType(softwareType.softwareTypePluginClassName)

        return withSoftwareTypePlugins(
            definition,
            softwareType,
            settingsBuilder
        )
    }

    PluginBuilder withLegacySoftwareTypePluginThatRegistersItsOwnExtension() {
        def definition = new SoftwareTypeDefinitionClassBuilder()
        def softwareType = new LegacySoftwareTypePluginThatRegistersItsOwnExtensionBuilder()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersSoftwareType(softwareType.softwareTypePluginClassName)

        return withSoftwareTypePlugins(
            definition,
            softwareType,
            settingsBuilder
        )
    }

    PluginBuilder withLegacySoftwareTypePluginThatFailsToRegistersItsOwnExtension() {
        def definition = new SoftwareTypeDefinitionClassBuilder()
        def softwareType = new LegacySoftwareTypePluginThatRegistersItsOwnExtensionBuilder()
            .shouldRegisterExtension(false)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersSoftwareType(softwareType.softwareTypePluginClassName)

        return withSoftwareTypePlugins(
            definition,
            softwareType,
            settingsBuilder
        )
    }

    PluginBuilder withLegacySoftwareTypePluginThatRegistersTheWrongExtension() {
        def definition = new SoftwareTypeDefinitionClassBuilder()
        def softwareType = new LegacySoftwareTypePluginThatRegistersItsOwnExtensionBuilder()
            .extensionFactory("new String()")
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersSoftwareType(softwareType.softwareTypePluginClassName)

        return withSoftwareTypePlugins(
            definition,
            softwareType,
            settingsBuilder
        )
    }

    static class LegacySoftwareTypePluginClassBuilder extends SoftwareTypeFixture.SoftwareTypePluginClassBuilder {
        LegacySoftwareTypePluginClassBuilder() {
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
                import ${SoftwareTypeBindingRegistration.class.name};
                import ${SoftwareTypeBindingBuilder.class.name};
                import javax.inject.Inject;


                abstract public class ${softwareTypePluginClassName} implements Plugin<Project> {

                    @SoftwareType(${SoftwareTypeArgumentBuilder.name(name)
                        .modelPublicType(definitionPublicTypeClassName)
                        .build()})
                    abstract public ${definitionImplementationTypeClassName} getTestSoftwareTypeExtension();

                    @Override
                    public void apply(Project target) {
                        System.out.println("Applying " + getClass().getSimpleName());
                        ${definitionImplementationTypeClassName} extension = getTestSoftwareTypeExtension();

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

    static class LegacySoftwareTypePluginThatRegistersItsOwnExtensionBuilder extends LegacySoftwareTypePluginClassBuilder {
        boolean shouldRegisterExtension = true
        String extensionFactory = "extension"

        LegacySoftwareTypePluginThatRegistersItsOwnExtensionBuilder shouldRegisterExtension(boolean shouldRegisterExtension) {
            this.shouldRegisterExtension = shouldRegisterExtension
            return this
        }

        LegacySoftwareTypePluginThatRegistersItsOwnExtensionBuilder extensionFactory(String extension) {
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

                abstract public class ${softwareTypePluginClassName} implements Plugin<Project> {

                    @SoftwareType(${SoftwareTypeArgumentBuilder.name(name)
                .disableModelManagement(true)
                .build()})
                    abstract public ${definitionImplementationTypeClassName} getTestSoftwareTypeExtension();

                    @Override
                    public void apply(Project target) {
                        System.out.println("Applying " + getClass().getSimpleName());
                        ${definitionImplementationTypeClassName} extension = getTestSoftwareTypeExtension();
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

    static class LegacyProjectPluginThatProvidesSoftwareTypeFromParentClassBuilder extends LegacySoftwareTypePluginClassBuilder {
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

                abstract public class ${softwareTypePluginClassName} implements ExposesSoftwareType {

                    @Override
                    public void apply(Project target) {
                        ${definitionImplementationTypeClassName} extension = getTestSoftwareTypeExtension();
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
                    abstract public ${definitionImplementationTypeClassName} getTestSoftwareTypeExtension();
                }
            """
        }

        @Override
        void build(PluginBuilder pluginBuilder) {
            super.build(pluginBuilder)

            pluginBuilder.file("src/main/java/org/gradle/test/ExposesSoftwareType.java") << exposesSoftwareType
        }
    }

    static class LegacyProjectPluginThatProvidesSoftwareTypeThatHasUnannotatedMethodsBuilder extends LegacySoftwareTypePluginClassBuilder {
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

                abstract public class ${softwareTypePluginClassName} implements Plugin<Project> {

                    @SoftwareType(${SoftwareTypeArgumentBuilder.name(name)
                .modelPublicType(definitionPublicTypeClassName)
                .build()})
                    abstract public ${definitionImplementationTypeClassName} getTestSoftwareTypeExtension();

                    String getFoo() {
                        return "foo";
                    }

                    @Override
                    public void apply(Project target) {
                        ${definitionImplementationTypeClassName} extension = getTestSoftwareTypeExtension();
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
    static class LegacySoftwareTypePluginThatProvidesPrivateSoftwareTypeBuilder extends LegacySoftwareTypePluginClassBuilder {
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

            abstract public class ${softwareTypePluginClassName} implements Plugin<Project> {

                @SoftwareType(name="${name}", modelPublicType=AnotherSoftwareTypeExtension.class)
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
