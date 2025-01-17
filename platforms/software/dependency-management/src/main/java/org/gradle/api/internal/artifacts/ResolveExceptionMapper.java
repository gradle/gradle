/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.artifacts;

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.ivyservice.TypedResolveException;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.DisplayName;
import org.gradle.internal.resolve.ModuleVersionNotFoundException;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Adds additional context to exceptions thrown during resolution.
 */
public class ResolveExceptionMapper {

    private final DomainObjectContext domainObjectContext;
    private final DocumentationRegistry documentationRegistry;

    public ResolveExceptionMapper(
        DomainObjectContext domainObjectContext,
        DocumentationRegistry documentationRegistry
    ) {
        this.domainObjectContext = domainObjectContext;
        this.documentationRegistry = documentationRegistry;
    }

    @Nullable
    public TypedResolveException mapFailures(Collection<Throwable> failures, String type, DisplayName contextDisplayName) {
        if (failures.isEmpty()) {
            return null;
        }

        String displayName = contextDisplayName.getDisplayName();
        if (failures.size() > 1) {
            return new TypedResolveException(type, displayName, failures.stream().map(failure ->
                mapRepositoryOverrideFailure(displayName, failure)
            ).collect(ImmutableList.toImmutableList()));
        }

        Throwable failure = failures.iterator().next();
        return mapFailure(failure, type, displayName);
    }

    public TypedResolveException mapFailure(Throwable failure, String type, String contextDisplayName) {
        if (!(failure instanceof TypedResolveException)) {
            return new TypedResolveException(
                type,
                contextDisplayName,
                ImmutableList.of(mapRepositoryOverrideFailure(contextDisplayName, failure))
            );
        }

        TypedResolveException resolveException = (TypedResolveException) failure;

        List<Throwable> mappedCauses = resolveException.getCauses().stream()
            .map(cause -> mapRepositoryOverrideFailure(contextDisplayName, cause))
            .collect(ImmutableList.toImmutableList());

        // Keep the original exception if no changes were made to
        // the causes to avoid losing the original stack trace
        if (mappedCauses.equals(resolveException.getCauses())) {
            return resolveException;
        }

        return new TypedResolveException(resolveException.getType(), contextDisplayName, mappedCauses, resolveException.getResolutions());
    }

    // TODO: We should handle this exception at the source instead of using instanceof to detect it after it is thrown.
    //       We should try to avoid catching and analyzing runtime exceptions
    public Throwable mapRepositoryOverrideFailure(String contextDisplayName, Throwable failure) {
        if (!(failure instanceof ModuleVersionNotFoundException) || !settingsRepositoriesIgnored()) {
            return failure;
        }

        ImmutableList<String> resolutions = ImmutableList.of(
            "The project declares repositories, effectively ignoring the repositories you have declared in the settings.\n" +
                "To determine how project repositories are declared, configure your build to fail on project repositories.\n" +
                documentationRegistry.getDocumentationRecommendationFor("information", "declaring_repositories", "sub:fail_build_on_project_repositories")
        );

        return new TypedResolveException(
            "dependencies",
            contextDisplayName,
            Collections.singleton(failure),
            resolutions
        );
    }

    private boolean settingsRepositoriesIgnored() {
        if (!(domainObjectContext instanceof ProjectInternal)) {
            return false;
        }

        ProjectInternal project = (ProjectInternal) domainObjectContext;

        boolean hasSettingsRepos;
        try {
            hasSettingsRepos = !project.getGradle().getSettings().getDependencyResolutionManagement().getRepositories().isEmpty();
        } catch (Throwable e) {
            // To catch `The settings are not yet available for` error
            return false;
        }

        boolean hasProjectRepos = !project.getRepositories().isEmpty();
        return hasProjectRepos && hasSettingsRepos;
    }

}
