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

import org.gradle.internal.util.BiFunction;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

import java.util.Set;

public interface InstanceFactory<T> {
    ModelType<T> getBaseInterface();

    <S extends T> S create(ModelType<S> type, MutableModelNode modelNode, String name);

    Set<ModelType<? extends T>> getSupportedTypes();

    <S extends T> TypeRegistrationBuilder<S> register(ModelType<S> publicType, ModelRuleDescriptor sourceRule);

    <S extends T> Set<ModelType<?>> getInternalViews(ModelType<S> type);

    <S extends T> ImplementationInfo<? extends T> getImplementationInfo(ModelType<S> type);

    void validateRegistrations();

    interface TypeRegistrationBuilder<T> {
        TypeRegistrationBuilder<T> withImplementation(ModelType<? extends T> implementationType, BiFunction<? extends T, String, ? super MutableModelNode> factory);

        TypeRegistrationBuilder<T> withInternalView(ModelType<?> internalView);
    }

    interface ImplementationInfo<T> {
        ModelType<? extends T> getPublicType();
        ModelType<? extends T> getDelegateType();
    }
}
