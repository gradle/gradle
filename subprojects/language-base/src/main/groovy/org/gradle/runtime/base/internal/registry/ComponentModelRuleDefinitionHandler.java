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

import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.model.internal.core.Inputs;
import org.gradle.model.internal.core.ModelMutator;
import org.gradle.model.internal.core.ModelType;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.runtime.base.*;
import org.gradle.runtime.base.component.BaseComponentSpec;
import org.gradle.runtime.base.internal.DefaultComponentSpecIdentifier;

public class ComponentModelRuleDefinitionHandler extends AbstractAnnotationModelRuleDefinitionHandler<ComponentSpec, BaseComponentSpec> {

    private Instantiator instantiator;

    public ComponentModelRuleDefinitionHandler(Instantiator instantiator) {
        super("component", ComponentType.class, ComponentSpec.class, BaseComponentSpec.class, ComponentTypeBuilder.class);
        this.instantiator = instantiator;
    }

    @Override
    protected ModelMutator<ExtensionContainer> createModelMutator(ModelRuleDescriptor descriptor, Class<? extends ComponentSpec> type, Class<? extends BaseComponentSpec> implementation) {
        return new ComponentTypeRuleMutationAction(descriptor, instantiator, type, implementation);
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

    private static class ComponentTypeRuleMutationAction extends RegisterTypeRule {
        private final Instantiator instantiator;
        private final Class<? extends ComponentSpec> type;
        private final Class<? extends BaseComponentSpec> implementation;

        public ComponentTypeRuleMutationAction(ModelRuleDescriptor descriptor, Instantiator instantiator, Class<? extends ComponentSpec> type, Class<? extends BaseComponentSpec> implementation) {
            super(descriptor);
            this.instantiator = instantiator;
            this.type = type;
            this.implementation = implementation;
        }

        public void mutate(ExtensionContainer extensions, Inputs inputs) {
            final ProjectSourceSet projectSourceSet = extensions.getByType(ProjectSourceSet.class);
            ComponentSpecContainer componentSpecs = extensions.getByType(ComponentSpecContainer.class);
            final ProjectIdentifier projectIdentifier = inputs.get(0, ModelType.of(ProjectIdentifier.class)).getInstance();
            componentSpecs.registerFactory(type, new NamedDomainObjectFactory() {
                public Object create(String name) {
                    FunctionalSourceSet componentSourceSet = projectSourceSet.maybeCreate(name);
                    ComponentSpecIdentifier id = new DefaultComponentSpecIdentifier(projectIdentifier.getPath(), name);
                    return BaseComponentSpec.create(implementation, id, componentSourceSet, instantiator);
                }
            });
        }

    }
}
