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

package org.gradle.internal.component;

/**
 * This exception is thrown during variant aware dependency resolution when a dependency
 * explicitly requests a specific configuration of a component by name
 * and it is found in the component but it is not consumable.
 */
public class ConfigurationNotConsumableException extends AbstractNamedConfigurationException {
    public ConfigurationNotConsumableException(String targetComponent, String configurationName) {
        super("Selected configuration '" + configurationName + "' on '" + targetComponent + "' but it can't be used as a project dependency because it isn't intended for consumption by other components.");
    }
}
