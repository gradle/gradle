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

import org.gradle.api.artifacts.component.ComponentIdentifier;

/**
 * Represents a failure selecting a variant of a component in the graph
 * during {@link org.gradle.internal.component.resolution.failure.interfaces Variant Selection}.
 * <p>
 * When this failure occurs, we have always selected a component.
 *
 * @implSpec This interface is meant only to be extended by other interfaces, it should not
 * be implemented directly.
 */
/* package */ interface VariantSelectionFailure extends ResolutionFailure {
    /**
     * Gets the identifier of the component for which a variant could not be selected.
     *
     * @return identifier for the component for which a variant could not be selected
     */
    ComponentIdentifier getTargetComponent();
}
