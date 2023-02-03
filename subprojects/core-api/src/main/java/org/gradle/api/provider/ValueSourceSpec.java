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


/**
 * Base configuration for value source definitions.
 *
 * @see org.gradle.api.provider.ProviderFactory#of(Class, Action)
 * @param <P> The value source specific parameter type.
 * @since 6.1
 */
@Incubating
public interface ValueSourceSpec<P extends ValueSourceParameters> {

    /**
     * The parameters for the value source.
     *
     * @see org.gradle.api.provider.ProviderFactory#of(Class, Action)
     */
    P getParameters();

    /**
     * Configure the parameters for the value source.
     *
     * @see org.gradle.api.provider.ProviderFactory#of(Class, Action)
     */
    void parameters(Action<? super P> action);
}
