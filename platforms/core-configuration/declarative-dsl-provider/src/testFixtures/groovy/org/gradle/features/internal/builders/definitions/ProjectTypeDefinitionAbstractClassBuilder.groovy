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
 * A {@link ProjectTypeDefinitionClassBuilder} for creating a project type definition that is an abstract class.
 */
class ProjectTypeDefinitionAbstractClassBuilder extends ProjectTypeDefinitionClassBuilder {
    @Override
    String getPublicTypeClassContent() {
        return """
            package org.gradle.test;

            import ${HiddenInDefinition.class.name};

            import org.gradle.api.Action;
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;
            import ${Definition.class.name};
            import ${BuildModel.class.name};

            import javax.inject.Inject;

            public abstract class ${publicTypeClassName} implements ${Definition.class.simpleName}<${publicTypeClassName}.ModelType> {
                private final Foo foo;
                private boolean isFooConfigured = false;

                @Inject
                public ${publicTypeClassName}(ObjectFactory objects) {
                    this.foo = objects.newInstance(Foo.class);
                }

                public abstract Property<String> getId();

                public Foo getFoo() {
                    isFooConfigured = true; // TODO: get rid of the side effect in the getter
                    return foo;
                }

                @${HiddenInDefinition.class.simpleName}
                public void foo(Action<? super Foo> action) {
                    action.execute(foo);
                }

                ${maybeInjectedServiceDeclaration}

                public abstract static class Foo implements ${Definition.class.simpleName}<FooBuildModel> {
                    public Foo() { }

                    ${maybeNestedInjectedServiceDeclaration}

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
