/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.api.internal.artifacts;

import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.CapabilitiesResolutionInternal;
import org.gradle.internal.ImmutableActionSet;

/**
 * This is the legacy counterpart to {@link org.gradle.api.internal.artifacts.ivyservice.ResolutionParameters}.
 * The new parameters type is thread-safe, where any interactions with mutable Project state
 * are guarded by proper project locking. Otherwise, the new parameters are fully immutable.
 * <p>
 * TODO: Eventually, the data in this class should be made thread-safe and immutable in the same manner. The
 * primary restriction is that the user-provided {@link org.gradle.api.Action actions} provided by this class
 * are not necessarily isolated from the project. They are free to interact with the mutable project state without
 * proper locking. To resolve this, we should introduce a serialization and deserialization round-trip for each
 * registered action, where we deprecate (and then fail) if the user provided an action that cannot be
 * {@link org.gradle.api.IsolatedAction isolated} from the project.
 */
public interface LegacyResolutionParameters {

    /**
     * Rules that may substitute user declared dependencies for other dependencies.
     */
    ImmutableActionSet<DependencySubstitutionInternal> getDependencySubstitutionRules();

    /**
     * Rules that may resolve capability conflicts.
     */
    CapabilitiesResolutionInternal getCapabilityConflictResolutionRules();

    /**
     * Rules that specify which components dynamic version selection may select.
     */
    ComponentSelectionRulesInternal getComponentSelectionRules();

}
