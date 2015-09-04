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
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.internal.model.BinarySpecFactoryRegistry;
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
import org.gradle.platform.base.internal.builder.TypeBuilderFactory;
import org.gradle.platform.base.internal.builder.TypeBuilderInternal;

import java.util.Arrays;
import java.util.List;

import static org.gradle.internal.Cast.uncheckedCast;

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
        if (implementation != null) {
            ModelAction<?> mutator = new RegistrationAction(type, implementation, ruleDefinition.getDescriptor());
            return new ExtractedModelAction(ModelActionRole.Defaults, dependencies, mutator);
        }
        return new DependencyOnlyExtractedModelRule(dependencies);
    }

    public static class DefaultBinaryTypeBuilder extends AbstractTypeBuilder<BinarySpec> implements BinaryTypeBuilder<BinarySpec> {
        public DefaultBinaryTypeBuilder(ModelSchema<? extends BinarySpec> schema) {
            super(BinaryType.class, schema);
        }
    }

    private static class RegistrationAction implements ModelAction<BinarySpecFactoryRegistry> {
        private final ModelType<? extends BinarySpec> publicType;
        private final ModelType<? extends BaseBinarySpec> implementationType;
        private final ModelRuleDescriptor descriptor;
        private final ModelReference<BinarySpecFactoryRegistry> subject;
        private final List<ModelReference<?>> inputs;

        public RegistrationAction(ModelType<? extends BinarySpec> publicType, ModelType<? extends BaseBinarySpec> implementationType, ModelRuleDescriptor descriptor) {
            this.publicType = publicType;
            this.implementationType = implementationType;
            this.descriptor = descriptor;
            this.subject = ModelReference.of(BinarySpecFactoryRegistry.class);
            this.inputs = Arrays.<ModelReference<?>>asList(ModelReference.of(ServiceRegistry.class), ModelReference.of(ITaskFactory.class));
        }

        @Override
        public ModelReference<BinarySpecFactoryRegistry> getSubject() {
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
        public void execute(MutableModelNode modelNode, BinarySpecFactoryRegistry factories, final List<ModelView<?>> inputs) {
            final Class<BinarySpec> publicClass = uncheckedCast(publicType.getConcreteClass());
            ServiceRegistry serviceRegistry = ModelViews.assertType(inputs.get(0), ModelType.of(ServiceRegistry.class)).getInstance();
            final Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            final ModelView<ITaskFactory> taskFactory = ModelViews.assertType(inputs.get(1), ModelType.of(ITaskFactory.class));
            NamedDomainObjectFactory<BaseBinarySpec> factory = new NamedDomainObjectFactory<BaseBinarySpec>() {
                public BaseBinarySpec create(String name) {
                    return BaseBinarySpec.create(implementationType.getConcreteClass(), name, instantiator, taskFactory.getInstance());
                }
            };
            factories.registerFactory(publicClass, factory, descriptor);
        }
    }
}

