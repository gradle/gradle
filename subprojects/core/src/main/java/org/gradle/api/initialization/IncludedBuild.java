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

import java.io.File;

/**
 * A build that is included in the composite.
 */
@Incubating
@HasInternalProtocol
public interface IncludedBuild {
    /**
     * The root directory of the included build.
     */
    File getProjectDir();

    /**
     * Configures the set of dependency substitution rules for this configuration.  The action receives an instance of {@link DependencySubstitutions} which
     * can then be configured with substitution rules.
     * <p/>
     * Examples:
     * <pre autoTested=''>
     * // add dependency substitution rules
     * configurations.all {
     *   resolutionStrategy.dependencySubstitution {
     *     // Substitute project and module dependencies
     *     substitute module('org.gradle:api') with project(':api')
     *     substitute project(':util') with module('org.gradle:util:3.0')
     *
     *     // Substitute one module dependency for another
     *     substitute module('org.gradle:api:2.0') with module('org.gradle:api:2.1')
     *   }
     * }
     * </pre>
     *
     * @see DependencySubstitutions
     * @since 2.5
     */
    @Incubating
    void dependencySubstitution(Action<? super DependencySubstitutions> action);
}
