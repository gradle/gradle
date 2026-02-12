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

/**
 * A {@link ProjectFeatureDefinitionClassBuilder} for creating a project feature definition implemented as an abstract class,
 * rather than an interface.
 */
class ProjectFeatureDefinitionAbstractClassBuilder extends ProjectFeatureDefinitionClassBuilder {
    @Override
    protected String getPublicTypeClassContent() {
        return """
            package org.gradle.test;

            import ${Definition.class.name};
            import ${BuildModel.class.name};
            import org.gradle.api.provider.Property;
            import org.gradle.api.file.DirectoryProperty;
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.api.Action;
            import org.gradle.api.tasks.Nested;
            import ${HiddenInDefinition.class.name};
            import javax.inject.Inject;

            public abstract class ${publicTypeClassName} implements ${Definition.class.simpleName}<${publicTypeClassName}.FeatureModel> {
                public abstract Property<String> getText();

                ${maybeInjectedServiceDeclaration}

                @Nested
                public abstract Fizz getFizz();

                @${HiddenInDefinition.simpleName}
                public void fizz(Action<? super Fizz> action) {
                    action.execute(getFizz());
                }

                public interface Fizz {
                    ${maybeNestedInjectedServiceDeclaration}
                    Property<String> getBuzz();
                }

                public interface FeatureModel extends BuildModel {
                    Property<String> getText();
                    DirectoryProperty getDir();
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
        return """
            ${displayProperty("definition", "text", "definition.getText().get()")}
        """
    }

    @Override
    String displayModelPropertyValues() {
        return """
            ${displayProperty("model", "text", "model.getText().get()")}
        """
    }
}
