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

package org.gradle.api.internal.artifacts.configurations;

/**
 * Represents a request to possibly create a configuration.
 *
 * It can be used to create error messages that are specific to the context of the request and contain
 * actionable advice to avoid the problem.
 */
public interface RoleBasedConfigurationCreationRequest {

    /**
     * Get a resolution string to add to the exception thrown when a configuration with the same name already exists.
     */
    String getExistingConfigurationResolution(String configurationName);

}
