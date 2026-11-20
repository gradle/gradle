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

package org.gradle.api.internal.project;

import org.gradle.api.UnknownProjectException;
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.internal.ExceptionProblemRegistry;
import org.gradle.api.problems.internal.ProblemInternal;
import org.gradle.api.problems.internal.ProblemsInternal;
import org.gradle.util.Path;
import org.gradle.util.internal.GUtil;
import org.gradle.util.internal.NameMatcher;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.SortedSet;

/**
 * Suggests existing project paths close to a project path that could not be found.
 */
public final class ProjectPathSuggestions {

    private ProjectPathSuggestions() {
    }

    /**
     * Throws the {@link UnknownProjectException} for a project that could not be found, with an associated problem.
     * The message is extended with close existing paths, if any.
     * <p>
     * The problem is only reported if the exception fails the build, as callers of {@code project(path)} may catch it.
     * A caught exception still costs the matching against the existing paths and the creation of the problem,
     * which stays associated with the exception until the end of the build session.
     * Code probing for optional projects should use {@code findProject(path)} instead.
     *
     * @param exceptionProblemRegistry where to associate the problem with the exception
     * @param buildPath the path of the build that the existing paths belong to, or null for build tree paths
     * @param message the message describing the project that could not be found
     * @param requestedPath the path as requested, possibly relative
     * @param absoluteRequestedPath the requested path made absolute, or an empty string if it can't be
     * @param existingPaths the absolute paths of the projects that could have been meant
     * @return never returns, but using {@code throw} at the call site helps local control flow
     */
    public static RuntimeException unknownProject(
        ProblemsInternal problems,
        ExceptionProblemRegistry exceptionProblemRegistry,
        @Nullable String buildPath,
        String message,
        String requestedPath,
        String absoluteRequestedPath,
        Collection<String> existingPaths
    ) {
        SortedSet<String> candidates = findCandidates(absoluteRequestedPath, existingPaths);
        String fullMessage = message + formatCandidates(candidates);
        UnknownProjectException exception = new UnknownProjectException(fullMessage);
        ProblemInternal problem = problems.getInternalReporter().internalCreate(spec -> spec
            .id(problems.getGroups().getGradle().getBuildDefinition().problemId("Project not found"))
            .contextualLabel(fullMessage)
            .withException(exception)
            .additionalData(ProjectNotFoundData.class, data -> {
                data.setRequestedPath(requestedPath);
                data.setCandidates(new ArrayList<>(candidates));
                data.setBuildPath(buildPath);
            })
        ).toBuilder(problems.getInfrastructure()).internalSeverity(Severity.ERROR).build();
        // Associated rather than reported, so that it only surfaces if the exception fails the build
        exceptionProblemRegistry.onProblem(exception, problem);
        throw exception;
    }

    /**
     * Returns a suffix for an unknown project error message listing close existing paths, or an empty string if there are none.
     *
     * @param requestedPath the absolute path that could not be found
     * @param existingPaths the absolute paths of the projects that could have been meant
     */
    public static String formatSuggestions(String requestedPath, Collection<String> existingPaths) {
        return formatCandidates(findCandidates(requestedPath, existingPaths));
    }

    private static SortedSet<String> findCandidates(String requestedPath, Collection<String> existingPaths) {
        SortedSet<String> candidates = NameMatcher.findCandidates(requestedPath, existingPaths);
        // The root path is close to every single-character path
        candidates.remove(Path.SEPARATOR);
        return candidates;
    }

    private static String formatCandidates(SortedSet<String> candidates) {
        if (candidates.isEmpty()) {
            return "";
        }
        return " Some candidates are: " + GUtil.toString(candidates) + ".";
    }
}
