/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.runtime.base.internal.registry;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.model.internal.core.ModelType;
import org.gradle.runtime.base.*;
import org.gradle.runtime.base.component.BaseComponentSpec;
import org.gradle.runtime.base.internal.DefaultComponentSpecIdentifier;

public class ComponentTypeRuleDefinitionHandler extends AbstractComponentModelRuleDefinitionHandler<ComponentType, ComponentSpec, BaseComponentSpec> {

    private Instantiator instantiator;

    public ComponentTypeRuleDefinitionHandler(Instantiator instantiator) {
        super("component", ComponentType.class, ComponentSpec.class, BaseComponentSpec.class, ComponentTypeBuilder.class);
        this.instantiator = instantiator;
    }

    @Override
    protected <V extends ComponentSpec, W extends BaseComponentSpec> Action<? super TypeRegistrationContext> createTypeRegistrationAction(final ModelType<V> type, final ModelType<W> implementation) {
        return new Action<TypeRegistrationContext>() {
            public void execute(final TypeRegistrationContext context) {
                ExtensionContainer extensions = context.getExtensions();
                final ProjectSourceSet projectSourceSet = extensions.getByType(ProjectSourceSet.class);
                ComponentSpecContainer componentSpecs = extensions.getByType(ComponentSpecContainer.class);
                componentSpecs.registerFactory(type.getConcreteClass(), new NamedDomainObjectFactory<V>() {
                    public V create(String name) {
                        FunctionalSourceSet componentSourceSet = projectSourceSet.maybeCreate(name);
                        ComponentSpecIdentifier id = new DefaultComponentSpecIdentifier(context.getProjectIdentifier().getPath(), name);

                        // safe because we implicitly know that U extends V, but can't express this in the type system
                        @SuppressWarnings("unchecked")
                        V created = (V) BaseComponentSpec.create(implementation.getConcreteClass(), id, componentSourceSet, instantiator);

                        return created;
                    }
                });
            }
        };
    }

    @Override
    protected TypeBuilderInternal createBuilder() {
        return new DefaultComponentTypeBuilder();
    }

    private static class DefaultComponentTypeBuilder<T extends ComponentSpec> extends AbstractTypeBuilder<T> implements ComponentTypeBuilder<T> {
        public DefaultComponentTypeBuilder() {
            super(ComponentType.class);
        }
    }

}
