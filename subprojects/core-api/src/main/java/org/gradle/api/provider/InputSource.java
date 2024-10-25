/*
 * Copyright 2024 the original author or authors.
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

import javax.annotation.Nullable;
import javax.inject.Inject;

public interface InputSource<T, P extends InputSourceParameters> {
    /**
     * The object provided by {@link InputSourceSpec#getParameters()} when creating a provider from the input source.
     *
     * <p>
     * Do not implement this method in your subclass.
     * Gradle provides the implementation when creating a provider from the input source via {@link ProviderFactory#inputOf(Class, Action)}.
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
