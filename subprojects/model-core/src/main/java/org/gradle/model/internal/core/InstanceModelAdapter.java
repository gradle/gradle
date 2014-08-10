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

public class InstanceModelAdapter<I> implements ModelAdapter {

    private final ModelType<I> type;
    private final Factory<? extends I> factory;
    private I instance;

    public InstanceModelAdapter(ModelType<I> type, Factory<? extends I> factory) {
        this.type = type;
        this.factory = factory;
    }

    public static <T> ModelAdapter of(ModelType<T> type, T instance) {
        return of(type, Factories.constant(instance));
    }

    public static <T> ModelAdapter of(ModelType<T> type, Factory<? extends T> factory) {
        return new InstanceModelAdapter<T>(type, factory);
    }

    public <T> ModelView<? extends T> asWritable(ModelBinding<T> binding, ModelRuleDescriptor sourceDescriptor, Inputs inputs, ModelRuleRegistrar modelRuleRegistrar) {
        return type(binding.getReference().getType());
    }

    public <T> ModelView<? extends T> asReadOnly(ModelType<T> type) {
        return type(type);
    }

    private I getInstance() {
        if (instance == null) {
            instance = factory.create();
        }
        return instance;
    }

    private <T> ModelView<? extends T> type(ModelType<T> targetType) {
        if (targetType.isAssignableFrom(type)) {
            @SuppressWarnings("unchecked") ModelView<? extends T> cast = (ModelView<? extends T>) InstanceModelView.of(type, getInstance());
            return cast;
        } else {
            return null;
        }
    }

}
