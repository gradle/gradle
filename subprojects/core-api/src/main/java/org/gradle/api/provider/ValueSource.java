/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.Incubating;

import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Base configuration for value source definitions.
 *
 * @see ProviderFactory#of(Class, Action)
 *
 * @param <T> The type of value obtained from this source.
 * @param <P> The source specific parameter type.
 * @since 6.1
 */
@Incubating
public interface ValueSource<T, P extends ValueSourceParameters> {

    /**
     * The object provided by {@link ValueSourceSpec#getParameters()} when creating a provider from the value source.
     *
     * <p>
     * Do not implement this method in your subclass.
     * Gradle provides the implementation when creating a provider from the value source via {@link ProviderFactory#of(Class, Action)}.
     * </p>
     */
    @Inject
    P getParameters();

    /**
     * Obtains the value from the source. The returned value must be effectively immutable.
     *
     * <p>This method must be implemented in the subclass.</p>
     * <p>This method is only called if the provider value is requested and only once in that case.</p>
     *
     * @return the value obtained or {@code null} if the value is not present.
     */
    @Nullable
    T obtain();
}
