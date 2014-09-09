/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.initialization;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.internal.project.ProjectRegistry;

public interface ProjectSpec {
    /**
     * Determines whether the given registry contains at least 1 project which meets this spec.
     */
    boolean containsProject(ProjectRegistry<? extends ProjectIdentifier> registry);

    /**
     * Returns the single project in the given registry which meets this spec.
     * @return the project.
     * @throws InvalidUserDataException When project cannot be selected due to some user input mismatch, or when there are no matching projects
     * or multiple matching projects.
     */
    <T extends ProjectIdentifier> T selectProject(ProjectRegistry<? extends T> registry) throws InvalidUserDataException;
}
