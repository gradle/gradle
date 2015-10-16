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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.util.BiFunction;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.BinaryType;
import org.gradle.platform.base.BinaryTypeBuilder;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.binary.BaseBinarySpec;
import org.gradle.platform.base.binary.internal.BinarySpecFactory;
import org.gradle.platform.base.internal.BinarySpecInternal;
import org.gradle.platform.base.internal.ComponentSpecAware;
import org.gradle.platform.base.internal.builder.TypeBuilderFactory;
import org.gradle.platform.base.internal.builder.TypeBuilderInternal;

import java.util.List;
import java.util.Set;

public class BinaryTypeModelRuleExtractor extends TypeModelRuleExtractor<BinaryType, BinarySpec, BaseBinarySpec> {
    public BinaryTypeModelRuleExtractor(ModelSchemaStore schemaStore) {
        super("binary", BinarySpec.class, BaseBinarySpec.class, BinaryTypeBuilder.class, schemaStore, new TypeBuilderFactory<BinarySpec>() {
            @Override
            public TypeBuilderInternal<BinarySpec> create(ModelSchema<? extends BinarySpec> schema) {
                return new DefaultBinaryTypeBuilder(schema);
            }
        });
    }

    @Override
    protected <R, S> ExtractedModelRule createRegistration(MethodRuleDefinition<R, S> ruleDefinition, ModelType<? extends BinarySpec> type, TypeBuilderInternal<BinarySpec> builder) {
        ImmutableList<Class<?>> dependencies = ImmutableList.<Class<?>>of(ComponentModelBasePlugin.class);
        ModelType<? extends BaseBinarySpec> implementation = determineImplementationType(type, builder);
        ImmutableSet<ModelType<?>> internalViews = ImmutableSet.copyOf(Iterables.transform(builder.getInternalViews(), new Function<Class<?>, ModelType<?>>() {
            @Override
            public ModelType<?> apply(Class<?> type) {
                return ModelType.of(type);
            }
        }));
        ModelAction mutator = RegistrationAction.create(type, implementation, internalViews, ruleDefinition.getDescriptor());
        return new ExtractedModelAction(ModelActionRole.Defaults, dependencies, mutator);
    }

    public static class DefaultBinaryTypeBuilder extends AbstractTypeBuilder<BinarySpec> implements BinaryTypeBuilder<BinarySpec> {
        public DefaultBinaryTypeBuilder(ModelSchema<? extends BinarySpec> schema) {
            super(BinaryType.class, schema);
        }
    }

    private static class RegistrationAction<S extends BinarySpec> extends AbstractModelActionWithView<BinarySpecFactory> {
        private static final ModelType<BinarySpecInternal> BINARY_SPEC_INTERNAL_MODEL_TYPE = ModelType.of(BinarySpecInternal.class);

        private final ModelType<S> publicType;
        private final ModelType<? extends BaseBinarySpec> implementationType;
        private final Set<ModelType<?>> internalViews;

        public static <S extends BinarySpec> RegistrationAction<S> create(ModelType<S> publicType, ModelType<? extends BaseBinarySpec> implementationType, Set<ModelType<?>> internalViews, ModelRuleDescriptor descriptor) {
            return new RegistrationAction<S>(publicType, implementationType, internalViews, descriptor);
        }

        private RegistrationAction(ModelType<S> publicType, ModelType<? extends BaseBinarySpec> implementationType, Set<ModelType<?>> internalViews, ModelRuleDescriptor descriptor) {
            super(ModelReference.of(BinarySpecFactory.class), descriptor, ModelReference.of(ServiceRegistry.class), ModelReference.of(ITaskFactory.class));
            this.publicType = publicType;
            this.implementationType = implementationType;
            this.internalViews = internalViews;
        }

        @Override
        public void execute(MutableModelNode modelNode, BinarySpecFactory binaries, List<ModelView<?>> inputs) {
            InstanceFactory.TypeRegistrationBuilder<S> registration = binaries.register(publicType, descriptor);
            if (implementationType != null) {
                ServiceRegistry serviceRegistry = ModelViews.assertType(inputs.get(0), ModelType.of(ServiceRegistry.class)).getInstance();
                final Instantiator instantiator = serviceRegistry.get(Instantiator.class);
                final ITaskFactory taskFactory = ModelViews.assertType(inputs.get(1), ModelType.of(ITaskFactory.class)).getInstance();
                registration.withImplementation(Cast.<ModelType<? extends S>>uncheckedCast(implementationType), new BiFunction<S, String, MutableModelNode>() {
                    @Override
                    public S apply(String name, MutableModelNode modelNode1) {
                        S binarySpec = Cast.uncheckedCast(BaseBinarySpec.create(
                            publicType.getConcreteClass(), implementationType.getConcreteClass(), name, instantiator, taskFactory));
                        final Object parentObject = modelNode1.getParent().getParent().getPrivateData();
                        if (parentObject instanceof ComponentSpec && binarySpec instanceof ComponentSpecAware) {
                            ((ComponentSpecAware) binarySpec).setComponent((ComponentSpec) parentObject);
                        }
                        return binarySpec;
                    }
                });
                binaries.registerDomainObjectFactory(publicType.getConcreteClass(), descriptor, new NamedDomainObjectFactory<S>() {
                    public S create(String name) {
                        return Cast.uncheckedCast(BaseBinarySpec.create(publicType.getConcreteClass(), implementationType.getConcreteClass(), name, instantiator, taskFactory));
                    }
                });
                if (BINARY_SPEC_INTERNAL_MODEL_TYPE.isAssignableFrom(implementationType)) {
                    registration.withInternalView(BINARY_SPEC_INTERNAL_MODEL_TYPE);
                }
            }
            for (ModelType<?> internalView : internalViews) {
                registration.withInternalView(internalView);
            }
        }

    }
}
