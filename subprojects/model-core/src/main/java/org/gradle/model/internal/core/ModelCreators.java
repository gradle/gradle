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

import net.jcip.annotations.NotThreadSafe;
import net.jcip.annotations.ThreadSafe;
import org.gradle.internal.BiAction;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@ThreadSafe
abstract public class ModelCreators {

    public static <T> Builder bridgedInstance(ModelReference<T> modelReference, T instance) {
        return unmanagedInstance(modelReference, Factories.constant(instance));
    }

    public static <T> Builder unmanagedInstance(final ModelReference<T> modelReference, final Factory<? extends T> factory) {
        BiAction<? super MutableModelNode, ? super List<ModelView<?>>> initializer = new BiAction<MutableModelNode, List<ModelView<?>>>() {
            public void execute(MutableModelNode modelNode, List<ModelView<?>> inputs) {
                modelNode.setPrivateData(modelReference.getType(), factory.create());
            }
        };

        return of(modelReference, initializer)
                .withProjection(new UnmanagedModelProjection<T>(modelReference.getType(), true, true));
    }

    public static Builder of(ModelReference<?> modelReference, BiAction<? super MutableModelNode, ? super List<ModelView<?>>> initializer) {
        return new Builder(modelReference, initializer);
    }

    @NotThreadSafe
    public static class Builder {
        private final BiAction<? super MutableModelNode, ? super List<ModelView<?>>> initializer;
        private final ModelReference<?> modelReference;
        private final List<ModelProjection> projections = new ArrayList<ModelProjection>();
        private boolean ephemeral;
        private boolean hidden;

        private ModelRuleDescriptor modelRuleDescriptor;
        private List<? extends ModelReference<?>> inputs = Collections.emptyList();

        private Builder(ModelReference<?> modelReference, BiAction<? super MutableModelNode, ? super List<ModelView<?>>> initializer) {
            this.modelReference = modelReference;
            this.initializer = initializer;
        }

        public Builder descriptor(String descriptor) {
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

        public Builder inputs(ModelReference<?>... inputs) {
            this.inputs = Arrays.asList(inputs);
            return this;
        }

        // Callers must take care
        public Builder withProjection(ModelProjection projection) {
            projections.add(projection);
            return this;
        }

        public Builder hidden(boolean flag) {
            this.hidden = flag;
            return this;
        }

        public Builder ephemeral(boolean flag) {
            this.ephemeral = flag;
            return this;
        }

        public ModelCreator build() {
            ModelProjection projection = projections.size() == 1 ? projections.get(0) : new ChainingModelProjection(projections);
            return new ProjectionBackedModelCreator(modelReference.getPath(), modelRuleDescriptor, ephemeral, hidden, inputs, projection, initializer);
        }
    }

}
