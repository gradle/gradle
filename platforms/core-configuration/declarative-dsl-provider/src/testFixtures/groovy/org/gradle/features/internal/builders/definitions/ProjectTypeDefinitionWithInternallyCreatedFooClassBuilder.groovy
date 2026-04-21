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


import org.gradle.features.binding.BuildModel
import org.gradle.features.binding.Definition

/**
 * A {@link ProjectTypeDefinitionClassBuilder} that creates a nested {@code Foo} definition object internally in its constructor.
 * Demonstrates how to handle build model registration with an unsafe apply action.
 */
class ProjectTypeDefinitionWithInternallyCreatedFooClassBuilder extends ProjectTypeDefinitionClassBuilder {

    @Override
    String getPublicTypeClassContent() {
        return """
            package org.gradle.test;

            import org.gradle.api.Action;
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.api.provider.Property;
            import ${Definition.class.name};
            import ${BuildModel.class.name};

            import javax.inject.Inject;

            public abstract class ${publicTypeClassName} implements ${Definition.class.simpleName}<${publicTypeClassName}.${buildModelTypeClassName}> {

                private final Foo myFoo;

                @Inject
                public ${publicTypeClassName}(ObjectFactory objectFactory) {
                    // Foo is created internally. Gradle cannot auto-discover it for build model registration.
                    this.myFoo = objectFactory.newInstance(Foo.class);
                }

                public abstract Property<String> getId();

                public void foo(Action<? super Foo> action) {
                    action.execute(myFoo);
                }

                Foo getMyFoo() {
                    return myFoo;
                }

                public interface Foo extends ${Definition.class.simpleName}<Foo.FooBuildModel> {
                    Property<String> getBar();

                    interface FooBuildModel extends ${BuildModel.class.simpleName} {
                        Property<String> getBarProcessed();
                    }
                }

                public interface ${buildModelTypeClassName} extends ${BuildModel.class.simpleName} {
                    Property<String> getId();
                }
            }
        """
    }

    @Override
    String getBuildModelMapping() {
        return """
                model.getId().set(definition.getId());
                ${publicTypeClassName}.Foo myFoo = definition.getMyFoo();
                ${publicTypeClassName}.Foo.FooBuildModel fooBuildModel =
                    services.getBuildModelRegistrar().registerBuildModel(myFoo);
                fooBuildModel.getBarProcessed().set(myFoo.getBar().map(it -> it.toUpperCase()));
            """
    }

    @Override
    String displayDefinitionPropertyValues() {
        return """
            ${displayProperty("definition", "foo.bar", "definition.getMyFoo().getBar().getOrNull()")}
        """
    }

    @Override
    String displayModelPropertyValues() {
        return """
            ${displayProperty("model", "foo.barProcessed", "fooBuildModel.getBarProcessed().getOrNull()")}
        """
    }
}
