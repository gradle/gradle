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
 * Status of a feature in a build that affects Gradle behavior,
 * and may impose additional requirements on plugins or build scripts.
 * <p>
 * It is possible to check if the feature is {@link #getActive() active} in the current build.
 * The {@link #getRequested() requested} property shows whether the user opted in or opted out from the feature.
 *
 * @see BuildFeatures
 * @since 8.5
 */
public interface BuildFeature {

    /**
     * Status of the feature flag with three possibilities:
     * <ul>
     *     <li>true - requested to opt in
     *     <li>false - requested to opt out
     *     <li>null - no request (default behavior)
     * </ul>
     *
     * Use {@link Provider#getOrNull()} to safely retrieve a nullable value or check {@link Provider#isPresent()}
     * as the provider <b>can be undefined</b> in the case of no request.
     * <p>
     * Using Configuration Cache as an example, it's possible to opt in via {@code --configuration-cache} flag,
     * and opt out via {@code --no-configuration-cache}.
     * <p>
     * Note that opt-ins can be dismissed when flags for other features take precedence.
     * For instance, running with {@code --export-keys} flag always disables Configuration Cache.
     * <p>
     * Use {@link #getActive()} to get the effective status of the feature.
     *
     * @since 8.5
     */
    Provider<Boolean> getRequested();

    /**
     * Effective status of the feature with two possibilities:
     * <ul>
     *     <li>true - the feature is taking effect, it is "on"
     *     <li>false - the feature is not taking any effect, it is "off"
     * </ul>
     *
     * Using Configuration Cache as an example, opting in via {@code --configuration-cache} turns it on.
     * However, when combined with {@code --export-keys} flag, the latter always take precedence
     * and turns Configuration Cache off.
     * Configuration Cache is also automatically turned on, when Isolated Projects are turned on.
     * <p>
     * Note, that this method provides generic information about the status of the feature.
     * It does not provide any information about the behavior that is specific for any given feature.
     * For instance, it <b>does not</b> say whether Configuration Cache got a hit or a miss for a given invocation.
     *
     * @since 8.5
     */
    Provider<Boolean> getActive();

}

