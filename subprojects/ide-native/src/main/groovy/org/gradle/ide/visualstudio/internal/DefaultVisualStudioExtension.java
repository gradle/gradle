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
package org.gradle.ide.visualstudio.internal;

import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.internal.resolve.ProjectModelResolver;
import org.gradle.ide.visualstudio.VisualStudioProject;
import org.gradle.ide.visualstudio.VisualStudioSolution;
import org.gradle.internal.reflect.Instantiator;

public class DefaultVisualStudioExtension implements VisualStudioExtensionInternal {
    private final VisualStudioProjectRegistry projectRegistry;
    private final VisualStudioSolutionRegistry solutionRegistry;

    public DefaultVisualStudioExtension(ProjectIdentifier projectIdentifier, Instantiator instantiator, ProjectModelResolver projectModelResolver, FileResolver fileResolver) {
        VisualStudioProjectMapper projectMapper = new VisualStudioProjectMapper();
        projectRegistry = new VisualStudioProjectRegistry(projectIdentifier, fileResolver, projectMapper, instantiator);
        VisualStudioProjectResolver projectResolver = new VisualStudioProjectResolver(projectModelResolver);
        solutionRegistry = new VisualStudioSolutionRegistry(projectIdentifier, fileResolver, projectResolver, instantiator);
    }

    @Override
    public NamedDomainObjectSet<? extends VisualStudioProject> getProjects() {
        return projectRegistry;
    }

    @Override
    public VisualStudioProjectRegistry getProjectRegistry() {
        return projectRegistry;
    }

    @Override
    public NamedDomainObjectSet<? extends VisualStudioSolution> getSolutions() {
        return solutionRegistry;
    }

    @Override
    public VisualStudioSolutionRegistry getSolutionRegistry() {
        return solutionRegistry;
    }
}
