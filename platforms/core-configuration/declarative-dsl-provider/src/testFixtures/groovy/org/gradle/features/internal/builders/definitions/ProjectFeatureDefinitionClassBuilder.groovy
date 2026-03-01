/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.features.internal.builders.definitions

import org.gradle.declarative.dsl.model.annotations.HiddenInDefinition
import org.gradle.features.binding.BuildModel
import org.gradle.features.binding.Definition
import org.gradle.test.fixtures.plugin.PluginBuilder

/**
 * A builder for a project feature definition class, used in tests of the declarative DSL provider. The generated definition class
 * contains multiple properties and nested types, to allow testing of a variety of scenarios with a single definition.
 *
 * This is the base builder, which creates a definition implemented as an interface. Subclasses can override methods to create
 * definitions with different behavior and qualities.
 */
class ProjectFeatureDefinitionClassBuilder {
    String publicTypeClassName = "FeatureDefinition"
    String implementationTypeClassName = "FeatureDefinitionImpl"
    String buildModelPublicTypeClassName = "FeatureModel"
    String buildModelImplementationTypeClassName = "FeatureModelImpl"

    boolean hasInjectedServices = false
    boolean hasNestedInjectedServices = false
    boolean hasDefinitionImplementationType = false
    boolean hasBuildModelImplementationType = false

    ProjectFeatureDefinitionClassBuilder withInjectedServices() {
        this.hasInjectedServices = true
        return this
    }

    ProjectFeatureDefinitionClassBuilder withNestedInjectedServices() {
        this.hasNestedInjectedServices = true
        return this
    }

    ProjectFeatureDefinitionClassBuilder withPublicClassName(String className) {
        this.publicTypeClassName = className
        return this
    }

    ProjectFeatureDefinitionClassBuilder withImplementationClassName(String className) {
        this.implementationTypeClassName = className
        return this
    }

    ProjectFeatureDefinitionClassBuilder buildModelPublicTypeClassName(String className) {
        this.buildModelPublicTypeClassName = className
        return this
    }

    ProjectFeatureDefinitionClassBuilder buildModelImplementationTypeClassName(String className) {
        this.buildModelImplementationTypeClassName = className
        return this
    }

    void build(PluginBuilder pluginBuilder) {
        pluginBuilder.file("src/main/java/org/gradle/test/${publicTypeClassName}.java") << getPublicTypeClassContent()
        if (hasDefinitionImplementationType) {
            pluginBuilder.file("src/main/java/org/gradle/test/${implementationTypeClassName}.java") << getImplementationTypeClassContent()
        }
    }

    protected String getPublicTypeClassContent() {
        getDefaultClassContent(publicTypeClassName)
    }

    protected String getImplementationTypeClassContent() {
        return null
    }

    protected String getDefaultClassContent(String className) {
        return """
            package org.gradle.test;

            import ${Definition.class.name};
            import ${BuildModel.class.name};
            import org.gradle.api.provider.Property;
            import org.gradle.api.file.DirectoryProperty;
            import ${HiddenInDefinition.class.name};
            import org.gradle.api.Action;
            import org.gradle.api.tasks.Nested;
            import javax.inject.Inject;
            import org.gradle.api.model.ObjectFactory;

            public interface ${className} extends ${Definition.class.simpleName}<${className}.${buildModelPublicTypeClassName}> {
                Property<String> getText();

                ${getMaybeInjectedServiceDeclaration()}

                @Nested
                Fizz getFizz();

                @${HiddenInDefinition.class.simpleName}
                default void fizz(Action<? super Fizz> action) {
                    action.execute(getFizz());
                }

                interface ${buildModelPublicTypeClassName} extends BuildModel {
                    Property<String> getText();
                    DirectoryProperty getDir();
                }

                interface Fizz {
                    ${getMaybeNestedInjectedServiceDeclaration()}
                    Property<String> getBuzz();
                }
            }
        """
    }

    String getBuildModelMapping() {
        return """
            model.getText().set(definition.getText());
            model.getDir().set(services.getProjectFeatureLayout().getProjectDirectory().dir(definition.getText()));
        """
    }

    String displayDefinitionPropertyValues() {
        return """
            ${displayProperty("definition", "text", "definition.getText().get()")}
            ${displayProperty("definition", "fizz.buzz", "definition.getFizz().getBuzz().get()")}
        """
    }

    String displayModelPropertyValues() {
        return """
            ${displayProperty("model", "text", "model.getText().get()")}
            ${displayProperty("model", "dir", "model.getDir().get().getAsFile().getAbsolutePath()")}
        """
    }

    String getBuildModelFullPublicClassName() {
        return "${publicTypeClassName}.${buildModelPublicTypeClassName}"
    }

    String getBuildModelFullImplementationClassName() {
        return "${publicTypeClassName}.${buildModelImplementationTypeClassName}"
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
        return """
            System.out.println("${objectType} ${propertyName} = " + ${propertyValueExpression});
        """
    }
}
