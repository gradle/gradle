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
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.util.BiFunction;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.internal.registry.LanguageRegistry;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.ComponentSpecIdentifier;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.ComponentTypeBuilder;
import org.gradle.platform.base.component.BaseComponentSpec;
import org.gradle.platform.base.component.internal.ComponentSpecFactory;
import org.gradle.platform.base.internal.ComponentSpecInternal;
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier;
import org.gradle.platform.base.internal.builder.TypeBuilderFactory;
import org.gradle.platform.base.internal.builder.TypeBuilderInternal;

import java.util.List;
import java.util.Set;

public class ComponentTypeModelRuleExtractor extends TypeModelRuleExtractor<ComponentType, ComponentSpec, BaseComponentSpec> {
    public ComponentTypeModelRuleExtractor(ModelSchemaStore schemaStore) {
        super("component", ComponentSpec.class, BaseComponentSpec.class, ComponentTypeBuilder.class, schemaStore, new TypeBuilderFactory<ComponentSpec>() {
            @Override
            public TypeBuilderInternal<ComponentSpec> create(ModelSchema<? extends ComponentSpec> schema) {
                return new DefaultComponentTypeBuilder(schema);
            }
        });
    }

    @Override
    protected <R, S> ExtractedModelRule createRegistration(MethodRuleDefinition<R, S> ruleDefinition, ModelType<? extends ComponentSpec> type, TypeBuilderInternal<ComponentSpec> builder) {
        List<Class<?>> dependencies = ImmutableList.<Class<?>>of(ComponentModelBasePlugin.class);
        ModelType<? extends BaseComponentSpec> implementation = determineImplementationType(type, builder);
        ModelAction mutator = RegistrationAction.create(type, implementation, builder.getInternalViews(), ruleDefinition.getDescriptor());
        return new ExtractedModelAction(ModelActionRole.Defaults, dependencies, mutator);
    }

    public static class DefaultComponentTypeBuilder extends AbstractTypeBuilder<ComponentSpec> implements ComponentTypeBuilder<ComponentSpec> {
        public DefaultComponentTypeBuilder(ModelSchema<? extends ComponentSpec> schema) {
            super(ComponentType.class, schema);
        }
    }

    private static class RegistrationAction<S extends ComponentSpec> extends AbstractModelActionWithView<ComponentSpecFactory> {
        private static final ModelType<ComponentSpecInternal> COMPONENT_SPEC_INTERNAL_MODEL_TYPE = ModelType.of(ComponentSpecInternal.class);

        private final ModelType<S> publicType;
        private final ModelType<? extends BaseComponentSpec> implementationType;
        private final Set<Class<?>> internalViews;

        public static <S extends ComponentSpec> RegistrationAction<S> create(ModelType<S> publicType, ModelType<? extends BaseComponentSpec> implementationType, Set<Class<?>> internalViews, ModelRuleDescriptor descriptor) {
            return new RegistrationAction<S>(publicType, implementationType, internalViews, descriptor);
        }

        private RegistrationAction(ModelType<S> publicType, ModelType<? extends BaseComponentSpec> implementationType, Set<Class<?>> internalViews, ModelRuleDescriptor descriptor) {
            super(ModelReference.of(ComponentSpecFactory.class), descriptor,
                ModelReference.of("serviceRegistry", ServiceRegistry.class),
                ModelReference.of("projectIdentifier", ProjectIdentifier.class),
                ModelReference.of("sources", ProjectSourceSet.class),
                ModelReference.of("languages", LanguageRegistry.class));
            this.publicType = publicType;
            this.implementationType = implementationType;
            this.internalViews = internalViews;
        }

        @Override
        protected void execute(MutableModelNode modelNode, ComponentSpecFactory components, List<ModelView<?>> inputs) {
            InstanceFactory.TypeRegistrationBuilder<S> registration = components.register(publicType, descriptor);
            if (implementationType != null) {
                ServiceRegistry serviceRegistry = ModelViews.assertType(inputs.get(0), ModelType.of(ServiceRegistry.class)).getInstance();
                final Instantiator instantiator = serviceRegistry.get(Instantiator.class);
                final ProjectIdentifier projectIdentifier = ModelViews.assertType(inputs.get(1), ModelType.of(ProjectIdentifier.class)).getInstance();
                final ProjectSourceSet projectSourceSet = ModelViews.assertType(inputs.get(2), ModelType.of(ProjectSourceSet.class)).getInstance();
                final LanguageRegistry languageRegistry = ModelViews.assertType(inputs.get(3), ModelType.of(LanguageRegistry.class)).getInstance();

                registration.withImplementation(Cast.<ModelType<? extends S>>uncheckedCast(implementationType), new BiFunction<S, String, MutableModelNode>() {
                    @Override
                    public S apply(String name, MutableModelNode modelNode1) {
                        ComponentSpecIdentifier id = new DefaultComponentSpecIdentifier(projectIdentifier.getPath(), name);
                        return Cast.uncheckedCast(BaseComponentSpec.create(implementationType.getConcreteClass(), id, modelNode1, instantiator, languageRegistry, projectIdentifier.getProjectDir()));
                    }
                });
                if (COMPONENT_SPEC_INTERNAL_MODEL_TYPE.isAssignableFrom(implementationType)) {
                    registration.withInternalView(COMPONENT_SPEC_INTERNAL_MODEL_TYPE);
                }
            }
            for (Class<?> internalView : internalViews) {
                registration.withInternalView(ModelType.of(internalView));
            }
        }
    }
}
