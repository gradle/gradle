/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publication.maven.internal.pom;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.maven.project.MavenProject;
import org.gradle.api.Nullable;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.maven.MavenDeployer;
import org.gradle.api.artifacts.maven.MavenResolver;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.plugins.BasePluginConvention;
import org.gradle.api.tasks.Upload;

import java.util.Collection;
import java.util.Set;

/**
 * Given a project dependency, determines the artifact ID that the depended-on project
 * can be referred to from a Maven POM. Falls back to project.name if the artifact ID
 * used for publishing the depended-on project cannot be determined with certainty.
 * 
 * The main goal of this class is to fix GRADLE-443 without changing any other existing
 * behavior (e.g. when a project that gets published to a Maven repo depends on a
 * project published to an Ivy repo).
 *
 * This class should be removed as soon as we have proper support for publications.
 */
class ProjectDependencyArtifactIdExtractorHack {
    private final Project project;

    public ProjectDependencyArtifactIdExtractorHack(ProjectDependency dependency) {
        this.project = dependency.getDependencyProject();
    }

    public String extract() {
        Collection<Upload> tasks = project.getTasks().withType(Upload.class);
        Collection<ArtifactRepository> repositories = getRepositories(tasks);
        if (!onlyContainsMavenResolvers(repositories)) {
            return project.getName();
        }

        Collection<MavenDeployer> deployers = getMavenDeployers(repositories);
        Set<String> artifactIds = getArtifactIds(deployers);
        if (artifactIds.size() == 1) {
            String artifactId = artifactIds.iterator().next();
            if (artifactId != null && !artifactId.equals(MavenProject.EMPTY_PROJECT_ARTIFACT_ID)) {
                return artifactId;
            }
        }
        String baseName = getArchivesBaseName();
        return baseName != null ? baseName : project.getName();
    }

    private Collection<ArtifactRepository> getRepositories(Collection<Upload> tasks) {
        Collection<ArtifactRepository> result = Lists.newArrayList();
        for (Upload task : tasks) {
            result.addAll(task.getRepositories());
        }
        return result;
    }

    private boolean onlyContainsMavenResolvers(Collection<ArtifactRepository> repositories) {
        for (ArtifactRepository repository : repositories) {
            if (!(repository instanceof MavenResolver)) {
                return false;
            }
        }
        return true;
    }

    private Collection<MavenDeployer> getMavenDeployers(Collection<ArtifactRepository> repositories) {
        Collection<MavenDeployer> result = Lists.newArrayList();
        for (ArtifactRepository repository : repositories) {
            if (repository instanceof MavenDeployer) {
                result.add((MavenDeployer) repository);
            }
        }
        return result;
    }

    private Set<String> getArtifactIds(Collection<MavenDeployer> deployers) {
        Set<String> result = Sets.newHashSet();
        for (MavenDeployer deployer : deployers) {
            result.add(deployer.getPom().getArtifactId());
        }
        return result;
    }

    @Nullable
    private String getArchivesBaseName() {
        BasePluginConvention convention = project.getConvention().findPlugin(BasePluginConvention.class);
        return convention != null ? convention.getArchivesBaseName() : null;
    }
}
