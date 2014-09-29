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

package org.gradle.model.internal.core;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Transformer;
import org.gradle.internal.Factory;
import org.gradle.internal.Transformers;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;

import java.util.Collections;
import java.util.List;

abstract public class ModelCreators {

    public static <T> Builder<T> of(ModelReference<? super T> modelReference, T instance) {
        return new Builder<T>(modelReference, Transformers.<T, Inputs>constant(instance)).withIdentityProjection();
    }

    public static <T> Builder<T> of(ModelReference<? super T> modelReference, Factory<? extends T> factory) {
        return new Builder<T>(modelReference, Transformers.toTransformer(factory)).withIdentityProjection();
    }

    public static <T> Builder<T> of(ModelReference<? super T> modelReference, Transformer<? extends T, ? super Inputs> transformer) {
        return new Builder<T>(modelReference, transformer).withIdentityProjection();
    }

    public static class Builder<T> {

        private final Transformer<? extends T, ? super Inputs> transformer;
        private final ModelReference<? super T> modelReference;
        private final ImmutableList.Builder<ModelProjection<? super T>> projections = ImmutableList.builder();

        private ModelRuleDescriptor modelRuleDescriptor;
        private List<ModelReference<?>> inputs = Collections.emptyList();

        private Builder(ModelReference<? super T> modelReference, Transformer<? extends T, ? super Inputs> transformer) {
            this.modelReference = modelReference;
            this.transformer = transformer;
        }

        public Builder<T> simpleDescriptor(String descriptor) {
            this.modelRuleDescriptor = new SimpleModelRuleDescriptor(descriptor);
            return this;
        }

        public Builder<T> descriptor(ModelRuleDescriptor descriptor) {
            this.modelRuleDescriptor = descriptor;
            return this;
        }

        public Builder<T> inputs(List<ModelReference<?>> inputs) {
            this.inputs = inputs;
            return this;
        }

        public Builder<T> withIdentityProjection() {
            projections.add(new IdentityModelProjection<T>(modelReference.getType(), true, true));
            return this;
        }

        public Builder<T> withProjection(ModelProjection<? super T> projection) {
            projections.add(projection);
            return this;
        }

        public ModelCreator build() {
            return new ProjectionBackedModelCreator<T>(modelReference.getPath(), modelRuleDescriptor, inputs, projections.build(), transformer);
        }
    }
}
