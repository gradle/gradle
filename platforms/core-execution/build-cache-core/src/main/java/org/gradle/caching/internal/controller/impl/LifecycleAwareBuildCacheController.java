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

package org.gradle.caching.internal.controller.impl;

import org.gradle.caching.configuration.internal.BuildCacheConfigurationInternal;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * This class is in this package, alongside {@link LifecycleAwareBuildCacheControllerFactory}, to keep the complexity of the Gradle-specific {@link BuildCacheController} lifecycle in one place.
 *
 * At some point we aim to better model services who need data from the Gradle model, and this complexity can go away.
 */
@ServiceScope(Scope.Build.class)
public interface LifecycleAwareBuildCacheController extends BuildCacheController {
    /**
     * Called when the configuration for this controller is available.
     * The implementation may or may not use the given configuration. For example, the controller for an included build might use the configuration from the root build instead.
     */
    void configurationAvailable(BuildCacheConfigurationInternal configuration);

    /**
     * Resets the state of this controller in preparation for loading configuration from the cache.
     */
    void resetState();
}
