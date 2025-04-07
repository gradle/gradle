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

package org.gradle.api.configuration;

import org.gradle.api.provider.Provider;

/**
 * Status of a Gradle feature in the current invocation, such as Configuration Cache.
 * <p>
 * Gradle features can be enabled or disabled via <a href="https://docs.gradle.org/current/userguide/build_environment.html">build options</a>,
 * but in some cases other flags or features can affect that.
 * For this reason, the {@link #getRequested()} status represents the configuration of a feature in isolation,
 * and the {@link #getActive()} status represents the final result, after everything has been taken into account.
 * <p>
 * Some flags disable features.
 * For instance, {@code --export-keys} flag always disables {@link BuildFeatures#getConfigurationCache() Configuration Cache}.
 * If {@code --configuration-cache} is provided at the same time, then {@code configurationCache.requested.get() == true},
 * but {@code configurationCache.active.get() == false}.
 * <p>
 * Some features automatically enable other features.
 * For instance, enabling {@link BuildFeatures#getIsolatedProjects() Isolated Projects} enables Configuration Cache.
 * So that {@code configurationCache.requested} has no value, but {@code configurationCache.active.get() == true}.
 *
 * @see BuildFeatures
 * @since 8.5
 */
public interface BuildFeature {

    /**
     * How the feature was requested:
     * <ul>
     *     <li>true - explicitly enabled (e.g. {@code --configuration-cache})
     *     <li>false - explicitly disabled (e.g. {@code --no-configuration-cache})
     *     <li>provider has no value - no preference, default behavior
     * </ul>
     *
     * Use {@link Provider#getOrNull()} to safely retrieve a nullable value or check {@link Provider#isPresent()}
     * as the <b>provider can have no value</b> in the case there is no explicit request.
     * <p>
     * Note that enabling the feature doesn't necessary mean the feature will be activated.
     * See {@link BuildFeature} for more details.
     * <p>
     * Use {@link #getActive()} to get the effective status of the feature.
     *
     * @return The provider that <b>can have no value</b> and its value denotes the requested status of a feature
     * @since 8.5
     */
    Provider<Boolean> getRequested();

    /**
     * Effective status of the feature:
     * <ul>
     *     <li>true - the feature is active and taking effect
     *     <li>false - the feature is deactivated
     * </ul>
     *
     * This method provides only generic information about the status of the feature.
     * It does not provide information about a behavior that is specific for any given feature.
     * For instance, it <b>does not</b> say whether Configuration Cache got a hit or a miss for a given invocation.
     * <p>
     * Note that a feature can get activated even if not explicitly enabled.
     * See {@link BuildFeature} for more details.
     *
     * @return The provider that is always has a value and its value denotes the effective status of a feature
     * @since 8.5
     */
    Provider<Boolean> getActive();

}

