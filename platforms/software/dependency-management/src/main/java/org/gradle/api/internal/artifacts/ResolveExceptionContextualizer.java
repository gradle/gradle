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
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.ivyservice.DefaultLenientConfiguration;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.exceptions.ResolutionProvider;
import org.gradle.internal.resolve.ModuleVersionNotFoundException;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Adds additional hints to a {@link ResolveException} given the context of a {@link ResolveContext}.
 */
public class ResolveExceptionContextualizer {

    private final DomainObjectContext domainObjectContext;
    private final DocumentationRegistry documentationRegistry;

    public ResolveExceptionContextualizer(
        DomainObjectContext domainObjectContext,
        DocumentationRegistry documentationRegistry
    ) {
        this.domainObjectContext = domainObjectContext;
        this.documentationRegistry = documentationRegistry;
    }

    @Nullable
    public ResolveException mapFailures(Collection<Throwable> failures, String contextDisplayName, String type) {
        if (failures.isEmpty()) {
            return null;
        }

        if (failures.size() > 1) {
            return new DefaultLenientConfiguration.ArtifactResolveException(type, contextDisplayName, failures);
        }

        Throwable failure = failures.iterator().next();
        return mapFailure(failure, type, contextDisplayName);
    }

    public ResolveException contextualize(Throwable e, ResolveContext resolveContext) {
        return mapFailure(e, "dependencies", resolveContext.getDisplayName());
    }

    private ResolveException mapFailure(Throwable failure, String type, String contextDisplayName) {
        Collection<? extends Throwable> causes = failure instanceof ResolveException
            ? ((ResolveException) failure).getCauses()
            : Collections.singleton(failure);

        ResolveException detected = detectRepositoryOverride(contextDisplayName, causes);
        if (detected != null) {
            return detected;
        }

        if (failure instanceof ResolveException) {
            return (ResolveException) failure;
        }

        return new DefaultLenientConfiguration.ArtifactResolveException(type, contextDisplayName, Collections.singleton(failure));
    }

    @Nullable
    private ResolveException detectRepositoryOverride(String contextDisplayName, Collection<? extends Throwable> causes) {
        try {
            boolean ignoresSettingsRepositories = false;
            if (domainObjectContext instanceof ProjectInternal) {
                ProjectInternal project = (ProjectInternal) domainObjectContext;
                ignoresSettingsRepositories = !project.getRepositories().isEmpty() &&
                    !project.getGradle().getSettings().getDependencyResolutionManagement().getRepositories().isEmpty();
            }

            boolean hasModuleNotFound = causes.stream().anyMatch(ModuleVersionNotFoundException.class::isInstance);

            if (ignoresSettingsRepositories && hasModuleNotFound) {
                return new ResolveExceptionWithHints(contextDisplayName, causes,
                    "The project declares repositories, effectively ignoring the repositories you have declared in the settings.\n" +
                        "You can figure out how project repositories are declared by configuring your build to fail on project repositories.\n" +
                        documentationRegistry.getDocumentationRecommendationFor("information", "declaring_repositories", "sub:fail_build_on_project_repositories")
                );
            }

            return null;
        } catch (Throwable e) {
            // To catch `The settings are not yet available for` error
            return new ResolveException(contextDisplayName, ImmutableList.<Throwable>builder().addAll(causes).add(e).build());
        }
    }

    public static class ResolveExceptionWithHints extends ResolveException implements ResolutionProvider {

        private final List<String> resolutions;

        public ResolveExceptionWithHints(String resolveContext, Iterable<? extends Throwable> causes, String resolution) {
            super(resolveContext, causes);
            this.resolutions = ImmutableList.of(resolution);
        }

        @Override
        public List<String> getResolutions() {
            return resolutions;
        }
    }

}
