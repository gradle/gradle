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
package org.gradle.api.internal.artifacts.ivyservice.projectmodule;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.component.ComponentWithVariants;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.execution.ProjectConfigurer;
import org.gradle.internal.text.TreeFormatter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A service that will resolve a ProjectDependency into publication coordinates, to use for publishing.
 * For now is a simple implementation, but at some point could utilise components in the dependency project, usage in the referencing project, etc.
 */
public class ProjectDependencyPublicationResolver {
    private final ProjectPublicationRegistry publicationRegistry;
    private final ProjectConfigurer projectConfigurer;

    public ProjectDependencyPublicationResolver(ProjectPublicationRegistry publicationRegistry, ProjectConfigurer projectConfigurer) {
        this.publicationRegistry = publicationRegistry;
        this.projectConfigurer = projectConfigurer;
    }

    public ModuleVersionIdentifier resolve(ProjectDependency dependency) {
        ProjectInternal dependencyProject = (ProjectInternal) dependency.getDependencyProject();
        // Ensure target project is configured
        projectConfigurer.configureFully(dependencyProject);

        List<ProjectPublication> publications = new ArrayList<ProjectPublication>();
        for (ProjectPublication publication : publicationRegistry.getPublications(dependencyProject.getPath())) {
            if (!publication.isLegacy()) {
                publications.add(publication);
            }
        }

        if (publications.isEmpty()) {
            // Project does not apply publishing (or has no publications): simply use the project name in place of the dependency name
            return new DefaultModuleVersionIdentifier(dependency.getGroup(), dependencyProject.getName(), dependency.getVersion());
        }

        // Select all entry points. An entry point is a publication that does not contain a component whose parent is also published
        Set<SoftwareComponent> ignored = new HashSet<SoftwareComponent>();
        for (ProjectPublication publication : publications) {
            if (publication.getComponent() != null && publication.getComponent() instanceof ComponentWithVariants) {
                ComponentWithVariants parent = (ComponentWithVariants) publication.getComponent();
                ignored.addAll(parent.getVariants());
            }
        }
        Set<ProjectPublication> topLevel = new LinkedHashSet<ProjectPublication>();
        for (ProjectPublication publication : publications) {
            if (!publication.isAlias() && (publication.getComponent() == null || !ignored.contains(publication.getComponent()))) {
                topLevel.add(publication);
            }
        }

        // See if all entry points have the same identifier
        Iterator<ProjectPublication> iterator = topLevel.iterator();
        ModuleVersionIdentifier candidate = iterator.next().getCoordinates();
        while (iterator.hasNext()) {
            ModuleVersionIdentifier alternative = iterator.next().getCoordinates();
            if (!candidate.equals(alternative)) {
                TreeFormatter formatter = new TreeFormatter();
                formatter.node("Publishing is not yet able to resolve a dependency on a project with multiple publications that have different coordinates.");
                formatter.node("Found the following publications in " + dependencyProject.getDisplayName());
                formatter.startChildren();
                for (ProjectPublication publication : topLevel) {
                    formatter.node(publication.getDisplayName().getCapitalizedDisplayName() + " with coordinates " + publication.getCoordinates());
                }
                formatter.endChildren();
                throw new UnsupportedOperationException(formatter.toString());
            }
        }
        return candidate;

    }
}
