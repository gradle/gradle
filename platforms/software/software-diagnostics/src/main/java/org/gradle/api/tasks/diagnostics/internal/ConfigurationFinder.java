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

package org.gradle.api.tasks.diagnostics.internal;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.util.internal.NameMatcher;

public class ConfigurationFinder {
    private ConfigurationFinder() {
    }

    public static Configuration find(ConfigurationContainer configurations, String configurationName) {
        NameMatcher matcher = new NameMatcher();
        Configuration configuration = matcher.find(configurationName, configurations.getAsMap());
        if (configuration != null) {
            return configuration;
        }

        // if configuration with exact name is present return it
        configuration = configurations.findByName(configurationName);
        if (configuration != null) {
            return configuration;
        }

        throw new InvalidUserDataException(matcher.formatErrorMessage("configuration", configurations));
    }
}
