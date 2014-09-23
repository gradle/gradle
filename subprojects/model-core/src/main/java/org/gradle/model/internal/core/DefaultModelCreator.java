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
import org.gradle.api.specs.Spec;
import org.gradle.internal.Transformers;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.util.CollectionUtils;

import java.util.List;

public class DefaultModelCreator<T> implements ModelCreator {

    private final List<? extends ModelProjection<? super T>> projections;
    private final ModelRuleDescriptor descriptor;
    private final List<ModelReference<?>> inputs;
    private final ModelPath path;
    private final Transformer<? extends T, ? super Inputs> transformer;

    public DefaultModelCreator(ModelPath path, ModelRuleDescriptor descriptor, List<ModelReference<?>> inputs, List<? extends ModelProjection<? super T>> projections,
                               final Transformer<? extends T, ? super Inputs> transformer) {
        this.transformer = transformer;
        this.projections = projections;
        this.path = path;
        this.descriptor = descriptor;
        this.inputs = inputs;
    }

    public static <T> ModelCreator forFactory(ModelReference<T> reference, ModelRuleDescriptor sourceDescriptor, List<ModelReference<?>> inputs, org.gradle.internal.Factory<T> factory) {
        return forTransformer(reference, sourceDescriptor, inputs, Transformers.toTransformer(factory));
    }

    public static <T> ModelCreator forTransformer(ModelReference<T> reference, ModelRuleDescriptor sourceDescriptor, List<ModelReference<?>> inputs, Transformer<T, ? super Inputs> transformer) {
        ModelProjection<T> identityProjection = new IdentityModelProjection<T>(reference.getType(), true, true);
        List<ModelProjection<T>> projections = ImmutableList.of(identityProjection);
        return new DefaultModelCreator<T>(reference.getPath(), sourceDescriptor, inputs, projections, transformer);
    }

    public ModelPath getPath() {
        return path;
    }

    public ModelPromise getPromise() {
        return new DefaultModelPromise<T>(projections);
    }

    public ModelAdapter create(Inputs inputs) {
        return new DefaultModelAdapter<T>(projections, transformer.transform(inputs));
    }

    public List<ModelReference<?>> getInputs() {
        return inputs;
    }

    public ModelRuleDescriptor getDescriptor() {
        return descriptor;
    }

    private static class DefaultModelPromise<T> implements ModelPromise {

        private final List<? extends ModelProjection<? super T>> projections;

        DefaultModelPromise(List<? extends ModelProjection<? super T>> projections) {
            this.projections = projections;
        }

        public <B> boolean asWritable(final ModelType<B> type) {
            return CollectionUtils.any(projections, new Spec<ModelProjection<? super T>>() {
                public boolean isSatisfiedBy(ModelProjection<? super T> projection) {
                    return projection.canBeViewedAsWritable(type);
                }
            });
        }

        public <B> boolean asReadOnly(final ModelType<B> type) {
            return CollectionUtils.any(projections, new Spec<ModelProjection<? super T>>() {
                public boolean isSatisfiedBy(ModelProjection<? super T> projection) {
                    return projection.canBeViewedAsReadOnly(type);
                }
            });
        }

        private Iterable<String> collectDescriptions(Transformer<Iterable<String>, ModelProjection<? super T>> transformer) {
            return CollectionUtils.flattenCollections(String.class, CollectionUtils.collect(projections, transformer));
        }
        public Iterable<String> getWritableTypeDescriptions() {
            return collectDescriptions(new Transformer<Iterable<String>, ModelProjection<? super T>>() {
                public Iterable<String> transform(ModelProjection<? super T> projection) {
                    return projection.getWritableTypeDescriptions();
                }
            });
        }

        public Iterable<String> getReadableTypeDescriptions() {
            return collectDescriptions(new Transformer<Iterable<String>, ModelProjection<? super T>>() {
                public Iterable<String> transform(ModelProjection<? super T> projection) {
                    return projection.getReadableTypeDescriptions();
                }
            });
        }

    }

    private static class DefaultModelAdapter<T> implements ModelAdapter {

        private final List<? extends ModelProjection<? super T>> projections;
        private final T instance;

        DefaultModelAdapter(List<? extends ModelProjection<? super T>> projections, T instance) {
            this.instance = instance;
            this.projections = projections;
        }
        public <B> ModelView<? extends B> asWritable(ModelBinding<B> binding, ModelRuleDescriptor sourceDescriptor, Inputs inputs, ModelRuleRegistrar modelRegistry) {
            for (ModelProjection<? super T> projection : projections) {
                ModelView<? extends B> view = projection.asWritable(binding, sourceDescriptor, inputs, modelRegistry, instance);
                if (view != null) {
                    return view;
                }
            }
            return null;
        }

        public <B> ModelView<? extends B> asReadOnly(ModelType<B> targetType) {
            for (ModelProjection<? super T> projection : projections) {
                ModelView<? extends B> view = projection.asReadOnly(targetType, instance);
                if (view != null) {
                    return view;
                }
            }
            return null;
        }

    }
}
