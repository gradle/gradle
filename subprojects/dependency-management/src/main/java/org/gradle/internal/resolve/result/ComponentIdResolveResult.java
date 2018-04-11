/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.resolve.result;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.resolve.ModuleVersionResolveException;

import javax.annotation.Nullable;

/**
 * The result of resolving a module version selector to a particular component id.
 * The result may optionally include the meta-data for the selected component, if it is cheaply available (for example, it was used to select the component).
 */
public interface ComponentIdResolveResult extends ResolveResult {
    /**
     * Returns the resolve failure, if any.
     */
    @Nullable
    ModuleVersionResolveException getFailure();

    /**
     * Returns the identifier of the component.
     *
     * @throws org.gradle.internal.resolve.ModuleVersionResolveException If resolution was unsuccessful and the id is unknown.
     */
    ComponentIdentifier getId();

    /**
     * Returns the module version id of the component.
     *
     * @throws org.gradle.internal.resolve.ModuleVersionResolveException If resolution was unsuccessful and the id is unknown.
     */
    ModuleVersionIdentifier getModuleVersionId();

    /**
     * Returns the meta-data for the component, if it was available at resolve time.
     *
     * @throws ModuleVersionResolveException If resolution was unsuccessful and the descriptor is not available.
     */
    @Nullable
    ComponentResolveMetadata getMetadata();

    /**
     * Returns true if the component id was resolved, but it was rejected by constraint.
     */
    boolean isRejected();

}
