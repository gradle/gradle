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

package org.gradle.internal.deprecation;

import org.gradle.api.artifacts.Configuration;

import javax.annotation.Nullable;
import java.util.List;

public interface DeprecatableConfiguration extends Configuration {

    /**
     * @return configurations that should be used to declare dependencies instead of this configuration.
     *         Returns 'null' if this configuration is not deprecated for declaration.
     */
    @Nullable
    List<String> getDeclarationAlternatives();

    /**
     * @return configurations that should be used to consume a component instead of consuming this configuration.
     *         Returns 'null' if this configuration is not deprecated for consumption.
     */
    @Nullable
    List<String> getConsumptionAlternatives();

    /**
     * @return configurations that should be used to consume a component instead of consuming this configuration.
     *         Returns 'null' if this configuration is not deprecated for resolution.
     */
    @Nullable
    List<String> getResolutionAlternatives();

    /**
     * @return true, if all functionality of the configuration is either disabled or deprecated
     */
    boolean isFullyDeprecated();

    /**
     * Allows plugins to deprecate a configuration that will be removed in the next major Gradle version.
     *
     * @param alternativesForDeclaring alternative configurations that can be used to declare dependencies
     * @return this configuration
     */
    DeprecatableConfiguration deprecateForDeclaration(String... alternativesForDeclaring);

    /**
     * Allows plugins to deprecate the consumability property (canBeConsumed() == true) of a configuration that will be changed in the next major Gradle version.
     *
     * @param alternativesForConsumption alternative configurations that can be used for consumption
     * @return this configuration
     */
    DeprecatableConfiguration deprecateForConsumption(String... alternativesForConsumption);

    /**
     * Allows plugins to deprecate the resolvability property (canBeResolved() == true) of a configuration that will be changed in the next major Gradle version.
     *
     * @param alternativesForResolving alternative configurations that can be used for dependency resolution
     * @return this configuration
     */
    DeprecatableConfiguration deprecateForResolution(String... alternativesForResolving);

    default boolean canSafelyBeResolved() {
        if (!isCanBeResolved()) {
            return false;
        }
        List<String> resolutionAlternatives = getResolutionAlternatives();
        return resolutionAlternatives == null;
    }
}
