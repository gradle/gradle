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

package org.gradle.execution.taskpath;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.util.NameMatcher;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ProjectFinderByTaskPath {

    public ProjectInternal findProject(String projectPath, ProjectInternal startFrom, Collection<? extends IncludedBuildState> includedBuilds) {
        if (projectPath.equals(Project.PATH_SEPARATOR)) {
            return startFrom.getRootProject();
        }
        Project current = startFrom;
        if (projectPath.startsWith(Project.PATH_SEPARATOR)) {
            current = current.getRootProject();
            projectPath = projectPath.substring(1);
        }

        List<String> projectPathElements = Arrays.asList(projectPath.split(Project.PATH_SEPARATOR));
        if (!projectPathElements.isEmpty()) {
            String includedBuildName = projectPathElements.get(0);
            IncludedBuildState includedBuild = findIncludedBuild(includedBuildName, includedBuilds);
            if (includedBuild != null) {
                current = includedBuild.getConfiguredBuild().getRootProject();
                projectPathElements = projectPathElements.subList(1, projectPathElements.size());
            }
        }

        for (String pattern : projectPathElements) {
            Map<String, Project> children = current.getChildProjects();

            NameMatcher matcher = new NameMatcher();
            Project child = matcher.find(pattern, children);
            if (child != null) {
                current = child;
                continue;
            }

            throw new ProjectLookupException(matcher.formatErrorMessage("project", current));
        }

        return (ProjectInternal) current;
    }

    private IncludedBuildState findIncludedBuild(String name, Collection<? extends IncludedBuildState> includedBuilds) {
        if (includedBuilds.isEmpty()) {
            return null;
        }

        Map<String, IncludedBuildState> builds = includedBuilds.stream().collect(Collectors.toMap(IncludedBuildState::getName, Function.identity()));
        NameMatcher matcher = new NameMatcher();
        return matcher.find(name, builds);
    }

    public static class ProjectLookupException extends InvalidUserDataException {
        public ProjectLookupException(String message) {
            super(message);
        }
    }
}
