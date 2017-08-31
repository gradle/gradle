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
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.publish.PublishingExtension;

import java.util.Iterator;
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

        // See if all publications have the same identifier
        Set<? extends PublicationInternal> publications = publishing.getPublications().withType(PublicationInternal.class);
        Iterator<? extends PublicationInternal> iterator = publications.iterator();
        ModuleVersionIdentifier candidate = iterator.next().getCoordinates();
        while (iterator.hasNext()) {
            ModuleVersionIdentifier alternative = iterator.next().getCoordinates();
            if (!candidate.equals(alternative)) {
                throw new UnsupportedOperationException("Publishing is not yet able to resolve a dependency on a project with multiple different publications.");
            }
        }
        return candidate;

    }
}
