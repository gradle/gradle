/*
 * Copyright 2007-2008 the original author or authors.
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

import org.gradle.api.Project;
import org.gradle.internal.HasInternalProtocol;

/**
 * <p>A {@code ProjectDependency} is a {@link Dependency} on another project in the current project hierarchy.</p>
 */
@HasInternalProtocol
public interface ProjectDependency extends ModuleDependency, SelfResolvingDependency {
    /**
     * Returns the project associated with this project dependency.
     */
    Project getDependencyProject();

    /**
     * {@inheritDoc}
     */
    @Override
    ProjectDependency copy();
}
