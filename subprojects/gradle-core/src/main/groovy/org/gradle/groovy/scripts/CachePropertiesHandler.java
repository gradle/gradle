/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.groovy.scripts;

import java.io.File;
import java.util.Map;

/**
 * Maintains the cache metadata.  This is used to determine if the cache is invalid for any reason (e.g. out of date, or
 * from an old version) and needs to be rebuilt.
 *
 * @author Hans Dockter
 */
public interface CachePropertiesHandler {
    /**
     * name of the file in the cache that contains this metadata
     */
    String PROPERTY_FILE_NAME = "cache.properties";

    /**
     * key used to store the hash of the contents of the script in the cache
     */
    String HASH_KEY = "hash";

    /**
     * key used to store the version of Gradle that wrote this cache
     */
    String VERSION_KEY = "version";

    /**
     * Enumeration of possible states for the cache.  If INVALID, the cache should be regenerated.
     */
    enum CacheState {
        VALID, INVALID
    }

    /**
     * Writes the cache information for the given script to the properties file in the given cache directory.
     *
     * @param script The script for which to write the cache information.
     * @param scriptCacheDir The cache directory in which to write the properties file.
     * @param additionalProperties Additional properties to include
     */
    void writeProperties(ScriptSource script, File scriptCacheDir, Map<String, ?> additionalProperties);

    /**
     * Reads the cached data from the properties file in the given cache directory and determines it's state with
     * respect to the given script.
     *
     * @param script The script with which the cached data should be compared.
     * @param scriptCacheDir The cache directory from which to read the properties file.
     * @param additionalProperties The additional properties. The cache state is only valid if each property is present
     * and equal to the given value.
     * @return The cache's current state.
     */
    CacheState getCacheState(ScriptSource script, File scriptCacheDir, Map<String, ?> additionalProperties);
}
