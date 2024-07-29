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

package org.gradle.internal.component.resolution.failure.interfaces;

import org.gradle.api.artifacts.component.ComponentSelector;

/**
 * Represents a failure selecting a component when building the graph
 * during the {@link org.gradle.internal.component.resolution.failure.interfaces Component Selection} part of dependency resolution.
 * <p>
 * When this failure occurs, we have only a component selector, and no component, as the
 * selection did not succeed.
 * <p>
 * These failures are typically caused by a missing component that does not
 * exist either locally or in any of the searched repositories.
 */
public interface ComponentSelectionFailure extends ResolutionFailure {
    /**
     * Gets the component selector that failed to select.
     *
     * @return component selector that failed to select
     */
    ComponentSelector getSelector();
}
