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
import org.gradle.api.internal.project.ProjectState;
import org.gradle.internal.build.BuildState;

import java.util.Comparator;
import java.util.Set;

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

    private static BuildStructureOperationProject convert(ProjectState project) {
        return new BuildStructureOperationProject(
            project.getName(),
            project.getIdentity().getProjectPath().asString(),
            project.getIdentity().getBuildTreePath().asString(),
            project.getProjectDir().getAbsolutePath(),
            project.getMutableModel().getBuildFile().getAbsolutePath(),
            convertAll(project.getChildProjects())
        );
    }

    private static Set<BuildStructureOperationProject> convertAll(Iterable<ProjectState> children) {
        ImmutableSortedSet.Builder<BuildStructureOperationProject> builder = new ImmutableSortedSet.Builder<>(PROJECT_COMPARATOR);
        for (ProjectState child : children) {
            builder.add(convert(child));
        }
        return builder.build();
    }

    public static BuildStructureOperationProject from(BuildState build) {
        return convert(build.getProjects().getRootProject());
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
