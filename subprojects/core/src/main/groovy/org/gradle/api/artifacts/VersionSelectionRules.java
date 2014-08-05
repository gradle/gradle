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

package org.gradle.api.artifacts;

import org.gradle.api.Action;
import org.gradle.api.Incubating;

/***
 * Represents a container for version selection rules.  Rules can be applied as part of the
 * resolutionStrategy of a configuration.
 *
 * <pre>
 *     configurations {
 *         conf {
 *             resolutionStrategy {
 *                 versionSelection {
 *                     anyVersion { VersionSelection selection ->
 *                         // TODO - add a reasonable example
 *                     }
 *                 }
 *             }
 *         }
 *     }
 * </pre>
 */
@Incubating
public interface VersionSelectionRules {
    /**
     * Add a new version selection rule to the container.
     * @param selectionAction the Action or Closure that implements the rule
     * @return this
     */
    public VersionSelectionRules anyVersion(Action<? super VersionSelection> selectionAction);
}
