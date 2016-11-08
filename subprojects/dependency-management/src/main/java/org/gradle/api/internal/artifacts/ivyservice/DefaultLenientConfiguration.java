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
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.component.Artifact;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.DefaultResolvedDependency;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.FileDependencyResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifacts;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.TransientConfigurationResults;
import org.gradle.api.internal.artifacts.result.DefaultResolvedArtifactResult;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.Factory;
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

public class DefaultLenientConfiguration implements LenientConfiguration, ArtifactResults {
    private CacheLockingManager cacheLockingManager;
    private final ConfigurationInternal configuration;
    private final Set<UnresolvedDependency> unresolvedDependencies;
    private final ResolvedArtifacts artifactResults;
    private final FileDependencyResults fileDependencyResults;
    private final Factory<TransientConfigurationResults> transientConfigurationResultsFactory;

    public DefaultLenientConfiguration(ConfigurationInternal configuration, CacheLockingManager cacheLockingManager, Set<UnresolvedDependency> unresolvedDependencies,
                                       ResolvedArtifacts artifactResults, FileDependencyResults fileDependencyResults, Factory<TransientConfigurationResults> transientConfigurationResultsLoader) {
        this.configuration = configuration;
        this.cacheLockingManager = cacheLockingManager;
        this.unresolvedDependencies = unresolvedDependencies;
        this.artifactResults = artifactResults;
        this.fileDependencyResults = fileDependencyResults;
        this.transientConfigurationResultsFactory = transientConfigurationResultsLoader;
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

    public Set<ResolvedArtifact> getResolvedArtifacts() throws ResolveException {
        return artifactResults.getArtifacts();
    }

    private TransientConfigurationResults loadTransientGraphResults() {
        return transientConfigurationResultsFactory.create();
    }

    public Set<ResolvedDependency> getFirstLevelModuleDependencies(Spec<? super Dependency> dependencySpec) {
        Set<ResolvedDependency> matches = new LinkedHashSet<ResolvedDependency>();
        for (Map.Entry<ModuleDependency, ResolvedDependency> entry : loadTransientGraphResults().getFirstLevelDependencies().entrySet()) {
            if (dependencySpec.isSatisfiedBy(entry.getKey())) {
                matches.add(entry.getValue());
            }
        }
        return matches;
    }

    public Set<ResolvedDependency> getAllModuleDependencies() {
        Set<ResolvedDependency> resolvedElements = new LinkedHashSet<ResolvedDependency>();
        Deque<ResolvedDependency> workQueue = new LinkedList<ResolvedDependency>();
        workQueue.addAll(loadTransientGraphResults().getRoot().getChildren());
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

    /**
     * Recursive but excludes unsuccessfully resolved artifacts.
     */
    public Set<File> getFiles(Spec<? super Dependency> dependencySpec) {
        Set<File> files = Sets.newLinkedHashSet();
        FilesAndArtifactCollectingVisitor visitor = new FilesAndArtifactCollectingVisitor(files);
        visitArtifacts(dependencySpec, visitor);
        files.addAll(getFiles(filterUnresolved(visitor.artifacts)));
        return files;
    }

    /**
     * Collects files reachable from first level dependencies that satisfy the given spec. Rethrows first failure encountered.
     */
    public void collectFiles(Spec<? super Dependency> dependencySpec, Collection<File> dest) {
        ResolvedFilesCollectingVisitor visitor = new ResolvedFilesCollectingVisitor(dest);
        try {
            visitArtifacts(dependencySpec, visitor);
            // The visitor adds file dependencies directly to the destination collection however defers adding the artifacts. This is to ensure a fixed order regardless of whether the first level dependencies are filtered or not
            // File dependencies and artifacts are currently treated separately as a migration step
            visitor.addArtifacts();
        } catch (Throwable t) {
            visitor.failures.add(t);
        }
        if (!visitor.failures.isEmpty()) {
            throw new ArtifactResolveException("files", configuration.getPath(), configuration.getDisplayName(), visitor.failures);
        }
    }

    /**
     * Collects all artifacts. Rethrows first failure encountered.
     */
    public void collectArtifacts(Collection<? super ResolvedArtifactResult> dest) {
        ResolvedArtifactCollectingVisitor visitor = new ResolvedArtifactCollectingVisitor(dest);
        try {
            visitArtifacts(Specs.<Dependency>satisfyAll(), visitor);
        } catch (Throwable t) {
            visitor.failures.add(t);
        }
        if (!visitor.failures.isEmpty()) {
            throw new ArtifactResolveException("artifacts", configuration.getPath(), configuration.getDisplayName(), visitor.failures);
        }
    }

    /**
     * Recursive but excludes unsuccessfully resolved artifacts.
     */
    public Set<ResolvedArtifact> getArtifacts(Spec<? super Dependency> dependencySpec) {
        ArtifactCollectingVisitor visitor = new ArtifactCollectingVisitor();
        visitArtifacts(dependencySpec, visitor);
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
    private void visitArtifacts(Spec<? super Dependency> dependencySpec, Visitor visitor) {
        //this is not very nice might be good enough until we get rid of ResolvedConfiguration and friends
        //avoid traversing the graph causing the full ResolvedDependency graph to be loaded for the most typical scenario
        if (dependencySpec == Specs.SATISFIES_ALL) {
            if (visitor.shouldVisitFiles()) {
                for (FileCollection fileCollection : fileDependencyResults.getFiles()) {
                    visitor.visitFiles(fileCollection);
                }
            }
            visitor.visitArtifacts(artifactResults.getArtifacts());
            return;
        }

        if (visitor.shouldVisitFiles()) {
            for (Map.Entry<FileCollectionDependency, FileCollection> entry: fileDependencyResults.getFirstLevelFiles().entrySet()) {
                if (dependencySpec.isSatisfiedBy(entry.getKey())) {
                    visitor.visitFiles(entry.getValue());
                }
            }
        }

        CachingDirectedGraphWalker<ResolvedDependency, ResolvedArtifact> walker = new CachingDirectedGraphWalker<ResolvedDependency, ResolvedArtifact>(new ResolvedDependencyArtifactsGraph(visitor));

        Set<ResolvedDependency> firstLevelModuleDependencies = getFirstLevelModuleDependencies(dependencySpec);

        for (ResolvedDependency resolvedDependency : firstLevelModuleDependencies) {
            visitor.visitArtifacts(resolvedDependency.getParentArtifacts(loadTransientGraphResults().getRoot()));
            walker.add(resolvedDependency);
        }
        walker.findValues();
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public Set<ResolvedDependency> getFirstLevelModuleDependencies() {
        return loadTransientGraphResults().getRoot().getChildren();
    }

    private static class Visitor {
        void visitArtifacts(Set<ResolvedArtifact> artifacts) {
        }

        boolean shouldVisitFiles() {
            return false;
        }

        void visitFiles(FileCollection fileCollection) {
            throw new UnsupportedOperationException();
        }
    }

    private static class ResolvedFilesCollectingVisitor extends Visitor {
        private final Collection<File> files;
        private final List<Throwable> failures = new ArrayList<Throwable>();
        private final Set<ResolvedArtifact> artifacts = new LinkedHashSet<ResolvedArtifact>();

        ResolvedFilesCollectingVisitor(Collection<File> files) {
            this.files = files;
        }

        @Override
        void visitArtifacts(Set<ResolvedArtifact> artifacts) {
            // Defer adding the artifacts until after all the file dependencies have been visited
            this.artifacts.addAll(artifacts);
        }

        @Override
        boolean shouldVisitFiles() {
            return true;
        }

        @Override
        void visitFiles(FileCollection fileCollection) {
            try {
                for (File file : fileCollection) {
                    files.add(file);
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

    private static class ResolvedArtifactCollectingVisitor extends Visitor {
        private final Collection<? super ResolvedArtifactResult> artifacts;
        private final Set<ComponentArtifactIdentifier> seenArtifacts = new HashSet<ComponentArtifactIdentifier>();
        private final Set<File> seenFiles = new HashSet<File>();
        private final List<Throwable> failures = new ArrayList<Throwable>();

        ResolvedArtifactCollectingVisitor(Collection<? super ResolvedArtifactResult> artifacts) {
            this.artifacts = artifacts;
        }

        @Override
        void visitArtifacts(Set<ResolvedArtifact> artifacts) {
            for (ResolvedArtifact artifact : artifacts) {
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
        }

        @Override
        boolean shouldVisitFiles() {
            return true;
        }

        @Override
        void visitFiles(FileCollection fileCollection) {
            try {
                for (File file : fileCollection) {
                    if (seenFiles.add(file)) {
                        artifacts.add(new DefaultResolvedArtifactResult(new OpaqueComponentArtifactIdentifier(file.getName()), Artifact.class, file));
                    }
                }
            } catch (Throwable t) {
                failures.add(t);
            }
        }
    }

    private static class ArtifactCollectingVisitor extends Visitor {
        final Set<ResolvedArtifact> artifacts = Sets.newLinkedHashSet();

        void visitArtifacts(Set<ResolvedArtifact> artifacts) {
            this.artifacts.addAll(artifacts);
        }
    }

    private static class FilesAndArtifactCollectingVisitor extends ArtifactCollectingVisitor {
        final Collection<File> files;

        FilesAndArtifactCollectingVisitor(Collection<File> files) {
            this.files = files;
        }

        @Override
        boolean shouldVisitFiles() {
            return true;
        }

        @Override
        void visitFiles(FileCollection fileCollection) {
            this.files.addAll(fileCollection.getFiles());
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

    private class ResolvedDependencyArtifactsGraph implements DirectedGraphWithEdgeValues<ResolvedDependency, ResolvedArtifact> {
        private final Visitor artifactsVisitor;

        ResolvedDependencyArtifactsGraph(Visitor artifactsVisitor) {
            this.artifactsVisitor = artifactsVisitor;
        }

        public void getNodeValues(ResolvedDependency node, Collection<? super ResolvedArtifact> values,
                                  Collection<? super ResolvedDependency> connectedNodes) {
            connectedNodes.addAll(node.getChildren());
            if (artifactsVisitor.shouldVisitFiles()) {
                for (FileCollection fileCollection : fileDependencyResults.getFiles(((DefaultResolvedDependency) node).getId())) {
                    artifactsVisitor.visitFiles(fileCollection);
                }
            }
        }

        public void getEdgeValues(ResolvedDependency from, ResolvedDependency to,
                                  Collection<ResolvedArtifact> values) {
            artifactsVisitor.visitArtifacts(to.getParentArtifacts(from));
        }
    }

    private static class IgnoreMissingExternalArtifacts implements Spec<ResolvedArtifact> {
        public boolean isSatisfiedBy(ResolvedArtifact element) {
            if (isExternalModuleArtifact(element)) {
                try {
                    element.getFile();
                } catch (ArtifactResolveException e) {
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
