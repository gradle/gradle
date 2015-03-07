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

package org.gradle.platform.base.internal.registry;

import com.google.common.collect.ImmutableList;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.internal.DefaultFunctionalSourceSet;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.ComponentSpecIdentifier;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.ComponentTypeBuilder;
import org.gradle.platform.base.component.BaseComponentSpec;
import org.gradle.platform.base.internal.DefaultComponentSpecContainer;
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier;
import org.gradle.platform.base.internal.builder.TypeBuilderInternal;

import java.util.Arrays;
import java.util.List;

public class ComponentTypeModelRuleExtractor extends TypeModelRuleExtractor<ComponentType, ComponentSpec, BaseComponentSpec> {

    private Instantiator instantiator;

    public ComponentTypeModelRuleExtractor(final Instantiator instantiator) {
        super("component", ComponentSpec.class, BaseComponentSpec.class, ComponentTypeBuilder.class, JavaReflectionUtil.factory(DirectInstantiator.INSTANCE, DefaultComponentTypeBuilder.class));
        this.instantiator = instantiator;
    }

    @Override
    protected <R, S> ExtractedModelRule createRegistration(MethodRuleDefinition<R, S> ruleDefinition, ModelType<? extends ComponentSpec> type, TypeBuilderInternal<ComponentSpec> builder) {
        ImmutableList<Class<?>> dependencies = ImmutableList.<Class<?>>of(ComponentModelBasePlugin.class);
        ModelType<? extends BaseComponentSpec> implementation = determineImplementationType(type, builder);
        if (implementation != null) {
            ModelAction<?> mutator = new RegistrationAction(type, implementation, ruleDefinition.getDescriptor(), instantiator);
            return new ExtractedModelAction(ModelActionRole.Defaults, dependencies, mutator);
        }
        return new DependencyOnlyExtractedModelRule(dependencies);
    }

    public static class DefaultComponentTypeBuilder extends AbstractTypeBuilder<ComponentSpec> implements ComponentTypeBuilder<ComponentSpec> {
        public DefaultComponentTypeBuilder() {
            super(ComponentType.class);
        }
    }

    private static class RegistrationAction implements ModelAction<DefaultComponentSpecContainer> {
        private final ModelType<? extends ComponentSpec> publicType;
        private final ModelType<? extends BaseComponentSpec> implementationType;
        private final ModelRuleDescriptor descriptor;
        private final Instantiator instantiator;
        private final ModelReference<DefaultComponentSpecContainer> subject;
        private final List<ModelReference<?>> inputs;

        public RegistrationAction(ModelType<? extends ComponentSpec> publicType, ModelType<? extends BaseComponentSpec> implementationType, ModelRuleDescriptor descriptor, Instantiator instantiator) {
            this.publicType = publicType;
            this.implementationType = implementationType;
            this.descriptor = descriptor;
            this.instantiator = instantiator;
            this.subject = ModelReference.of(DefaultComponentSpecContainer.class);
            this.inputs = Arrays.<ModelReference<?>>asList(ModelReference.of(ProjectIdentifier.class), ModelReference.of(ProjectSourceSet.class));
        }

        @Override
        public ModelReference<DefaultComponentSpecContainer> getSubject() {
            return subject;
        }

        @Override
        public ModelRuleDescriptor getDescriptor() {
            return descriptor;
        }

        @Override
        public List<ModelReference<?>> getInputs() {
            return inputs;
        }

        @Override
        public void execute(MutableModelNode modelNode, DefaultComponentSpecContainer components, List<ModelView<?>> inputs) {
            final ProjectIdentifier projectIdentifier = ModelViews.assertType(inputs.get(0), ModelType.of(ProjectIdentifier.class)).getInstance();
            final ProjectSourceSet projectSourceSet = ModelViews.assertType(inputs.get(1), ModelType.of(ProjectSourceSet.class)).getInstance();
            @SuppressWarnings("unchecked")
            Class<ComponentSpec> publicClass = (Class<ComponentSpec>) publicType.getConcreteClass();
            components.registerFactory(publicClass, new NamedDomainObjectFactory<BaseComponentSpec>() {
                public BaseComponentSpec create(String name) {
                    FunctionalSourceSet componentSourceSet = instantiator.newInstance(DefaultFunctionalSourceSet.class, name, instantiator, projectSourceSet);
                    ComponentSpecIdentifier id = new DefaultComponentSpecIdentifier(projectIdentifier.getPath(), name);
                    return BaseComponentSpec.create(implementationType.getConcreteClass(), id, componentSourceSet, instantiator);
                }
            }, descriptor);
        }
    }
}
