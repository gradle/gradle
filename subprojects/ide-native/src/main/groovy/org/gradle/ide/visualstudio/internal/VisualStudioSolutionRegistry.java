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

import org.gradle.api.internal.DefaultNamedDomainObjectSet;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier;

public class VisualStudioSolutionRegistry extends DefaultNamedDomainObjectSet<DefaultVisualStudioSolution> {
    private final ProjectIdentifier projectIdentifier;
    private final FileResolver fileResolver;
    private final VisualStudioProjectResolver projectResolver;

    public VisualStudioSolutionRegistry(ProjectIdentifier projectIdentifier, FileResolver fileResolver, VisualStudioProjectResolver projectResolver, Instantiator instantiator) {
        super(DefaultVisualStudioSolution.class, instantiator);
        this.projectIdentifier = projectIdentifier;
        this.fileResolver = fileResolver;
        this.projectResolver = projectResolver;
    }

    public void addSolution(DefaultVisualStudioProject visualStudioProject) {
        for (DefaultVisualStudioSolution solution : this) {
            if (solution.getRootProject() == visualStudioProject) {
                return;
            }
        }

        DefaultVisualStudioSolution solution = getInstantiator().newInstance(
                DefaultVisualStudioSolution.class, new DefaultComponentSpecIdentifier(projectIdentifier.getPath(), visualStudioProject.getName()), visualStudioProject, fileResolver, projectResolver, getInstantiator());
        add(solution);
    }
}
