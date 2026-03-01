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

/**
 * A {@link ProjectTypeDefinitionClassBuilder} for creating a project type definition that has both a public type and an implementation type.
 */
class ProjectTypeDefinitionWithPublicAndImplementationTypesClassBuilder extends ProjectTypeDefinitionClassBuilder {
    ProjectTypeDefinitionWithPublicAndImplementationTypesClassBuilder() {
        this.hasImplementationType = true
    }

    @Override
    String getImplementationTypeClassContent() {
        return """
            package org.gradle.test;

            import org.gradle.api.Action;
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;

            import javax.inject.Inject;

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

                public abstract Property<String> getNonPublic();
            }
        """
    }
}
