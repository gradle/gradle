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
package org.gradle.api.publish.internal.versionmapping;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.publish.VariantVersionMappingStrategy;

import javax.annotation.Nullable;

public interface VariantVersionMappingStrategyInternal extends VariantVersionMappingStrategy {
    /**
     * Return true if the user has explicitly enabled version mapping. False otherwise.
     */
    boolean isEnabled();

    /**
     * Get the resolution configuration, as specified by the user.
     */
    @Nullable
    Configuration getUserResolutionConfiguration();

    /**
     * Get the resolution configuration, as specified by {@link VersionMappingStrategyInternal#defaultResolutionConfiguration(String, String)}.
     */
    @Nullable
    Configuration getDefaultResolutionConfiguration();
}
