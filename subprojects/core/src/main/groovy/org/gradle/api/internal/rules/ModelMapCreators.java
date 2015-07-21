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

package org.gradle.api.internal.rules;

import org.gradle.api.internal.DefaultPolymorphicNamedEntityInstantiator;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.model.ModelMap;
import org.gradle.model.collection.internal.PolymorphicModelMapProjection;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

public class ModelMapCreators {

    public static <T, C extends ModelMap<T>> ModelCreator specialized(ModelPath path,
                                                                      Class<T> typeClass,
                                                                      Class<C> containerClass,
                                                                      Class<? extends C> viewClass,
                                                                      ModelReference<? extends InstanceFactory<? super T, String>> factoryReference,
                                                                      ModelRuleDescriptor descriptor) {

        ChildNodeInitializerStrategy<T> childFactory = NodeBackedModelMap.createUsingFactory(factoryReference);

        ModelType<C> containerType = ModelType.of(containerClass);
        ModelType<T> modelType = ModelType.of(typeClass);
        return ModelCreators.of(ModelReference.of(path, containerType), Factories.<C>constantNull())
            .descriptor(descriptor)
            .withProjection(new SpecializedModelMapProjection<C, T>(containerType, modelType, viewClass, childFactory))
            .withProjection(PolymorphicModelMapProjection.of(modelType, childFactory))
            .build();
    }

    public static <T> ModelCreators.Builder of(ModelPath path, final Class<T> typeClass) {

        final ModelType<RuleAwarePolymorphicNamedEntityInstantiator<T>> instantiatorType = instantiatorType(typeClass);

        ModelType<T> modelType = ModelType.of(typeClass);
        return ModelCreators.of(
            ModelReference.of(path, instantiatorType),
            new Factory<RuleAwarePolymorphicNamedEntityInstantiator<T>>() {
                @Override
                public RuleAwarePolymorphicNamedEntityInstantiator<T> create() {
                    return new DefaultRuleAwarePolymorphicNamedEntityInstantiator<T>(
                        new DefaultPolymorphicNamedEntityInstantiator<T>(typeClass, "this collection")
                    );
                }
            }
        )
            .withProjection(PolymorphicModelMapProjection.of(modelType, NodeBackedModelMap.createUsingParentNode(modelType)))
            .withProjection(UnmanagedModelProjection.of(instantiatorType));
    }

    public static <T> ModelType<RuleAwarePolymorphicNamedEntityInstantiator<T>> instantiatorType(Class<T> typeClass) {
        return new ModelType.Builder<RuleAwarePolymorphicNamedEntityInstantiator<T>>() {
        }.where(new ModelType.Parameter<T>() {
        }, ModelType.of(typeClass)).build();
    }

}
