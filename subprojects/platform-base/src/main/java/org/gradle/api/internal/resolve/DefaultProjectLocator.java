/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.resolve;

import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.project.ProjectInternal;

public class DefaultProjectLocator implements ProjectLocator {
    private final ProjectFinder delegate;

    public DefaultProjectLocator(ProjectFinder delegate) {
        this.delegate = delegate;
    }

    public ProjectInternal locateProject(String path) {
        ProjectInternal referencedProject = delegate.getProject(path);
        // TODO This is a brain-dead way to ensure that the reference project's model is ready to access
        if (referencedProject!=null) {
            referencedProject.evaluate();
            referencedProject.getTasks().discoverTasks();
        }
        return referencedProject;
    }
}
