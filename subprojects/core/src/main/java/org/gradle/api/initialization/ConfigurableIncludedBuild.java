/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.initialization;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.artifacts.DependencySubstitutions;
import org.gradle.internal.HasInternalProtocol;

/**
 * A build that is to be included in the composite.
 */
@Incubating
@HasInternalProtocol
public interface ConfigurableIncludedBuild extends IncludedBuild {

    /**
     * Configures the dependency substitution rules for this included build.
     *
     * The action receives an instance of {@link DependencySubstitutions} which can be configured with substitution rules.
     * Project dependencies are resolved in the context of the included build.
     *
     * @see org.gradle.api.artifacts.ResolutionStrategy#dependencySubstitution(Action)
     * @see DependencySubstitutions
     * @since 2.5
     */
    @Incubating
    void dependencySubstitution(Action<? super DependencySubstitutions> action);
}
