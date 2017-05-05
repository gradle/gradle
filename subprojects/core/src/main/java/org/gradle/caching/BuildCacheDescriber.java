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

package org.gradle.caching;

import org.gradle.api.Incubating;

import java.util.Map;

/**
 * Describer object to capture a build cache configuration.
 *
 * @since 4.0
 */
@Incubating
public interface BuildCacheDescriber {

    /**
     * Sets a human friendly description of the type of this cache.
     */
    BuildCacheDescriber type(String type);

    /**
     * Sets a configuration param such as the remote url or the local directory location.
     */
    BuildCacheDescriber configParam(String name, String value);

    /**
     * Provides a human friendly description of the type of this cache.
     */
    String getType();

    /**
     * Returns a map of configuration params such as the remote url or the local directory location.
     * <p>
     * Implementations should return an empty map if they have no config to describe.
     */
    Map<String, String> getConfigParams();
}
