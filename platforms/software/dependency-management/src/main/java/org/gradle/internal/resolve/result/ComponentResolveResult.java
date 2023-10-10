/*
 * Copyright 2012 the original author or authors.
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
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.ComponentGraphSpecificResolveState;
import org.gradle.internal.resolve.ModuleVersionResolveException;

import javax.annotation.Nullable;

/**
 * The result of resolving a module version selector to a particular component.
 *
 * <p>Very similar to {@link org.gradle.internal.resolve.result.ComponentIdResolveResult}, could probably merge these.
 */
public interface ComponentResolveResult extends ResolveResult {

    /**
     * Returns the identifier of the component.
     */
    ComponentIdentifier getId();

    /**
     * Returns the module version id of the component.
     *
     * @throws org.gradle.internal.resolve.ModuleVersionResolveException If resolution was unsuccessful and the id is unknown.
     */
    ModuleVersionIdentifier getModuleVersionId() throws ModuleVersionResolveException;

    /**
     * Returns the graph resolution state for the component.
     *
     * @throws ModuleVersionResolveException If resolution was unsuccessful and the descriptor is not available.
     */
    ComponentGraphResolveState getState() throws ModuleVersionResolveException;

    /**
     * Returns the graph specific resolution state for the component.
     *
     * @throws ModuleVersionResolveException If resolution was unsuccessful and the descriptor is not available.
     */
    ComponentGraphSpecificResolveState getGraphState() throws ModuleVersionResolveException;

    /**
     * Returns the resolve failure, if any.
     */
    @Override
    @Nullable
    ModuleVersionResolveException getFailure();
}
