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

/**
 * A {@link ResolutionFailure} that represents a specialization of {@link RequestedConfigurationNotFoundFailure} when
 * a configuration is requested by name on an external dependency and does not exist on the target component.
 */
public final class ExternalRequestedConfigurationNotFoundFailure extends RequestedConfigurationNotFoundFailure {
    private final ComponentIdentifier fromComponent;
    private final String fromConfigurationName;

    public ExternalRequestedConfigurationNotFoundFailure(String requestedName, ComponentIdentifier requestedComponent, ComponentIdentifier fromComponent, String fromConfigurationName) {
        super(requestedName, requestedComponent);
        this.fromComponent = fromComponent;
        this.fromConfigurationName = fromConfigurationName;
    }

    public ComponentIdentifier getFromComponent() {
        return fromComponent;
    }

    public String getFromConfigurationName() {
        return fromConfigurationName;
    }
}
