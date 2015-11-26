/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.fixture;

import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.internal.DefaultPolymorphicNamedEntityInstantiator;
import org.gradle.api.internal.rules.DefaultRuleAwarePolymorphicNamedEntityInstantiator;
import org.gradle.api.internal.rules.RuleAwarePolymorphicNamedEntityInstantiator;
import org.gradle.internal.Actions;
import org.gradle.internal.BiAction;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.model.collection.internal.ChildNodeInitializerStrategyAccessors;
import org.gradle.model.collection.internal.ModelMapModelProjection;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

import java.util.List;

public class ModelRegistrationBuilder {
    private final ModelPath path;
    private ModelRuleDescriptor descriptor = new SimpleModelRuleDescriptor("tester");

    public ModelRegistrationBuilder(ModelPath path) {
        this.path = path;
        descriptor = new SimpleModelRuleDescriptor(path + " creator");
    }

    public ModelRegistrationBuilder descriptor(String descriptor) {
        return descriptor(new SimpleModelRuleDescriptor(descriptor));
    }

    public ModelRegistrationBuilder descriptor(ModelRuleDescriptor descriptor) {
        this.descriptor = descriptor;
        return this;
    }

    public <C> ModelRegistration unmanaged(final Class<C> modelType, String inputPath, final Transformer<? extends C, Object> action) {
        return unmanaged(modelType, inputPath, inputPath, action);
    }

    public <C> ModelRegistration unmanaged(final Class<C> modelType, String inputPath, String referenceDescription, final Transformer<? extends C, Object> action) {
        return unmanaged(ModelType.of(modelType), inputPath, referenceDescription, action);
    }

    public <C> ModelRegistration unmanaged(final ModelType<C> modelType, String inputPath, final Transformer<? extends C, Object> action) {
        return unmanaged(modelType, inputPath, inputPath, action);
    }

    public <C> ModelRegistration unmanaged(final ModelType<C> modelType, String inputPath, String inputDescriptor, final Transformer<? extends C, Object> action) {
        return ModelRegistrations.of(
            path,
            ModelReference.of(inputPath, ModelType.UNTYPED, inputDescriptor),
            new BiAction<MutableModelNode, List<ModelView<?>>>() {
                @Override
                public void execute(MutableModelNode mutableModelNode, List<ModelView<?>> inputs) {
                    mutableModelNode.setPrivateData(modelType, action.transform(inputs.get(0).getInstance()));
                }
            })
            .withProjection(new UnmanagedModelProjection<C>(modelType, true, true))
            .descriptor(descriptor)
            .build();
    }

    public <C, I> ModelRegistration unmanaged(Class<C> type, final Class<I> inputType, final Transformer<? extends C, ? super I> action) {
        return unmanaged(ModelType.of(type), ModelType.of(inputType), action);
    }

    public <C, I> ModelRegistration unmanaged(final ModelType<C> modelType, final ModelType<I> inputModelType, final Transformer<? extends C, ? super I> action) {
        return ModelRegistrations.of(
            path,
            ModelReference.of(inputModelType),
            new BiAction<MutableModelNode, List<ModelView<?>>>() {
                @Override
                public void execute(MutableModelNode mutableModelNode, List<ModelView<?>> inputs) {
                    mutableModelNode.setPrivateData(modelType, action.transform(ModelViews.assertType(inputs.get(0), inputModelType).getInstance()));
                }
            })
            .withProjection(new UnmanagedModelProjection<C>(modelType, true, true))
            .descriptor(descriptor)
            .build();
    }

    public <C> ModelRegistration unmanaged(Class<C> type, final Factory<? extends C> initializer) {
        return unmanaged(ModelType.of(type), initializer);
    }

    public <C> ModelRegistration unmanaged(Class<C> type, final C c) {
        return unmanaged(ModelType.of(type), Factories.constant(c));
    }

    private <C> ModelRegistration unmanaged(final ModelType<C> modelType, final Factory<? extends C> initializer) {
        return ModelRegistrations.of(
            path,
            new Action<MutableModelNode>() {
                @Override
                public void execute(MutableModelNode mutableModelNode) {
                    mutableModelNode.setPrivateData(modelType, initializer.create());
                }
            })
            .withProjection(new UnmanagedModelProjection<C>(modelType, true, true))
            .descriptor(descriptor)
            .build();
    }

    public <C> ModelRegistration unmanagedNode(Class<C> modelType, final Action<? super MutableModelNode> action) {
        return unmanagedNode(ModelType.of(modelType), action);
    }

    public <C> ModelRegistration unmanagedNode(ModelType<C> modelType, final Action<? super MutableModelNode> action) {
        return ModelRegistrations.of(path, action)
            .withProjection(new UnmanagedModelProjection<C>(modelType, true, true))
            .descriptor(descriptor)
            .build();
    }

    public <C> ModelRegistration unmanaged(C c) {
        return unmanaged(c, Actions.doNothing());
    }

    public <C> ModelRegistration unmanaged(final C c, final Action<? super C> action) {
        return unmanaged(ModelType.typeOf(c).getConcreteClass(), new Factory<C>() {
            @Override
            public C create() {
                action.execute(c);
                return c;
            }
        });
    }

    public <I> ModelRegistration modelMap(final Class<I> itemType) {
        final ModelType<RuleAwarePolymorphicNamedEntityInstantiator<I>> instantiatorType = ModelRegistryHelper.instantiatorType(itemType);

        ModelType<I> modelType = ModelType.of(itemType);
        return ModelRegistrations.of(
                ModelReference.of(path, instantiatorType),
                new Factory<RuleAwarePolymorphicNamedEntityInstantiator<I>>() {
                    @Override
                    public RuleAwarePolymorphicNamedEntityInstantiator<I> create() {
                        return new DefaultRuleAwarePolymorphicNamedEntityInstantiator<I>(
                            new DefaultPolymorphicNamedEntityInstantiator<I>(itemType, "this collection")
                        );
                    }
                }
            )
            .withProjection(ModelMapModelProjection.unmanaged(
                modelType,
                ChildNodeInitializerStrategyAccessors.of(NodeBackedModelMap.createUsingParentNode(modelType)))
            )
            .withProjection(UnmanagedModelProjection.of(instantiatorType))
            .descriptor(descriptor)
            .build();
    }
}
