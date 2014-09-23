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

package org.gradle.api.internal;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Transformer;
import org.gradle.internal.*;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.Collections;
import java.util.List;

abstract public class ModelCreators {

    public static <I, C extends PolymorphicDomainObjectContainerInternal<I>> ModelCreator forPolymorphicDomainObjectContainer(ModelReference<? super C> modelReference, Class<I> itemClass, C container,
                                                                                                                              ModelRuleDescriptor descriptor) {
        ModelProjection<C> identityProjection = new IdentityModelProjection<C>(modelReference.getType(), true, true);
        ModelProjection<C> containerProjection = new PolymorphicDomainObjectContainerModelProjection<I, C>(
                container, itemClass
        );

        List<ModelProjection<C>> projections = ImmutableList.of(
                identityProjection, containerProjection
        );

        return new DefaultModelCreator<C>(modelReference.getPath(), descriptor,
                Collections.<ModelReference<?>>emptyList(), projections, Transformers.<C, Inputs>constant(container));
    }

    public static <T> ModelCreator forInstance(ModelReference<T> reference, ModelRuleDescriptor sourceDescriptor, T instance) {
        return forTransformer(reference, sourceDescriptor, Collections.<ModelReference<?>>emptyList(), Transformers.<T, Inputs>constant(instance));
    }

    public static <T> ModelCreator forFactory(ModelReference<T> reference, ModelRuleDescriptor sourceDescriptor, org.gradle.internal.Factory<T> factory) {
        return forTransformer(reference, sourceDescriptor, Collections.<ModelReference<?>>emptyList(), Transformers.toTransformer(factory));
    }

    public static <T> ModelCreator forFactory(ModelReference<T> reference, ModelRuleDescriptor sourceDescriptor, List<ModelReference<?>> inputs, org.gradle.internal.Factory<T> factory) {
        return forTransformer(reference, sourceDescriptor, inputs, Transformers.toTransformer(factory));
    }

    public static <T> ModelCreator forTransformer(ModelReference<T> reference, ModelRuleDescriptor sourceDescriptor, List<ModelReference<?>> inputs, Transformer<T, ? super Inputs> transformer) {
        return DefaultModelCreator.forTransformer(reference, sourceDescriptor, inputs, transformer);
    }
}
