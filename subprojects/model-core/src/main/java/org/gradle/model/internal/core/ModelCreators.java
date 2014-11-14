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
import net.jcip.annotations.NotThreadSafe;
import net.jcip.annotations.ThreadSafe;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.Transformers;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;

import java.util.Collections;
import java.util.List;

@ThreadSafe
abstract public class ModelCreators {

    public static <T> Builder bridgedInstance(ModelReference<T> modelReference, T instance) {
        return unmanagedInstance(modelReference, Factories.constant(instance));
    }

    public static <T> Builder unmanagedInstance(final ModelReference<T> modelReference, final Factory<? extends T> factory) {
        Transformer<? extends Action<ModelNode>, Object> initializer = Transformers.toTransformer(
                Factories.constant(
                        new Action<ModelNode>() {
                            public void execute(ModelNode modelNode) {
                                modelNode.setPrivateData(modelReference.getType(), factory.create());
                            }
                        }
                )
        );

        return of(modelReference, initializer)
                .withProjection(new UnmanagedModelProjection<T>(modelReference.getType(), true, true));
    }

    public static Builder of(ModelReference<?> modelReference, Transformer<? extends Action<? super ModelNode>, ? super Inputs> initializer) {
        return new Builder(modelReference, initializer);
    }

    @NotThreadSafe
    public static class Builder {

        private final Transformer<? extends Action<? super ModelNode>, ? super Inputs> initializer;
        private final ModelReference<?> modelReference;
        private final ImmutableList.Builder<ModelProjection> projections = ImmutableList.builder();

        private ModelRuleDescriptor modelRuleDescriptor;
        private List<? extends ModelReference<?>> inputs = Collections.emptyList();

        private Builder(ModelReference<?> modelReference, Transformer<? extends Action<? super ModelNode>, ? super Inputs> initializer) {
            this.modelReference = modelReference;
            this.initializer = initializer;
        }

        public Builder simpleDescriptor(String descriptor) {
            this.modelRuleDescriptor = new SimpleModelRuleDescriptor(descriptor);
            return this;
        }

        public Builder descriptor(ModelRuleDescriptor descriptor) {
            this.modelRuleDescriptor = descriptor;
            return this;
        }

        public Builder inputs(List<? extends ModelReference<?>> inputs) {
            this.inputs = inputs;
            return this;
        }

        // Callers must take care
        public Builder withProjection(ModelProjection projection) {
            projections.add(projection);
            return this;
        }

        public ModelCreator build() {
            return new ProjectionBackedModelCreator(modelReference.getPath(), modelRuleDescriptor, inputs, projections.build(), initializer);
        }
    }

}
