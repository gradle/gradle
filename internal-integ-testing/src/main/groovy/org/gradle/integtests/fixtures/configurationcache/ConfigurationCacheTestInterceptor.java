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

package org.gradle.integtests.fixtures.configurationcache;

import com.google.common.collect.ImmutableMap;
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheOption;
import org.gradle.integtests.fixtures.extensions.BehindFlagFeatureInterceptor;

/**
 * Intended to be a temporary runner until there is full cross-cutting coverage for all int tests with configuration cache enabled.
 */
public class ConfigurationCacheTestInterceptor extends BehindFlagFeatureInterceptor {
    public ConfigurationCacheTestInterceptor(Class<?> target) {
        super(target, ImmutableMap.of(ConfigurationCacheOption.PROPERTY_NAME, booleanFeature("configuration cache")));
    }
}
