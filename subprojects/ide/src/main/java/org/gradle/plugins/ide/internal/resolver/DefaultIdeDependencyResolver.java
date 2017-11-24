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

package org.gradle.plugins.ide.internal.resolver;

import com.google.common.collect.Iterables;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier;
import org.gradle.plugins.ide.internal.resolver.model.IdeExtendedRepoFileDependency;
import org.gradle.plugins.ide.internal.resolver.model.IdeLocalFileDependency;
import org.gradle.plugins.ide.internal.resolver.model.IdeProjectDependency;
import org.gradle.plugins.ide.internal.resolver.model.UnresolvedIdeRepoFileDependency;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Converts from our {@link org.gradle.api.artifacts.ArtifactCollection} and {@link ResolutionResult} API to our IDE dependency model.
 */
public class DefaultIdeDependencyResolver implements IdeDependencyResolver {

    private static final Spec<ComponentIdentifier> IS_A_PROJECT_ID = new Spec<ComponentIdentifier>() {
        @Override
        public boolean isSatisfiedBy(ComponentIdentifier id) {
            return id instanceof ProjectComponentIdentifier;
        }
    };

    private static final Spec<ComponentIdentifier> IS_A_MODULE_ID = new Spec<ComponentIdentifier>() {
        @Override
        public boolean isSatisfiedBy(ComponentIdentifier id) {
            return id instanceof ModuleComponentIdentifier;
        }
    };

    private static final Spec<ComponentIdentifier> IS_AN_UNKNOWN_COMPONENT = Specs.negate(Specs.union(IS_A_PROJECT_ID, IS_A_MODULE_ID));

    /**
     * Gets IDE project dependencies.
     *
     * @param configuration Configuration
     * @param project Project
     * @return IDE project dependencies
     */
    public List<IdeProjectDependency> getIdeProjectDependencies(Configuration configuration, Project project) {
        ProjectComponentIdentifier thisProjectId = DefaultProjectComponentIdentifier.newProjectId(project);
        List<IdeProjectDependency> ideProjectDependencies = new ArrayList<IdeProjectDependency>();
        for (ResolvedArtifactResult artifact : getLenientArtifacts(configuration, IS_A_PROJECT_ID)) {
            ProjectComponentIdentifier projectId = (ProjectComponentIdentifier) artifact.getId().getComponentIdentifier();
            if (!thisProjectId.equals(projectId)) {
                ideProjectDependencies.add(new IdeProjectDependency(projectId));
            }
        }

        return ideProjectDependencies;
    }

    /**
     * Gets unresolved IDE repository file dependencies.
     *
     * @param configuration Configuration
     * @return Unresolved IDE repository file dependencies
     */
    public List<UnresolvedIdeRepoFileDependency> getUnresolvedIdeRepoFileDependencies(Configuration configuration) {
        if (getLenientArtifacts(configuration, Specs.satisfyAll()).getFailures().isEmpty()) {
            return Collections.emptyList();
        }

        ResolutionResult result = configuration.getIncoming().getResolutionResult();
        //TODO why is this not calling "getAllDependencies"? It only finds unresolved first level dependencies.
        Iterable<UnresolvedDependencyResult> unresolvedDependencies = Iterables.filter(result.getRoot().getDependencies(), UnresolvedDependencyResult.class);

        List<UnresolvedIdeRepoFileDependency> unresolvedIdeRepoFileDependencies = new ArrayList<UnresolvedIdeRepoFileDependency>();
        for (UnresolvedDependencyResult unresolvedDependency : unresolvedDependencies) {
            Throwable failure = unresolvedDependency.getFailure();
            ComponentSelector componentSelector = unresolvedDependency.getAttempted();

            String displayName = componentSelector.getDisplayName();
            File file = new File(unresolvedFileName(componentSelector));
            unresolvedIdeRepoFileDependencies.add(new UnresolvedIdeRepoFileDependency(file, failure, displayName));
        }

        return unresolvedIdeRepoFileDependencies;
    }

    private String unresolvedFileName(ComponentSelector componentSelector) {
        return "unresolved dependency - " + componentSelector.getDisplayName().replaceAll(":", " ");
    }

    /**
     * Gets IDE repository file dependencies.
     *
     * @param configuration Configuration
     * @return IDE repository file dependencies
     */
    public List<IdeExtendedRepoFileDependency> getIdeRepoFileDependencies(Configuration configuration) {
        List<IdeExtendedRepoFileDependency> externalDependencies = new ArrayList<IdeExtendedRepoFileDependency>();
        for (ResolvedArtifactResult artifact : getLenientArtifacts(configuration, IS_A_MODULE_ID)) {
            ModuleComponentIdentifier moduleId = (ModuleComponentIdentifier) artifact.getId().getComponentIdentifier();
            IdeExtendedRepoFileDependency ideRepoFileDependency = new IdeExtendedRepoFileDependency(artifact.getFile());
            ideRepoFileDependency.setId(new DefaultModuleVersionIdentifier(moduleId.getGroup(), moduleId.getModule(), moduleId.getVersion()));
            externalDependencies.add(ideRepoFileDependency);
        }

        return externalDependencies;
    }

    /**
     * Gets IDE local file dependencies.
     *
     * @param configuration Configuration
     * @return IDE local file dependencies
     */
    public List<IdeLocalFileDependency> getIdeLocalFileDependencies(Configuration configuration) {
        List<IdeLocalFileDependency> ideLocalFileDependencies = new ArrayList<IdeLocalFileDependency>();
        for (ResolvedArtifactResult artifact : getLenientArtifacts(configuration, IS_AN_UNKNOWN_COMPONENT)) {
            IdeLocalFileDependency ideLocalFileDependency = new IdeLocalFileDependency(artifact.getFile());
            ideLocalFileDependencies.add(ideLocalFileDependency);
        }

        return ideLocalFileDependencies;
    }

    private ArtifactCollection getLenientArtifacts(Configuration configuration, final Spec<? super ComponentIdentifier> componentFilter) {
        return configuration.getIncoming().artifactView(new Action<ArtifactView.ViewConfiguration>() {
            @Override
            public void execute(ArtifactView.ViewConfiguration viewConfiguration) {
                viewConfiguration.lenient(true);
                viewConfiguration.componentFilter(componentFilter);
            }
        }).getArtifacts();
    }
}
