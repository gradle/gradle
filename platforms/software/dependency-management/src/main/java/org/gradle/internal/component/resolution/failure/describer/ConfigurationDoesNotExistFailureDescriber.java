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

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ProjectComponentIdentifierInternal;
import org.gradle.internal.component.resolution.failure.exception.VariantSelectionByNameException;
import org.gradle.internal.component.resolution.failure.type.ConfigurationDoesNotExistFailure;
import org.gradle.util.Path;

/**
 * A {@link ResolutionFailureDescriber} that describes a {@link ConfigurationDoesNotExistFailure}.
 */
public abstract class ConfigurationDoesNotExistFailureDescriber extends AbstractResolutionFailureDescriber<ConfigurationDoesNotExistFailure> {
    @Override
    public VariantSelectionByNameException describeFailure(ConfigurationDoesNotExistFailure failure) {
        String message = buildFailureMsg(failure);

        ImmutableList.Builder<String> resolutions = ImmutableList.builder();
        boolean isLocalComponent = failure.getTargetComponent() instanceof ProjectComponentIdentifier;
        if (isLocalComponent) {
            ProjectComponentIdentifierInternal id = (ProjectComponentIdentifierInternal) failure.getTargetComponent();
            Path outgoingVariantsPath = id.getIdentityPath().append(Path.path("outgoingVariants"));
            resolutions.add("To determine which configurations are available in the target " + failure.getTargetComponent().getDisplayName() + ", run " + outgoingVariantsPath.getPath() + ".");
        }

        resolutions.addAll(buildResolutions(suggestReviewAlgorithm()));
        return new VariantSelectionByNameException(message, failure, resolutions.build());
    }

    private String buildFailureMsg(ConfigurationDoesNotExistFailure failure) {
        return String.format(
            "A dependency was declared on configuration '%s' of '%s' but no variant with that configuration name exists.",
            failure.getRequestedConfigurationName(),
            failure.getTargetComponent().getDisplayName()
        );
    }

    private String quoteNameOnly(String formattedId) {
        int projectIdIdx = formattedId.indexOf("project ");
        return projectIdIdx < 0 ? '\'' + formattedId + '\'' : formattedId.substring(0, projectIdIdx + 8) + '\'' + formattedId.substring(projectIdIdx + 8) + '\'';
    }
}
