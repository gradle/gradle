/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.initialization;

import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Map;

/**
 * Provides access to the environment in a configuration cache friendly way.
 *
 * Access to resources will be tracked when storing to the configuration cache.
 */
@ServiceScope(Scopes.Build.class)
public interface Environment {

    /**
     * Reads the given Java properties file if it exists.
     *
     * @return the properties loaded from the given file or {@code null} if the file does not exist.
     */
    @Nullable
    Map<String, String> propertiesFile(File propertiesFile);

    /**
     * Provides access to system properties.
     */
    Properties getSystemProperties();

    /**
     * Provides access to environment variables.
     */
    Properties getVariables();

    /**
     * Common interface for tracked access to system properties and environment variables.
     */
    interface Properties {
        /**
         * Selects the properties that have a name longer than and starting with the given prefix.
         *
         * The returned map keys still contain the prefix.
         *
         * @return a map containing only the properties whose name start with the given prefix.
         */
        Map<String, String> byNamePrefix(String prefix);
    }
}
