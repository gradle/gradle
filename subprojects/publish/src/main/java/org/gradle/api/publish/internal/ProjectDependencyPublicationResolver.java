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
package org.gradle.api.publish.internal;

import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.component.ComponentWithVariants;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.internal.text.TreeFormatter;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The start of a service that will resolve a ProjectDependency into publication coordinates, to use for publishing.
 * For now is a simple implementation, but at some point could utilise components in the dependency project, usage in the referencing project, etc.
 */
public class ProjectDependencyPublicationResolver {
    public ModuleVersionIdentifier resolve(ProjectDependency dependency) {
        Project dependencyProject = dependency.getDependencyProject();
        ((ProjectInternal) dependencyProject).evaluate();

        PublishingExtension publishing = dependencyProject.getExtensions().findByType(PublishingExtension.class);

        if (publishing == null || publishing.getPublications().withType(PublicationInternal.class).isEmpty()) {
            // Project does not apply publishing (or has no publications): simply use the project name in place of the dependency name
            return new DefaultModuleVersionIdentifier(dependency.getGroup(), dependencyProject.getName(), dependency.getVersion());
        }

        // Select all entry points. An entry point is a publication that does not contain a component whose parent is also published
        Set<? extends PublicationInternal> publications = publishing.getPublications().withType(PublicationInternal.class);
        Set<SoftwareComponent> ignored = new HashSet<SoftwareComponent>();
        for (PublicationInternal publication : publications) {
            if (publication.getComponent() != null && publication.getComponent() instanceof ComponentWithVariants) {
                ComponentWithVariants parent = (ComponentWithVariants) publication.getComponent();
                ignored.addAll(parent.getVariants());
            }
        }
        Set<PublicationInternal> topLevel = new LinkedHashSet<PublicationInternal>();
        for (PublicationInternal publication : publications) {
            if (!publication.isAlias() && (publication.getComponent() == null || !ignored.contains(publication.getComponent()))) {
                topLevel.add(publication);
            }
        }

        // See if all entry points have the same identifier
        Iterator<? extends PublicationInternal> iterator = topLevel.iterator();
        ModuleVersionIdentifier candidate = iterator.next().getCoordinates();
        while (iterator.hasNext()) {
            ModuleVersionIdentifier alternative = iterator.next().getCoordinates();
            if (!candidate.equals(alternative)) {
                TreeFormatter formatter = new TreeFormatter();
                formatter.node("Publishing is not yet able to resolve a dependency on a project with multiple publications that have different coordinates.");
                formatter.node("Found the following publications in " + dependencyProject.getDisplayName());
                formatter.startChildren();
                for (PublicationInternal publication : topLevel) {
                    formatter.node("Publication '" + publication.getName() + "' with coordinates " + publication.getCoordinates());
                }
                formatter.endChildren();
                throw new UnsupportedOperationException(formatter.toString());
            }
        }
        return candidate;

    }
}
