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

import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;

public interface DependencyToComponentIdResolver {
    /**
     * Resolves the given dependency to a component instance. Failures should be attached to the result.
     *
     * <p>At some point in the future, this should resolve to a set of candidates rather than a single instance.
     */
    void resolve(DependencyMetadata dependency, BuildableComponentIdResolveResult result);
}
