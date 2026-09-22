/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ResolutionParameters;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.internal.DisplayName;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reports projects that dependency resolution cannot tell apart, because they share the same
 * synthetic module coordinates. See <a href="https://github.com/gradle/gradle/issues/847">gradle/gradle#847</a>.
 */
public class ProjectCoordinateCollisionReporter {

    private static final ProblemId PROBLEM_ID = ProblemId.create(
        "project-coordinate-collision",
        "Project coordinate collision",
        GradleCoreProblemGroup.dependencyResolution()
    );

    private final Problems problems;

    public ProjectCoordinateCollisionReporter(Problems problems) {
        this.problems = problems;
    }

    /**
     * Reports the collisions observed while resolving one dependency graph: for each module, the
     * distinct projects that resolution merged into a single component of that module.
     */
    public void report(
        Map<ModuleIdentifier, Set<ProjectComponentIdentifier>> collisions,
        DisplayName resolution,
        ResolutionParameters.FailureResolutions failureResolutions
    ) {
        collisions.forEach((module, projects) -> reportCollision(module, projects, resolution, failureResolutions));
    }

    private void reportCollision(
        ModuleIdentifier module,
        Set<ProjectComponentIdentifier> projects,
        DisplayName resolution,
        ResolutionParameters.FailureResolutions failureResolutions
    ) {
        problems.getReporter().report(PROBLEM_ID, problem -> {
            problem
                .contextualLabel(collisionMessage(module, projects, resolution))
                .details(detailsMessage(module, failureResolutions))
                // .documentedAt() // TODO add documentation?
                .solution(
                    """
                        Set a distinct `project.group` for each colliding project.
                        For example, set `group = "com.example.app"` in the `:app:service` subproject and `group = "com.example.lib"` in the `:lib:service` subproject.
                        """.trim()
                )
                .solution(
                    """
                        Rename one of the projects in `settings.gradle(.kts)`.
                        For example, prevent a clash on two 'service' subprojects by prefixing the name with the project path:
                        `project(":lib:service").name = "lib-service"` and `project(":app:service").name = "app-service"`.
                        """.trim()
                );
        });
    }

    private static String detailsMessage(
        ModuleIdentifier module,
        ResolutionParameters.FailureResolutions failureResolutions
    ) {
        var sb = new StringBuilder();
        sb.append("""
            Multiple projects have the same name and group values.
            Gradle cannot tell them apart, so a dependency on one project can resolve to another.
            """);

        List<String> insight = failureResolutions.forProjectCoordinateCollision(module);
        if (!insight.isEmpty()) {
            sb.append("\n");
            sb.append(String.join("\n", insight));
        }

        return sb.toString().trim();
    }

    private static String collisionMessage(
        ModuleIdentifier module,
        Set<ProjectComponentIdentifier> projects,
        DisplayName resolution
    ) {
        String projectPaths = projects.stream()
            .map(ProjectComponentIdentifier::getBuildTreePath)
            .sorted()
            .collect(Collectors.joining(", "));

        return resolution.getCapitalizedDisplayName() + " resolves projects " + projectPaths + " as the same module '" + module + "'.";
    }
}
