/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.Collections;
import java.util.List;

public class InstanceBackedModelCreator<T> implements ModelCreator {

    private final ModelPromise promise;
    private final ModelRuleDescriptor descriptor;
    private final List<ModelReference<?>> inputs;
    private final ModelReference<T> reference;
    private final Factory<? extends T> factory;

    public static <T> ModelCreator of(ModelReference<T> reference, ModelRuleDescriptor sourceDescriptor, T instance) {
        return of(reference, sourceDescriptor, Factories.constant(instance));
    }

    public static <T> ModelCreator of(ModelReference<T> reference, ModelRuleDescriptor sourceDescriptor, Factory<? extends T> factory) {
        return of(reference, sourceDescriptor, Collections.<ModelReference<?>>emptyList(), factory);
    }

    public static <T> ModelCreator of(ModelReference<T> reference, ModelRuleDescriptor sourceDescriptor, List<ModelReference<?>> inputBindings, Factory<? extends T> factory) {
        return new InstanceBackedModelCreator<T>(reference, sourceDescriptor, inputBindings, factory);
    }

    private InstanceBackedModelCreator(ModelReference<T> reference, ModelRuleDescriptor descriptor, List<ModelReference<?>> inputs, Factory<? extends T> factory) {
        this.reference = reference;
        this.factory = factory;
        this.promise = new SingleTypeModelPromise(reference.getType());
        this.descriptor = descriptor;
        this.inputs = inputs;
    }

    public ModelPath getPath() {
        return reference.getPath();
    }

    public ModelPromise getPromise() {
        return promise;
    }

    public ModelAdapter create(Inputs inputs) {
        return InstanceModelAdapter.of(reference.getType(), factory.create());
    }

    public List<ModelReference<?>> getInputs() {
        return inputs;
    }

    public ModelRuleDescriptor getDescriptor() {
        return descriptor;
    }

}
