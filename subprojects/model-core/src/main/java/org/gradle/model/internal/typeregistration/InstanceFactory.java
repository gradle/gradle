/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.model.internal.typeregistration;

import org.gradle.api.Nullable;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.type.ModelType;

import java.util.Set;

public interface InstanceFactory<T> {
    ModelType<T> getBaseInterface();

    Set<ModelType<? extends T>> getSupportedTypes();

    /**
     * Return information about the implementation of an unmanaged type.
     */
    @Nullable
    <S extends T> ImplementationInfo getImplementationInfo(ModelType<S> publicType);

    /**
     * Return information about the implementation of a managed type with an unmanaged super-type.
     */
    <S extends T> ImplementationInfo getManagedSubtypeImplementationInfo(ModelType<S> publicType);

    interface ImplementationInfo {
        /**
         * Creates an instance of the delegate for the given node.
         */
        Object create(MutableModelNode modelNode);

        /**
         * The default implementation type that can be used as a delegate for any managed subtypes of the public type.
         */
        ModelType<?> getDelegateType();

        /**
         * The internal views for the public type.
         */
        Set<ModelType<?>> getInternalViews();
    }
}
