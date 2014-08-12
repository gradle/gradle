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
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.model.internal.core.ModelType;
import org.gradle.runtime.base.*;
import org.gradle.runtime.base.component.DefaultComponentSpec;
import org.gradle.runtime.base.internal.DefaultComponentSpecIdentifier;

public class ComponentModelRuleDefinitionHandler extends AbstractAnnotationModelRuleDefinitionHandler<ComponentSpec, DefaultComponentSpec> {

    private Instantiator instantiator;

    public ComponentModelRuleDefinitionHandler(Instantiator instantiator) {
        super("component", ComponentType.class, ComponentSpec.class, DefaultComponentSpec.class, ComponentTypeBuilder.class);
        this.instantiator = instantiator;
    }

    @Override
    protected Action<MutationActionParameter> createMutationAction(Class<? extends ComponentSpec> type, Class<? extends DefaultComponentSpec> implementation) {
        return new ComponentTypeRuleMutationAction(instantiator, type, implementation);
    }

    @Override
    protected TypeBuilder createBuilder() {
        return new MyComponentTypeBuilder();
    }

    private static class MyComponentTypeBuilder<T extends ComponentSpec> extends AbstractTypeBuilder<T> implements ComponentTypeBuilder<T> {
        public MyComponentTypeBuilder() {
            super(ComponentType.class);
        }
    }

    private static class ComponentTypeRuleMutationAction implements Action<MutationActionParameter> {
        private final Instantiator instantiator;
        private final Class<? extends ComponentSpec> type;
        private final Class<? extends DefaultComponentSpec> implementation;

        public ComponentTypeRuleMutationAction(Instantiator instantiator, Class<? extends ComponentSpec> type, Class<? extends DefaultComponentSpec> implementation) {
            this.instantiator = instantiator;
            this.type = type;
            this.implementation = implementation;
        }

        public void execute(MutationActionParameter mp) {
            final ProjectSourceSet projectSourceSet = mp.extensions.getByType(ProjectSourceSet.class);
            ComponentSpecContainer componentSpecs = mp.extensions.getByType(ComponentSpecContainer.class);
            final ProjectIdentifier projectIdentifier = mp.inputs.get(0, ModelType.of(ProjectIdentifier.class)).getInstance();
            componentSpecs.registerFactory(type, new NamedDomainObjectFactory() {
                public Object create(String name) {
                    FunctionalSourceSet componentSourceSet = projectSourceSet.maybeCreate(name);
                    ComponentSpecIdentifier id = new DefaultComponentSpecIdentifier(projectIdentifier.getPath(), name);
                    return DefaultComponentSpec.create(implementation, id, componentSourceSet, instantiator);
                }
            });
        }

    }
}
