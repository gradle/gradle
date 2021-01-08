/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.build;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.internal.Pair;

import java.util.Set;

public interface CompositeBuildParticipantBuildState extends BuildState {

    /**
     * Identities of the modules represented by the projects of this build.
     */
    Set<Pair<ModuleVersionIdentifier, ProjectComponentIdentifier>> getAvailableModules();

    /**
     * Creates a copy of the identifier for a project in this build, to use in the dependency resolution result from some other build
     */
    ProjectComponentIdentifier idToReferenceProjectFromAnotherBuild(ProjectComponentIdentifier identifier);

}
