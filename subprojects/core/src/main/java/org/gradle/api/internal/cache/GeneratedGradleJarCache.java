/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.cache;

import org.gradle.api.Action;

import java.io.File;

/**
 * Cache for generated jars such as {@code gradle-api-${version}.jar} and {@code gradle-test-kit-${version}.jar}.
 */
public interface GeneratedGradleJarCache {

    String CACHE_KEY = "generated-gradle-jars";

    String CACHE_DISPLAY_NAME = "Generated Gradle JARs cache";

    /**
     * Returns the generated jar uniquely identified by {@code identifier}.
     *
     * If the jar is not found in the cache the given {@code creator} action will be invoked to create it.
     *
     * @param identifier the jar identifier (for example, {@code "api"}).
     * @param creator the action that will create the file should it not be found in the cache.
     * @return the generated file.
     */
    File get(String identifier, Action<File> creator);
}
