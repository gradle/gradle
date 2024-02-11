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

import org.apache.commons.lang.StringUtils;
import org.gradle.internal.component.ExternalConfigurationNotFoundException;
import org.gradle.internal.component.resolution.failure.type.ExternalRequestedConfigurationNotFoundFailure;

/**
 * A {@link ResolutionFailureDescriber} that describes an {@link ExternalRequestedConfigurationNotFoundFailure}.
 */
public abstract class ExternalRequestedConfigurationNotFoundFailureDescriber extends AbstractResolutionFailureDescriber<ExternalConfigurationNotFoundException, ExternalRequestedConfigurationNotFoundFailure> {
    @Override
    public ExternalConfigurationNotFoundException describeFailure(ExternalRequestedConfigurationNotFoundFailure failure) {
        ExternalConfigurationNotFoundException result = new ExternalConfigurationNotFoundException(buildExternalConfigurationNotFoundFailureMsg(failure));
        suggestReviewAlgorithm(result);
        return result;
    }

    private String buildExternalConfigurationNotFoundFailureMsg(ExternalRequestedConfigurationNotFoundFailure failure) {
        return String.format("%s declares a dependency from configuration '%s' to configuration '%s' which is not declared in the descriptor for %s.", StringUtils.capitalize(failure.getFromComponent().getDisplayName()), failure.getFromConfigurationName(), failure.getRequestedName(), failure.getRequestedComponent().getDisplayName());
    }
}
