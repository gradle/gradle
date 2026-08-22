/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.provider;

import org.gradle.api.Incubating;
import org.gradle.api.NonExtensible;
import org.gradle.internal.HasInternalProtocol;

/**
 * A {@link Provider} that always has a value present.
 *
 * <p>Querying the value of this provider never fails: {@link #get()} always returns the value,
 * {@link #getOrNull()} never returns {@code null} and {@link #isPresent()} always returns {@code true}.
 * Only the presence of the value is guaranteed; the value itself is not necessarily constant.
 *
 * <p><b>Note:</b> This interface is not intended for implementation by build script or plugin authors.
 * A provider that always has the given value can be created via {@link ProviderFactory#some(Object)}.
 *
 * @param <T> Type of value represented by the provider
 * @since 9.8.0
 */
@HasInternalProtocol
@NonExtensible
@Incubating
public interface PresentProvider<T> extends Provider<T> {

    /**
     * Returns the value of this provider. Unlike {@link Provider#get()}, never fails, as the value is always present.
     *
     * @return the current value of this provider.
     * @since 9.8.0
     */
    @Override
    T get();

    /**
     * Returns the value of this provider. Unlike {@link Provider#getOrNull()}, never returns {@code null}, as the value is always present.
     *
     * @return the current value of this provider.
     * @since 9.8.0
     */
    @Override
    T getOrNull();

    /**
     * Returns the value of this provider. Unlike {@link Provider#getOrElse(Object)}, the given default value is never used, as the value is always present.
     *
     * @param defaultValue The default value to use when this provider has no value. Never used.
     * @return the current value of this provider.
     * @since 9.8.0
     */
    @Override
    T getOrElse(T defaultValue);

    /**
     * Returns whether the provider has a value present. Always {@code true}.
     *
     * @return {@code true}
     * @since 9.8.0
     */
    @Override
    boolean isPresent();

    /**
     * Returns this provider, as the value is always present and the given default value can never be used.
     *
     * @param value The default value to use when this provider has no value. Never used.
     * @return this provider.
     * @since 9.8.0
     */
    @Override
    PresentProvider<T> orElse(T value);

    /**
     * Returns this provider, as the value is always present and the given default provider can never be used.
     *
     * @param provider The default provider to use when this provider has no value. Never used.
     * @return this provider.
     * @since 9.8.0
     */
    @Override
    PresentProvider<T> orElse(Provider<? extends T> provider);
}
