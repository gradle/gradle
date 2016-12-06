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

import com.google.common.collect.Sets;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.LenientConfiguration;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.component.Artifact;
import org.gradle.api.internal.artifacts.DependencyGraphNodeResult;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedFileDependencyResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactsResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedFileDependencyResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.TransientConfigurationResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.TransientConfigurationResultsLoader;
import org.gradle.api.internal.artifacts.result.DefaultResolvedArtifactResult;
import org.gradle.api.internal.artifacts.transform.ArtifactTransformer;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.Factory;
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier;
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier;
import org.gradle.internal.graph.CachingDirectedGraphWalker;
import org.gradle.internal.graph.DirectedGraphWithEdgeValues;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultLenientConfiguration implements LenientConfiguration, VisitedArtifactSet {
    private final CacheLockingManager cacheLockingManager;
    private final ConfigurationInternal configuration;
    private final Set<UnresolvedDependency> unresolvedDependencies;
    private final VisitedArtifactsResults artifactResults;
    private final VisitedFileDependencyResults fileDependencyResults;
    private final TransientConfigurationResultsLoader transientConfigurationResultsFactory;
    private final ArtifactTransformer artifactTransformer;
    // Selected for the configuration
    private final SelectedArtifactResults selectedArtifacts;
    private final SelectedFileDependencyResults selectedFileDependencies;

    public DefaultLenientConfiguration(ConfigurationInternal configuration, CacheLockingManager cacheLockingManager, Set<UnresolvedDependency> unresolvedDependencies, VisitedArtifactsResults artifactResults, VisitedFileDependencyResults fileDependencyResults, TransientConfigurationResultsLoader transientConfigurationResultsLoader, ArtifactTransformer artifactTransformer) {
        this.configuration = configuration;
        this.cacheLockingManager = cacheLockingManager;
        this.unresolvedDependencies = unresolvedDependencies;
        this.artifactResults = artifactResults;
        this.fileDependencyResults = fileDependencyResults;
        this.transientConfigurationResultsFactory = transientConfigurationResultsLoader;
        this.artifactTransformer = artifactTransformer;
        selectedArtifacts = artifactResults.select(artifactTransformer.variantSelector(configuration.getAttributes()));
        selectedFileDependencies = fileDependencyResults.select(artifactTransformer.variantSelector(configuration.getAttributes()));
    }

    @Override
    public SelectedArtifactSet select(final Spec<? super Dependency> dependencySpec, final AttributeContainerInternal requestedAttributes) {
        Transformer<HasAttributes, Collection<? extends HasAttributes>> selector = artifactTransformer.variantSelector(requestedAttributes);
        final SelectedArtifactResults artifactResults = this.artifactResults.select(selector);
        final SelectedFileDependencyResults fileDependencyResults = this.fileDependencyResults.select(selector);
        return new SelectedArtifactSet() {
            @Override
            public <T extends Collection<Object>> T collectBuildDependencies(T dest) {
                artifactResults.getArtifacts().collectBuildDependencies(dest);
                fileDependencyResults.getFiles().collectBuildDependencies(dest);
                return dest;
            }

            @Override
            public void visitArtifacts(ArtifactVisitor visitor) {
                DefaultLenientConfiguration.this.visitArtifacts(dependencySpec, requestedAttributes, artifactResults, fileDependencyResults, visitor);
            }

            /**
             * Collects files reachable from first level dependencies that satisfy the given spec. Fails when any file cannot be resolved
             */
            @Override
            public <T extends Collection<? super File>> T collectFiles(T dest) throws ResolveException {
                rethrowFailure();
                ResolvedFilesCollectingVisitor visitor = new ResolvedFilesCollectingVisitor(dest);
                try {
                    DefaultLenientConfiguration.this.visitArtifacts(dependencySpec, requestedAttributes, artifactResults, fileDependencyResults, visitor);
                    // The visitor adds file dependencies directly to the destination collection however defers adding the artifacts.
                    // This is to ensure a fixed order regardless of whether the first level dependencies are filtered or not
                    // File dependencies and artifacts are currently treated separately as a migration step
                    visitor.addArtifacts();
                } catch (Throwable t) {
                    visitor.failures.add(t);
                }
                if (!visitor.failures.isEmpty()) {
                    throw new ArtifactResolveException("files", configuration.getPath(), configuration.getDisplayName(), visitor.failures);
                }
                return dest;
            }

            /**
             * Collects all resolved artifacts. Fails when any artifact cannot be resolved.
             */
            @Override
            public <T extends Collection<? super ResolvedArtifactResult>> T collectArtifacts(T dest) throws ResolveException {
                rethrowFailure();
                ResolvedArtifactCollectingVisitor visitor = new ResolvedArtifactCollectingVisitor(dest);
                try {
                    DefaultLenientConfiguration.this.visitArtifacts(dependencySpec, requestedAttributes, artifactResults, fileDependencyResults, visitor);
                } catch (Throwable t) {
                    visitor.failures.add(t);
                }
                if (!visitor.failures.isEmpty()) {
                    throw new ArtifactResolveException("artifacts", configuration.getPath(), configuration.getDisplayName(), visitor.failures);
                }
                return dest;
            }
        };
    }

    public boolean hasError() {
        return unresolvedDependencies.size() > 0;
    }

    public Set<UnresolvedDependency> getUnresolvedModuleDependencies() {
        return unresolvedDependencies;
    }

    public void rethrowFailure() throws ResolveException {
        if (hasError()) {
            List<Throwable> failures = new ArrayList<Throwable>();
            for (UnresolvedDependency unresolvedDependency : unresolvedDependencies) {
                failures.add(unresolvedDependency.getProblem());
            }
            throw new ResolveException(configuration.toString(), failures);
        }
    }

    private TransientConfigurationResults loadTransientGraphResults(SelectedArtifactResults artifactResults) {
        return transientConfigurationResultsFactory.create(artifactResults);
    }

    public Set<ResolvedDependency> getFirstLevelModuleDependencies(Spec<? super Dependency> dependencySpec) {
        Set<ResolvedDependency> matches = new LinkedHashSet<ResolvedDependency>();
        for (DependencyGraphNodeResult node : getFirstLevelNodes(dependencySpec)) {
            matches.add(node.getPublicView());
        }
        return matches;
    }

    private Set<DependencyGraphNodeResult> getFirstLevelNodes(Spec<? super Dependency> dependencySpec) {
        Set<DependencyGraphNodeResult> matches = new LinkedHashSet<DependencyGraphNodeResult>();
        TransientConfigurationResults graphResults = loadTransientGraphResults(selectedArtifacts);
        for (Map.Entry<ModuleDependency, DependencyGraphNodeResult> entry : graphResults.getFirstLevelDependencies().entrySet()) {
            if (dependencySpec.isSatisfiedBy(entry.getKey())) {
                matches.add(entry.getValue());
            }
        }
        return matches;
    }

    public Set<ResolvedDependency> getAllModuleDependencies() {
        Set<ResolvedDependency> resolvedElements = new LinkedHashSet<ResolvedDependency>();
        Deque<ResolvedDependency> workQueue = new LinkedList<ResolvedDependency>();
        workQueue.addAll(loadTransientGraphResults(selectedArtifacts).getRootNode().getPublicView().getChildren());
        while (!workQueue.isEmpty()) {
            ResolvedDependency item = workQueue.removeFirst();
            if (resolvedElements.add(item)) {
                final Set<ResolvedDependency> children = item.getChildren();
                if (children != null) {
                    workQueue.addAll(children);
                }
            }
        }
        return resolvedElements;
    }

    @Override
    public Set<File> getFiles() {
        return getFiles(Specs.<Dependency>satisfyAll());
    }

    /**
     * Recursive but excludes unsuccessfully resolved artifacts.
     */
    public Set<File> getFiles(Spec<? super Dependency> dependencySpec) {
        Set<File> files = Sets.newLinkedHashSet();
        FilesAndArtifactCollectingVisitor visitor = new FilesAndArtifactCollectingVisitor(files);
        visitArtifacts(dependencySpec, configuration.getAttributes(), selectedArtifacts, selectedFileDependencies, visitor);
        files.addAll(getFiles(filterUnresolved(visitor.artifacts)));
        return files;
    }

    @Override
    public Set<ResolvedArtifact> getArtifacts() {
        return getArtifacts(Specs.<Dependency>satisfyAll());
    }

    /**
     * Recursive but excludes unsuccessfully resolved artifacts.
     */
    public Set<ResolvedArtifact> getArtifacts(Spec<? super Dependency> dependencySpec) {
        ArtifactCollectingVisitor visitor = new ArtifactCollectingVisitor();
        visitArtifacts(dependencySpec, configuration.getAttributes(), selectedArtifacts, selectedFileDependencies, visitor);
        return filterUnresolved(visitor.artifacts);
    }

    private Set<ResolvedArtifact> filterUnresolved(final Set<ResolvedArtifact> artifacts) {
        return cacheLockingManager.useCache("retrieve artifacts from " + configuration, new Factory<Set<ResolvedArtifact>>() {
            public Set<ResolvedArtifact> create() {
                return CollectionUtils.filter(artifacts, new IgnoreMissingExternalArtifacts());
            }
        });
    }

    private Set<File> getFiles(final Set<ResolvedArtifact> artifacts) {
        final Set<File> files = new LinkedHashSet<File>();
        cacheLockingManager.useCache("resolve files from " + configuration, new Runnable() {
            public void run() {
                for (ResolvedArtifact artifact : artifacts) {
                    File depFile = artifact.getFile();
                    if (depFile != null) {
                        files.add(depFile);
                    }
                }
            }
        });
        return files;
    }

    /**
     * Recursive, includes unsuccessfully resolved artifacts
     *
     * @param dependencySpec dependency spec
     */
    private void visitArtifacts(Spec<? super Dependency> dependencySpec, AttributeContainerInternal requestedAttributes, SelectedArtifactResults artifactResults, SelectedFileDependencyResults fileDependencyResults, ArtifactVisitor visitor) {
        ArtifactVisitor transformingVisitor = artifactTransformer.visitor(visitor, requestedAttributes);

        //this is not very nice might be good enough until we get rid of ResolvedConfiguration and friends
        //avoid traversing the graph causing the full ResolvedDependency graph to be loaded for the most typical scenario
        if (dependencySpec == Specs.SATISFIES_ALL) {
            if (transformingVisitor.includeFiles()) {
                fileDependencyResults.getFiles().visit(transformingVisitor);
            }
            artifactResults.getArtifacts().visit(transformingVisitor);
            return;
        }

        if (transformingVisitor.includeFiles()) {
            for (Map.Entry<FileCollectionDependency, ResolvedArtifactSet> entry: fileDependencyResults.getFirstLevelFiles().entrySet()) {
                if (dependencySpec.isSatisfiedBy(entry.getKey())) {
                    entry.getValue().visit(transformingVisitor);
                }
            }
        }

        CachingDirectedGraphWalker<DependencyGraphNodeResult, ResolvedArtifact> walker = new CachingDirectedGraphWalker<DependencyGraphNodeResult, ResolvedArtifact>(new ResolvedDependencyArtifactsGraph(transformingVisitor, fileDependencyResults));

        DependencyGraphNodeResult rootNode = loadTransientGraphResults(artifactResults).getRootNode();
        for (DependencyGraphNodeResult node : getFirstLevelNodes(dependencySpec)) {
            node.getArtifactsForIncomingEdge(rootNode).visit(transformingVisitor);
            walker.add(node);
        }
        walker.findValues();
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public Set<ResolvedDependency> getFirstLevelModuleDependencies() {
        return loadTransientGraphResults(selectedArtifacts).getRootNode().getPublicView().getChildren();
    }

    private static class ResolvedFilesCollectingVisitor implements ArtifactVisitor {
        private final Collection<? super File> files;
        private final List<Throwable> failures = new ArrayList<Throwable>();
        private final Set<ResolvedArtifact> artifacts = new LinkedHashSet<ResolvedArtifact>();

        ResolvedFilesCollectingVisitor(Collection<? super File> files) {
            this.files = files;
        }

        @Override
        public void visitArtifact(ResolvedArtifact artifact) {
            // Defer adding the artifacts until after all the file dependencies have been visited
            this.artifacts.add(artifact);
        }

        @Override
        public boolean includeFiles() {
            return true;
        }

        @Override
        public void visitFiles(@Nullable ComponentIdentifier componentIdentifier, Iterable<File> files) {
            try {
                for (File file : files) {
                    this.files.add(file);
                }
            } catch (Throwable t) {
                failures.add(t);
            }
        }

        public void addArtifacts() {
            for (ResolvedArtifact artifact : artifacts) {
                try {
                    this.files.add(artifact.getFile());
                } catch (Throwable t) {
                    failures.add(t);
                }
            }
        }
    }

    private static class ResolvedArtifactCollectingVisitor implements ArtifactVisitor {
        private final Collection<? super ResolvedArtifactResult> artifacts;
        private final Set<ComponentArtifactIdentifier> seenArtifacts = new HashSet<ComponentArtifactIdentifier>();
        private final Set<File> seenFiles = new HashSet<File>();
        private final List<Throwable> failures = new ArrayList<Throwable>();

        ResolvedArtifactCollectingVisitor(Collection<? super ResolvedArtifactResult> artifacts) {
            this.artifacts = artifacts;
        }

        @Override
        public void visitArtifact(ResolvedArtifact artifact) {
            try {
                if (seenArtifacts.add(artifact.getId())) {
                    // Trigger download of file, if required
                    File file = artifact.getFile();
                    this.artifacts.add(new DefaultResolvedArtifactResult(artifact.getId(), Artifact.class, file));
                }
            } catch (Throwable t) {
                failures.add(t);
            }
        }

        @Override
        public boolean includeFiles() {
            return true;
        }

        @Override
        public void visitFiles(@Nullable ComponentIdentifier componentIdentifier, Iterable<File> files) {
            try {
                for (File file : files) {
                    if (seenFiles.add(file)) {
                        ComponentArtifactIdentifier artifactIdentifier;
                        if (componentIdentifier == null) {
                            artifactIdentifier = new OpaqueComponentArtifactIdentifier(file);
                        } else {
                            artifactIdentifier = new ComponentFileArtifactIdentifier(componentIdentifier, file.getName());
                        }
                        artifacts.add(new DefaultResolvedArtifactResult(artifactIdentifier, Artifact.class, file));
                    }
                }
            } catch (Throwable t) {
                failures.add(t);
            }
        }
    }

    private static class FilesAndArtifactCollectingVisitor extends ArtifactCollectingVisitor {
        final Collection<File> files;

        FilesAndArtifactCollectingVisitor(Collection<File> files) {
            this.files = files;
        }

        @Override
        public boolean includeFiles() {
            return true;
        }

        @Override
        public void visitFiles(@Nullable ComponentIdentifier componentIdentifier, Iterable<File> files) {
            CollectionUtils.addAll(this.files, files);
        }
    }

    private static class ArtifactResolveException extends ResolveException {
        private final String type;
        private final String displayName;

        public ArtifactResolveException(String type, String path, String displayName, List<Throwable> failures) {
            super(path, failures);
            this.type = type;
            this.displayName = displayName;
        }

        // Need to override as error message is hardcoded in constructor of public type ResolveException
        @Override
        public String getMessage() {
            return String.format("Could not resolve all %s for %s.", type, displayName);
        }
    }

    private static class ResolvedDependencyArtifactsGraph implements DirectedGraphWithEdgeValues<DependencyGraphNodeResult, ResolvedArtifact> {
        private final ArtifactVisitor artifactsVisitor;
        private final SelectedFileDependencyResults fileDependencyResults;

        ResolvedDependencyArtifactsGraph(ArtifactVisitor artifactsVisitor, SelectedFileDependencyResults fileDependencyResults) {
            this.artifactsVisitor = artifactsVisitor;
            this.fileDependencyResults = fileDependencyResults;
        }

        @Override
        public void getNodeValues(DependencyGraphNodeResult node, Collection<? super ResolvedArtifact> values, Collection<? super DependencyGraphNodeResult> connectedNodes) {
            connectedNodes.addAll(node.getOutgoingEdges());
            if (artifactsVisitor.includeFiles()) {
                fileDependencyResults.getFiles(node.getNodeId()).visit(artifactsVisitor);
            }
        }

        @Override
        public void getEdgeValues(DependencyGraphNodeResult from, DependencyGraphNodeResult to,
                                  Collection<ResolvedArtifact> values) {
            to.getArtifactsForIncomingEdge(from).visit(artifactsVisitor);
        }
    }

    private static class IgnoreMissingExternalArtifacts implements Spec<ResolvedArtifact> {
        public boolean isSatisfiedBy(ResolvedArtifact element) {
            if (isExternalModuleArtifact(element)) {
                try {
                    element.getFile();
                } catch (org.gradle.internal.resolve.ArtifactResolveException e) {
                    return false;
                }
            }
            return true;
        }

        boolean isExternalModuleArtifact(ResolvedArtifact element) {
            return element.getId().getComponentIdentifier() instanceof ModuleComponentIdentifier;
        }
    }
}
