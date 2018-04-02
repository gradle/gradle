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

package org.gradle.execution;

import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.cache.internal.ProducerGuard;
import org.gradle.internal.Factory;

/**
 * A build tree scoped service that coordinates access to the mutable state of a project.
 */
public class ProjectStateAccess {
    private final ProducerGuard<ProjectComponentIdentifier> guard = ProducerGuard.adaptive();

    public <T> T withProjectState(ProjectComponentIdentifier project, Factory<T> factory) {
        return guard.guardByKey(project, factory);
    }
}
