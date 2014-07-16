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

package org.gradle.model.internal.core.rule;

import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleSourceDescriptor;

import java.util.Collections;
import java.util.List;

public class InstanceBackedModelCreator implements ModelCreator {

    private final ModelPath path;
    private final ModelPromise promise;
    private final ModelRuleSourceDescriptor descriptor;
    private final ModelAdapter adapter;

    public static <T> ModelCreator of(ModelPath path, ModelType<T> type, ModelRuleSourceDescriptor sourceDescriptor, T instance) {
        return of(path, type, sourceDescriptor, Factories.constant(instance));
    }

    public static <T> ModelCreator of(ModelPath path, ModelType<T> type, ModelRuleSourceDescriptor sourceDescriptor, Factory<? extends T> factory) {
        return new InstanceBackedModelCreator(path, new SingleTypeModelPromise(type), sourceDescriptor, InstanceModelAdapter.of(type, factory));
    }

    private InstanceBackedModelCreator(ModelPath path, ModelPromise promise, ModelRuleSourceDescriptor descriptor, ModelAdapter adapter) {
        this.path = path;
        this.promise = promise;
        this.descriptor = descriptor;
        this.adapter = adapter;
    }

    public ModelPath getPath() {
        return path;
    }

    public ModelPromise getPromise() {
        return promise;
    }

    public ModelAdapter create(Inputs inputs) {
        return adapter;
    }

    public List<? extends ModelReference<?>> getInputBindings() {
        return Collections.emptyList();
    }

    public ModelRuleSourceDescriptor getSourceDescriptor() {
        return descriptor;
    }
}
