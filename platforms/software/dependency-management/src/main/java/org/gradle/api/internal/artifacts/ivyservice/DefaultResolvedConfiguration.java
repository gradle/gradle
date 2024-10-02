/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.LenientConfiguration;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.internal.artifacts.configurations.ResolutionHost;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.VisitedGraphResults;
import org.gradle.api.specs.Spec;
import org.gradle.internal.deprecation.DeprecationLogger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DefaultResolvedConfiguration implements ResolvedConfiguration {
    private final VisitedGraphResults graphResults;
    private final ResolutionHost resolutionHost;
    private final VisitedArtifactSet visitedArtifacts;
    private final LenientConfigurationInternal configuration;

    public DefaultResolvedConfiguration(
        VisitedGraphResults graphResults,
        ResolutionHost resolutionHost,
        VisitedArtifactSet visitedArtifacts,
        LenientConfigurationInternal configuration
    ) {
        this.graphResults = graphResults;
        this.resolutionHost = resolutionHost;
        this.visitedArtifacts = visitedArtifacts;
        this.configuration = configuration;
    }

    @Override
    public boolean hasError() {
        return graphResults.hasAnyFailure();
    }

    @Override
    public void rethrowFailure() throws ResolveException {
        if (!graphResults.hasAnyFailure()) {
            return;
        }

        List<Throwable> failures = new ArrayList<>();
        graphResults.visitFailures(failures::add);
        resolutionHost.rethrowFailuresAndReportProblems("dependencies", failures);
    }

    @Override
    public LenientConfiguration getLenientConfiguration() {
        return configuration;
    }

    @Override
    @Deprecated
    public Set<File> getFiles() throws ResolveException {
        DeprecationLogger.deprecateMethod(ResolvedConfiguration.class, "getFiles()")
            .withAdvice("Use Configuration#getFiles instead.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "deprecate_legacy_configuration_get_files")
            .nagUser();

        ResolvedFilesCollectingVisitor visitor = new ResolvedFilesCollectingVisitor();
        visitedArtifacts.select(configuration.getImplicitSelectionSpec()).visitArtifacts(visitor, false);
        resolutionHost.rethrowFailuresAndReportProblems("files", visitor.getFailures());
        return visitor.getFiles();
    }

    @Override
    @Deprecated
    public Set<File> getFiles(final Spec<? super Dependency> dependencySpec) throws ResolveException {
        DeprecationLogger.deprecateMethod(ResolvedConfiguration.class, "getFiles(Spec)")
            .withAdvice("Use an ArtifactView with a componentFilter instead.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "deprecate_filtered_configuration_file_and_filecollection_methods")
            .nagUser();

        ResolvedFilesCollectingVisitor visitor = new ResolvedFilesCollectingVisitor();
        configuration.select(dependencySpec).visitArtifacts(visitor, false);
        resolutionHost.rethrowFailuresAndReportProblems("files", visitor.getFailures());
        return visitor.getFiles();
    }

    @Override
    public Set<ResolvedDependency> getFirstLevelModuleDependencies() throws ResolveException {
        rethrowFailure();
        return configuration.getFirstLevelModuleDependencies();
    }

    @Override
    @Deprecated
    public Set<ResolvedDependency> getFirstLevelModuleDependencies(Spec<? super Dependency> dependencySpec) throws ResolveException {
        DeprecationLogger.deprecateMethod(ResolvedConfiguration.class, "getFirstLevelModuleDependencies(Spec)")
            .withAdvice("Use getFirstLevelModuleDependencies() instead.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "deprecate_filtered_configuration_file_and_filecollection_methods")
            .nagUser();

        rethrowFailure();

        // Disable deprecation, since the lenient configuration method also emits a deprecation warning
        return DeprecationLogger.whileDisabled(() -> configuration.getFirstLevelModuleDependencies(dependencySpec));
    }

    @Override
    public Set<ResolvedArtifact> getResolvedArtifacts() throws ResolveException {
        ArtifactCollectingVisitor visitor = new ArtifactCollectingVisitor();
        visitedArtifacts.select(configuration.getImplicitSelectionSpec()).visitArtifacts(visitor, false);
        resolutionHost.rethrowFailuresAndReportProblems("artifacts", visitor.getFailures());
        return visitor.getArtifacts();
    }
}
