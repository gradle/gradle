/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.component.resolution.failure.describer;

import org.gradle.internal.component.ConfigurationNotFoundException;
import org.gradle.internal.component.resolution.failure.type.RequestedConfigurationNotFoundFailure;

/**
 * A {@link ResolutionFailureDescriber} that describes a {@link RequestedConfigurationNotFoundFailure}.
 */
public abstract class RequestedConfigurationNotFoundFailureDescriber extends AbstractResolutionFailureDescriber<ConfigurationNotFoundException, RequestedConfigurationNotFoundFailure> {
    @Override
    public ConfigurationNotFoundException describeFailure(RequestedConfigurationNotFoundFailure failure) {
        ConfigurationNotFoundException result = new ConfigurationNotFoundException(buildConfigurationNotFoundFailureMsg(failure));
        suggestReviewAlgorithm(result);
        return result;
    }

    private String buildConfigurationNotFoundFailureMsg(RequestedConfigurationNotFoundFailure failure) {
        return String.format("A dependency was declared on configuration '%s' which is not declared in the descriptor for %s.", failure.getRequestedName(), failure.getRequestedComponent().getDisplayName());
    }
}
