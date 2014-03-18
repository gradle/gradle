/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice;

public class ConfigurationResolveContext implements ArtifactResolveContext {
    private final String configurationName;

    public ConfigurationResolveContext(String configurationName) {
        this.configurationName = configurationName;
    }

    public String getConfigurationName() {
        return configurationName;
    }

    @Override
    public String toString() {
        return String.format("configuration '%s'", configurationName);
    }

    public String getId() {
        return "configuration:" + configurationName;
    }

    public String getDescription() {
        return String.format("artifacts for configuration '%s'", configurationName);
    }
}
