/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.publish;

import org.gradle.api.artifacts.Configuration;
import org.gradle.internal.HasInternalProtocol;

/**
 * Defines the version mapping strategy when publishing, for a specific variant.
 *
 * @since 5.2
 */
@HasInternalProtocol
public interface VariantVersionMappingStrategy {
    /**
     * Declares that this variant should use versions from the resolution
     * of a default configuration chosen by Gradle.
     */
    void fromResolutionResult();

    /**
     * Declares that this variant should use versions from the resolution
     * of the configuration provided as an argument.
     *
     * @param configuration a resolvable configuration where to pick resolved version numbers
     */
    void fromResolutionOf(Configuration configuration);


    /**
     * Declares that this variant should use versions from the resolution
     * of the configuration provided as an argument.
     *
     * @param configurationName a resolvable configuration name where to pick resolved version numbers
     */
    void fromResolutionOf(String configurationName);
}
