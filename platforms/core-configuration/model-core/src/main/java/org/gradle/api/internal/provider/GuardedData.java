/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.provider;

import org.gradle.internal.Cast;

/**
 * Base interface for the nested data which may cause circular evaluation errors upon accessing.
 * For example, if a provider depends on some other provider to compute its value, by applying a transformation to it, then
 * the "source" provider should be represented as a guarded data - because obtaining its value may cause the first provider
 * to be resolved in case of a circular evaluation.
 * <p>
 * Typical implementation provides additional convenience methods that add the owner to the evaluation context and call
 * some methods of the data.
 * <p>
 * Implementors <b>must</b> update {@link #of(ProviderInternal, Object)} method to recognize the new implementation.
 * This interface would be a sealed interface if the feature was available in the current language level.
 * <p>
 * Configuration cache serialization provides custom protocol for this interface.
 *
 * @param <T> the underlying type of the data
 */
public interface GuardedData<T> {
    /**
     * Returns the owner of the data: the provider which is marked as evaluating while the data is accessed.
     *
     * @return the owner of the data
     */
    ProviderInternal<?> getOwner();

    /**
     * Returns the data without marking the owner as evaluating. Can be sparingly used to forward the data outside the owner's evaluation.
     *
     * @return the data
     */
    T unsafeGet();

    /**
     * Restores GuardedData from parts. All interface implementation must be supported by this method.
     *
     * @param owner the owner
     * @param data the data
     * @param <T> the type of the data
     * @return GuardedData of the appropriate type to represent the data
     */
    static <T> GuardedData<T> of(ProviderInternal<?> owner, T data) {
        if (data instanceof ProviderInternal<?>) {
            return Cast.uncheckedCast(new AbstractMinimalProvider.ProviderGuard<>(owner, Cast.uncheckedCast(data)));
        } else if (data instanceof CollectionSupplier<?, ?>) {
            return Cast.uncheckedCast(new AbstractCollectionProperty.CollectionSupplierGuard<>(owner, Cast.uncheckedCast(data)));
        } else if (data instanceof MapSupplier<?, ?>) {
            return Cast.uncheckedCast(new DefaultMapProperty.MapSupplierGuard<>(owner, Cast.uncheckedCast(data)));
        } else {
            throw new IllegalArgumentException("Unknown data subtype " + data.getClass() + " for GuardedData");
        }
    }
}
