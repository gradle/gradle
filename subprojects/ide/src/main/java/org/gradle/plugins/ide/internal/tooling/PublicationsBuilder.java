/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling;

import com.google.common.collect.Lists;
import org.gradle.api.Project;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublication;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry;
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier;
import org.gradle.tooling.internal.gradle.DefaultGradleModuleVersion;
import org.gradle.tooling.internal.gradle.DefaultGradlePublication;
import org.gradle.tooling.internal.gradle.DefaultProjectPublications;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

import java.util.List;
import java.util.Set;

class PublicationsBuilder implements ToolingModelBuilder {
    private final ProjectPublicationRegistry publicationRegistry;

    PublicationsBuilder(ProjectPublicationRegistry publicationRegistry) {
        this.publicationRegistry = publicationRegistry;
    }

    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals("org.gradle.tooling.model.gradle.ProjectPublications");
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        DefaultProjectIdentifier projectIdentifier = new DefaultProjectIdentifier(project.getRootDir(), project.getPath());
        return new DefaultProjectPublications().setPublications(publications(projectIdentifier)).setProjectIdentifier(projectIdentifier);
    }

    private List<DefaultGradlePublication> publications(DefaultProjectIdentifier projectIdentifier) {
        List<DefaultGradlePublication> gradlePublications = Lists.newArrayList();

        Set<ProjectPublication> projectPublications = publicationRegistry.getPublications(projectIdentifier.getProjectPath());
        for (ProjectPublication projectPublication : projectPublications) {
            gradlePublications.add(new DefaultGradlePublication()
                    .setId(new DefaultGradleModuleVersion(projectPublication.getId()))
                    .setProjectIdentifier(projectIdentifier)
            );
        }

        return gradlePublications;
    }
}
