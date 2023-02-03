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

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.resolve.ModuleVersionResolveException;

public interface BuildableComponentResolveResult extends ComponentResolveResult, ResourceAwareResolveResult {
    /**
     * Marks the component as resolved, with the given graph resolution state.
     */
    void resolved(ComponentGraphResolveState state);

    /**
     * Marks the resolve as failed with the given exception.
     */
    BuildableComponentResolveResult failed(ModuleVersionResolveException failure);

    /**
     * Marks the component as not found.
     */
    void notFound(ModuleComponentIdentifier versionIdentifier);

    /**
     * Replaces the graph resolution state in the result. Result must already be resolved.
     */
    void setResult(ComponentGraphResolveState state);

}
