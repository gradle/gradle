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

package org.gradle.internal.component.resolution.failure.type;

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.catalog.problems.ResolutionFailureProblemId;
import org.gradle.internal.component.resolution.failure.interfaces.VariantSelectionByNameFailure;

/**
 * An abstract {@link VariantSelectionByNameFailure} that represents the situation when a variant is requested
 * via a configuration name and this request fails.
 */
public abstract class AbstractVariantSelectionByNameFailure extends AbstractResolutionFailure implements VariantSelectionByNameFailure {
    private final ComponentIdentifier targetComponent;
    private final String requestedConfigurationName;

    public AbstractVariantSelectionByNameFailure(ResolutionFailureProblemId problemId, ComponentIdentifier targetComponent, String requestedConfigurationName) {
        super(problemId);
        this.targetComponent = targetComponent;
        this.requestedConfigurationName = requestedConfigurationName;
    }

    @Override
    public String describeRequestTarget() {
        return requestedConfigurationName;
    }

    @Override
    public ComponentIdentifier getTargetComponent() {
        return targetComponent;
    }

    @Override
    public String getRequestedConfigurationName() {
        return requestedConfigurationName;
    }
}
