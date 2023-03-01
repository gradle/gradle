/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.initialization;

import com.google.common.collect.ImmutableSortedSet;
import org.gradle.api.Project;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;

import java.util.Comparator;
import java.util.Set;

import static org.gradle.api.internal.project.ProjectHierarchyUtils.getChildProjectsForInternalUse;

public class BuildStructureOperationProject implements LoadProjectsBuildOperationType.Result.Project, ProjectsIdentifiedProgressDetails.Project {
    private static final Comparator<LoadProjectsBuildOperationType.Result.Project> PROJECT_COMPARATOR =
        Comparator.comparing(LoadProjectsBuildOperationType.Result.Project::getName);
    private final String name;
    private final String path;
    private final String identityPath;
    private final String projectDir;
    private final String buildFile;
    private final Set<BuildStructureOperationProject> children;

    public BuildStructureOperationProject(String name, String path, String identityPath, String projectDir, String buildFile, Set<BuildStructureOperationProject> children) {
        this.name = name;
        this.path = path;
        this.identityPath = identityPath;
        this.projectDir = projectDir;
        this.buildFile = buildFile;
        this.children = children;
    }

    private static BuildStructureOperationProject convert(org.gradle.api.Project project) {
        return new BuildStructureOperationProject(
            project.getName(),
            project.getPath(),
            ((ProjectInternal) project).getIdentityPath().toString(),
            project.getProjectDir().getAbsolutePath(),
            project.getBuildFile().getAbsolutePath(),
            convert(getChildProjectsForInternalUse(project)));
    }

    private static Set<BuildStructureOperationProject> convert(Iterable<Project> children) {
        ImmutableSortedSet.Builder<BuildStructureOperationProject> builder = new ImmutableSortedSet.Builder<>(PROJECT_COMPARATOR);
        for (Project child : children) {
            builder.add(convert(child));
        }
        return builder.build();
    }

    public static BuildStructureOperationProject from(GradleInternal gradle) {
        return convert(gradle.getRootProject());
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public String getIdentityPath() {
        return identityPath;
    }

    @Override
    public String getProjectDir() {
        return projectDir;
    }

    @Override
    public String getBuildFile() {
        return buildFile;
    }

    @Override
    public Set<BuildStructureOperationProject> getChildren() {
        return children;
    }
}
