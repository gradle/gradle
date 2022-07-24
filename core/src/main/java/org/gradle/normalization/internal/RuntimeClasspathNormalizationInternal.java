/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.normalization.internal;

import org.gradle.api.internal.changedetection.state.ResourceEntryFilter;
import org.gradle.api.internal.changedetection.state.ResourceFilter;
import org.gradle.normalization.RuntimeClasspathNormalization;

import javax.annotation.Nullable;
import java.util.Map;

public interface RuntimeClasspathNormalizationInternal extends RuntimeClasspathNormalization {
    ResourceFilter getClasspathResourceFilter();

    ResourceEntryFilter getManifestAttributeResourceEntryFilter();

    Map<String, ResourceEntryFilter> getPropertiesFileFilters();

    /**
     * Returns the configuration of runtime classpath normalization in a configuration-cache friendly form.
     * The normalization cannot be further configured after this call.
     *
     * @return the configuration of input normalization or {@code null} if there is no user-defined state.
     */
    @Nullable
    CachedState computeCachedState();

    /**
     * Configures input normalization from cached state data.
     */
    void configureFromCachedState(CachedState state);

    /**
     * The opaque representation of the runtime classpath normalization state, intended to be serialized in the configuration cache.
     */
    interface CachedState {
    }
}
