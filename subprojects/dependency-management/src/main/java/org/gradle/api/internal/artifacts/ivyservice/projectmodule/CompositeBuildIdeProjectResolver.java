/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.service.ServiceRegistry;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class CompositeBuildIdeProjectResolver {
    private final CompositeProjectComponentRegistry discovered;
    private final List<ProjectArtifactBuilder> artifactBuilders;

    public CompositeBuildIdeProjectResolver(ServiceRegistry services) {
        List<CompositeProjectComponentRegistry> registries = services.getAll(CompositeProjectComponentRegistry.class);
        if (!registries.isEmpty()) {
            discovered = registries.iterator().next();
        } else {
            discovered = null;
        }
        artifactBuilders = services.getAll(ProjectArtifactBuilder.class);
    }

    public File getProjectDirectory(String projectPath) {
        ProjectComponentIdentifier projectComponentIdentifier = DefaultProjectComponentIdentifier.newId(projectPath);
        return getRegistry().getProjectDirectory(projectComponentIdentifier);
    }

    public Set<ProjectComponentIdentifier> getProjectsInComposite() {
        if (discovered == null) {
            return Collections.emptySet();
        }
        return getRegistry().getAllProjects();
    }

    // TODO:DAZ Push this into dependency resolution, getting artifact by type
    public File getImlArtifact(ProjectComponentIdentifier project) {
        String rootProjectPath = project.getProjectPath().split("::")[0] + "::";
        File buildDir = getProjectDirectory(rootProjectPath);
        File projectDir = getProjectDirectory(project.getProjectPath());

        // TODO:DAZ This isn't good: doesn't take into account project configuration. Need this to be "published" by project.
        String name = projectDir.getName();
        File imlFile = new File(projectDir, name + ".iml");
        IvyArtifactName ivyArtifactName = new DefaultIvyArtifactName(name, "iml", "iml", null);
        String taskPath = project.getProjectPath().equals(rootProjectPath) ? ":ideaModule" : project.getProjectPath().substring(rootProjectPath.length() - 1) + ":ideaModule";
        CompositeProjectComponentArtifactMetaData artifactMetaData =
            new CompositeProjectComponentArtifactMetaData(project, ivyArtifactName, imlFile, buildDir, Collections.singleton(taskPath));

        for (ProjectArtifactBuilder artifactBuilder : artifactBuilders) {
            artifactBuilder.build(artifactMetaData);
        }

        return imlFile;
    }

    private CompositeProjectComponentRegistry getRegistry() {
        if (discovered == null) {
            throw new IllegalStateException("Not a composite");
        }
        return discovered;
    }

}
