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

import org.gradle.api.Action;
import org.gradle.api.artifacts.ModuleIdentifier;

public interface PotentialConflict {
    /**
     * Executes an action with each module that participates in the conflict.
     * In a typical version conflict, e.g. org:foo:1.0 VS org:foo:2.0 there is only one participating module: org:foo.
     */
    void withParticipatingModules(Action<ModuleIdentifier> action);

    /**
     * informs whether the conflict exists
     */
    boolean conflictExists();
}
