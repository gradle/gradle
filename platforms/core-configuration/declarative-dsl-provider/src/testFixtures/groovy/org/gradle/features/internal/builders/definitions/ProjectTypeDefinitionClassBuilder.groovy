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
 * A builder for creating a project type definition with a single public type, and no injected services.
 *
 * This class is used as the basis for more specific builders that create project type definitions with different characteristics
 * or behaviors.
 */
class ProjectTypeDefinitionClassBuilder {
    String publicTypeClassName = "TestProjectTypeDefinition"
    String implementationTypeClassName = "TestProjectTypeDefinitionImpl"
    String buildModelTypeClassName = "ModelType"

    boolean hasImplementationType = false
    boolean hasInjectedServices = false
    boolean hasNestedInjectedServices = false

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

    String getFullyQualifiedBuildModelClassName() {
        return getFullyQualifiedPublicTypeClassName() + "." + buildModelTypeClassName
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

            import ${HiddenInDefinition.class.name};

            import org.gradle.api.Action;
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;
            import org.gradle.api.tasks.Nested;
            import ${Definition.class.name};
            import ${BuildModel.class.name};

            import javax.inject.Inject;

            public interface ${effectiveClassName} extends ${Definition.class.simpleName}<${effectiveClassName}.${buildModelTypeClassName}> {
                Property<String> getId();

                @Nested
                Foo getFoo();

                @${HiddenInDefinition.class.simpleName}
                default void foo(Action<? super Foo> action) {
                    action.execute(getFoo());
                }

                ${maybeInjectedServiceDeclaration}

                interface Foo extends ${Definition.class.simpleName}<FooBuildModel> {
                    public abstract Property<String> getBar();

                    ${maybeNestedInjectedServiceDeclaration}
                }

                interface FooBuildModel extends BuildModel {
                    Property<String> getBarProcessed();
                }

                interface ${buildModelTypeClassName} extends BuildModel {
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
        return """
            System.out.println("${objectType} ${propertyName} = " + ${propertyValueExpression});
        """
    }
}
