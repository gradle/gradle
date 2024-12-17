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

package org.gradle.internal.resolve.resolver;

import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;

import javax.annotation.Nullable;

/**
 * Responsible for taking a dependency declaration and locating the matching component. The component can be returned either the resolution state for the component, if this state is cheaply available
 * to this resolver, or an id for the component if not.
 *
 * <p>At some point in the future, this should resolve to a set of candidates rather than a single instance.
 */
public interface DependencyToComponentIdResolver {
    /**
     * Resolves the given selector to a component instance. Failures should be attached to the result.
     *
     * <p>At some point in the future, this should resolve to a set of candidates rather than a single instance.
     */
    void resolve(ComponentSelector selector, ComponentOverrideMetadata overrideMetadata, VersionSelector acceptor, @Nullable VersionSelector rejector, BuildableComponentIdResolveResult result);
}
