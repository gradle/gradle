/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleConflictResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState;

public interface ModuleConflictHandler {

    /**
     * Registers new module, de-selecting it and any other conflicting module if it is in
     * conflict with another registered module.
     *
     * @return true if a conflict was detected
     */
    boolean registerCandidate(CandidateModule candidate);

    /**
     * Informs whether there is any conflict at present
     */
    boolean hasConflicts();

    /**
     * Resolves next conflict.
     *
     * Must be called only if {@link #hasConflicts()} returns true.
     */
    void resolveNextConflict();

    ModuleConflictResolver<ComponentState> getResolver();

}
