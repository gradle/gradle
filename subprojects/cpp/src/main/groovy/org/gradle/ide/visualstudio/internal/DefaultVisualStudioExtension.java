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
import org.gradle.ide.visualstudio.VisualStudioExtension;
import org.gradle.ide.visualstudio.VisualStudioProject;
import org.gradle.ide.visualstudio.VisualStudioSolution;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.nativebinaries.FlavorContainer;
import org.gradle.nativebinaries.internal.resolve.ProjectLocator;
import org.gradle.nativebinaries.platform.PlatformContainer;

public class DefaultVisualStudioExtension implements VisualStudioExtension {
    private final VisualStudioProjectRegistry projectRegistry;
    private final VisualStudioSolutionRegistry solutionRegistry;

    public DefaultVisualStudioExtension(Instantiator instantiator, ProjectLocator projectLocator, FileResolver fileResolver,
                                        FlavorContainer flavors, PlatformContainer platforms) {
        VisualStudioProjectResolver projectResolver = new VisualStudioProjectResolver(projectLocator);
        VisualStudioProjectMapper projectMapper = new VisualStudioProjectMapper(flavors, platforms);
        projectRegistry = new VisualStudioProjectRegistry(fileResolver, projectResolver, projectMapper, instantiator);
        solutionRegistry = new VisualStudioSolutionRegistry(fileResolver, projectResolver, projectRegistry, instantiator);
    }

    public NamedDomainObjectSet<? extends VisualStudioProject> getProjects() {
        return projectRegistry;
    }

    public VisualStudioProjectRegistry getProjectRegistry() {
        return projectRegistry;
    }

    public NamedDomainObjectSet<? extends VisualStudioSolution> getSolutions() {
        return solutionRegistry;
    }

    public VisualStudioSolutionRegistry getSolutionRegistry() {
        return solutionRegistry;
    }
}
