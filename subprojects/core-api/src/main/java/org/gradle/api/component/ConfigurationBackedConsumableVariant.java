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

package org.gradle.api.component;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.artifacts.Configuration;

/**
 * A {@link ConsumableVariant} implementation which sources its data from a backing {@link Configuration}.
 *
 * <p>Mutable access to the underling {@code Configuration} is not permitted via the {@code ConsumableVariant}
 * methods. Any modification to this variant should be performed on the underlying configuration itself via
 * the {@link #getConfiguration()} and {@link #configuration(Action)} methods.</p>
 *
 * @since 8.2
 */
@Incubating
public interface ConfigurationBackedConsumableVariant extends ConsumableVariant {

    /**
     * Get the backing {@link Configuration}.
     *
     * @return The configuration which is used as a data source for this variant.
     */
    Configuration getConfiguration();

    /**
     * Configure the backing {@link Configuration}.
     *
     * @param action The action to use to configure the backing configuration.
     */
    void configuration(Action<? super Configuration> action);
}
