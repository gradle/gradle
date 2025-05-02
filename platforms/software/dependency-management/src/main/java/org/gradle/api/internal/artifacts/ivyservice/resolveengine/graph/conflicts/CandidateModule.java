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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts;

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState;

import java.util.Collection;

/**
 * Module that participates in conflict resolution. Contains id of the module and candidate versions.
 */
public interface CandidateModule {
    /**
     * Id of this module
     */
    ModuleIdentifier getId();

    /**
     * Candidate versions of this module. Many times, it has only single version.
     */
    Collection<ComponentState> getVersions();

    void replaceWith(ComponentState selected);
}
