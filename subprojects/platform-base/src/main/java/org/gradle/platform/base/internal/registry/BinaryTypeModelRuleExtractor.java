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
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
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
import org.gradle.platform.base.binary.BaseBinarySpec;
import org.gradle.platform.base.binary.internal.BinarySpecFactory;
import org.gradle.platform.base.internal.ComponentSpecInternal;
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
        public void execute(MutableModelNode binariesNode, BinarySpecFactory binaries, List<ModelView<?>> inputs) {
            InstanceFactory.TypeRegistrationBuilder<S> registration = binaries.register(publicType, descriptor);
            if (implementationType != null) {
                ServiceRegistry serviceRegistry = ModelViews.assertType(inputs.get(0), ModelType.of(ServiceRegistry.class)).getInstance();
                final Instantiator instantiator = serviceRegistry.get(Instantiator.class);
                final ITaskFactory taskFactory = ModelViews.assertType(inputs.get(1), ModelType.of(ITaskFactory.class)).getInstance();
                registration.withImplementation(Cast.<ModelType<? extends S>>uncheckedCast(implementationType), new InstanceFactory.ImplementationFactory<S>() {
                    @Override
                    public S create(ModelType<? extends S> publicType, String name, MutableModelNode binaryNode) {
                        MutableModelNode parentNode = binaryNode.getParent().getParent();
                        ComponentSpecInternal owner = parentNode.canBeViewedAs(ModelType.of(ComponentSpecInternal.class))
                            ? parentNode.asImmutable(ModelType.of(ComponentSpecInternal.class), descriptor).getInstance()
                            : null;
                        return Cast.uncheckedCast(BaseBinarySpec.create(publicType.getConcreteClass(),
                                implementationType.getConcreteClass(),
                                name,
                                binaryNode,
                                owner,
                                instantiator,
                                taskFactory));
                    }
                });
            }
            for (ModelType<?> internalView : internalViews) {
                registration.withInternalView(internalView);
            }
        }

    }
}
