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

package org.gradle.caching.internal.services;

import org.gradle.caching.configuration.internal.BuildCacheConfigurationInternal;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.internal.instantiation.InstanceGenerator;
import org.gradle.util.Path;

public interface BuildCacheControllerFactory {
    String REMOTE_CONTINUE_ON_ERROR_PROPERTY = "org.gradle.unsafe.build-cache.remote-continue-on-error";

    BuildCacheController createController(Path buildIdentityPath, BuildCacheConfigurationInternal buildCacheConfiguration, InstanceGenerator instanceGenerator);
}
