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

package org.gradle.model.internal.core;

import org.gradle.api.Nullable;
import org.gradle.internal.util.BiFunction;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

import java.util.Set;

public interface InstanceFactory<T, P> {
    <S extends T> S create(ModelType<S> type, MutableModelNode modelNode, P payload);

    Set<ModelType<? extends T>> getSupportedTypes();

    <S extends T> void registerFactory(ModelType<S> type, @Nullable ModelRuleDescriptor sourceRule, BiFunction<? extends S, ? super P, ? super MutableModelNode> factory);

    <S extends T> Set<ModelType<?>> getInternalViews(ModelType<S> type);

    <S extends T, I extends S> void registerImplementation(ModelType<S> type, @Nullable ModelRuleDescriptor sourceRule, ModelType<I> implementationViewType);

    <S extends T> void registerInternalView(ModelType<S> type, @Nullable ModelRuleDescriptor sourceRule, ModelType<?> internalViewType);

    void validateRegistrations();
}
