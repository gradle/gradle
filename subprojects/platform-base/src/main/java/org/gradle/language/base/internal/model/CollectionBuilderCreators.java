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

package org.gradle.language.base.internal.model;

import org.gradle.api.Transformer;
import org.gradle.api.internal.DefaultPolymorphicNamedEntityInstantiator;
import org.gradle.api.internal.rules.RuleAwareNamedDomainObjectFactoryRegistry;
import org.gradle.internal.BiAction;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.model.collection.internal.PolymorphicCollectionBuilderProjection;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.internal.rules.DefaultRuleAwarePolymorphicNamedEntityInstantiator;
import org.gradle.platform.base.internal.rules.RuleAwarePolymorphicNamedEntityInstantiator;

import java.util.List;

public class CollectionBuilderCreators {

    public static <T, C extends CollectionBuilder<T>> ModelCreator specialized(String name, final Class<T> typeClass,
                                                                               final Class<C> containerClass,
                                                                               Transformer<? extends C, ? super CollectionBuilder<T>> collectionBuilderFactory,
                                                                               String descriptor,
                                                                               BiAction<? super MutableModelNode, ? super T> initializeAction) {


        ModelType<RuleAwareNamedDomainObjectFactoryRegistry<T>> factoryRegistryType = new ModelType.Builder<RuleAwareNamedDomainObjectFactoryRegistry<T>>() {
        }.where(new ModelType.Parameter<T>() {
        }, ModelType.of(typeClass)).build();

        ModelReference<CollectionBuilder<T>> containerReference = ModelReference.of(name, DefaultCollectionBuilder.typeOf(typeClass));

        PolymorphicCollectionBuilderProjection<T> collectionBuilderProjection = new PolymorphicCollectionBuilderProjection<T>(ModelType.of(typeClass), initializeAction);

        return ModelCreators.of(containerReference, new BiAction<MutableModelNode, List<ModelView<?>>>() {
            @Override
            public void execute(MutableModelNode mutableModelNode, List<ModelView<?>> modelViews) {
                final DefaultPolymorphicNamedEntityInstantiator<T> namedEntityInstantiator = new DefaultPolymorphicNamedEntityInstantiator<T>(
                        typeClass, "this collection"
                );
                ModelType<RuleAwarePolymorphicNamedEntityInstantiator<T>> instantiatorType = new ModelType.Builder<RuleAwarePolymorphicNamedEntityInstantiator<T>>() {
                }.where(new ModelType.Parameter<T>() {
                }, ModelType.of(typeClass)).build();

                mutableModelNode.setPrivateData(instantiatorType, new DefaultRuleAwarePolymorphicNamedEntityInstantiator<T>(namedEntityInstantiator));
            }
        })
                .descriptor(descriptor)
                .ephemeral(true)
                .withProjection(collectionBuilderProjection)
                .withProjection(new SpecializedCollectionBuilderProjection<C, T>(ModelType.of(containerClass), ModelType.of(typeClass), collectionBuilderProjection, collectionBuilderFactory))
                .withProjection(new UnmanagedModelProjection<RuleAwareNamedDomainObjectFactoryRegistry<T>>(factoryRegistryType))
                .build();
    }
}
