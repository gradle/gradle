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

package org.gradle.caching.internal.controller;

import org.gradle.caching.internal.NextGenBuildCacheService;

/**
 * Handler for a remote build cache service.
 *
 * A remote build cache service can be disabled either by its configuration, or after an error has occurred.
 * Storing can be disabled via configuration even if the service is enabled (and thus can load).
 */
public interface RemoteNextGenBuildCacheServiceHandler extends NextGenBuildCacheService {
    /**
     * Returns if the service can fulfill load requests.
     * @return {@literal true} if the service is not disabled.
     */
    boolean canLoad();

    /**
     * Returns if the service can fulfill store requests.
     * @return {@literal true} if the service is not disabled and storing is enabled for the service.
     */
    boolean canStore();

    /**
     * Disables the service after an error has been encountered.
     */
    void disableOnError();
}
