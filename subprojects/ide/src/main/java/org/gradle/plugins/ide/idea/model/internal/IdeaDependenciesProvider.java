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

package org.gradle.plugins.ide.idea.model.internal;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.plugins.ide.idea.model.Dependency;
import org.gradle.plugins.ide.idea.model.FilePath;
import org.gradle.plugins.ide.idea.model.IdeaModule;
import org.gradle.plugins.ide.idea.model.Path;
import org.gradle.plugins.ide.idea.model.SingleEntryModuleLibrary;
import org.gradle.plugins.ide.internal.IdeArtifactRegistry;
import org.gradle.plugins.ide.internal.resolver.GradleApiSourcesResolver;
import org.gradle.plugins.ide.internal.resolver.IdeDependencySet;
import org.gradle.plugins.ide.internal.resolver.IdeDependencyVisitor;
import org.gradle.plugins.ide.internal.resolver.UnresolvedIdeDependencyHandler;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IdeaDependenciesProvider {

    public static final String SCOPE_PLUS = "plus";
    public static final String SCOPE_MINUS = "minus";
    private final ModuleDependencyBuilder moduleDependencyBuilder;
    private final IdeaDependenciesOptimizer optimizer;
    private final ProjectComponentIdentifier currentProjectId;
    private final GradleApiSourcesResolver gradleApiSourcesResolver;

    public IdeaDependenciesProvider(ProjectInternal project, IdeArtifactRegistry artifactRegistry, GradleApiSourcesResolver gradleApiSourcesResolver) {
        moduleDependencyBuilder = new ModuleDependencyBuilder(artifactRegistry);
        currentProjectId = project.getOwner().getComponentIdentifier();
        optimizer = new IdeaDependenciesOptimizer();
        this.gradleApiSourcesResolver = gradleApiSourcesResolver;
    }

    public Set<Dependency> provide(final IdeaModule ideaModule) {
        Set<Dependency> result = Sets.newLinkedHashSet();
        result.addAll(getOutputLocations(ideaModule));
        result.addAll(getDependencies(ideaModule));
        return result;
    }

    private Set<SingleEntryModuleLibrary> getOutputLocations(IdeaModule ideaModule) {
        if (ideaModule.getSingleEntryLibraries() == null) {
            return Collections.emptySet();
        }
        Set<SingleEntryModuleLibrary> outputLocations = Sets.newLinkedHashSet();
        for (Map.Entry<String, Iterable<File>> outputLocation : ideaModule.getSingleEntryLibraries().entrySet()) {
            String scope = outputLocation.getKey();
            for (File file : outputLocation.getValue()) {
                if (file != null && file.isDirectory()) {
                    outputLocations.add(new SingleEntryModuleLibrary(toPath(ideaModule, file), scope));
                }
            }
        }
        return outputLocations;
    }

    private Set<Dependency> getDependencies(IdeaModule ideaModule) {
        Set<Dependency> dependencies = Sets.newLinkedHashSet();
        Map<ComponentSelector, UnresolvedDependencyResult> unresolvedDependencies = Maps.newLinkedHashMap();
        for (GeneratedIdeaScope scope : GeneratedIdeaScope.values()) {
            IdeaDependenciesVisitor visitor = visitDependencies(ideaModule, scope);
            dependencies.addAll(visitor.getDependencies());
            unresolvedDependencies.putAll(visitor.getUnresolvedDependencies());
        }
        optimizer.optimizeDeps(dependencies);
        new UnresolvedIdeDependencyHandler().log(unresolvedDependencies.values());
        return dependencies;
    }

    private IdeaDependenciesVisitor visitDependencies(IdeaModule ideaModule, GeneratedIdeaScope scope) {
        ProjectInternal projectInternal = (ProjectInternal) ideaModule.getProject();
        final DependencyHandler handler = projectInternal.getDependencies();
        final Collection<Configuration> plusConfigurations = getPlusConfigurations(ideaModule, scope);
        final Collection<Configuration> minusConfigurations = getMinusConfigurations(ideaModule, scope);
        final JavaModuleDetector javaModuleDetector = projectInternal.getServices().get(JavaModuleDetector.class);

        final IdeaDependenciesVisitor visitor = new IdeaDependenciesVisitor(ideaModule, scope.name());
        return projectInternal.getOwner().fromMutableState(p -> {
            new IdeDependencySet(handler, javaModuleDetector, plusConfigurations, minusConfigurations, false, gradleApiSourcesResolver).visit(visitor);
            return visitor;
        });
    }

    private Collection<Configuration> getPlusConfigurations(IdeaModule ideaModule, GeneratedIdeaScope scope) {
        return getConfigurations(ideaModule, scope, SCOPE_PLUS);
    }

    private Collection<Configuration> getMinusConfigurations(IdeaModule ideaModule, GeneratedIdeaScope scope) {
        return getConfigurations(ideaModule, scope, SCOPE_MINUS);
    }

    private Collection<Configuration> getConfigurations(IdeaModule ideaModule, GeneratedIdeaScope scope, String plusMinus) {
        Map<String, Collection<Configuration>> plusMinusConfigurations = getPlusMinusConfigurations(ideaModule, scope);
        return plusMinusConfigurations.containsKey(plusMinus) ? plusMinusConfigurations.get(plusMinus) : Collections.<Configuration>emptyList();
    }

    private Map<String, Collection<Configuration>> getPlusMinusConfigurations(IdeaModule ideaModule, GeneratedIdeaScope scope) {
        Map<String, Collection<Configuration>> plusMinusConfigurations = ideaModule.getScopes().get(scope.name());
        return plusMinusConfigurations != null ? plusMinusConfigurations : Collections.<String, Collection<Configuration>>emptyMap();
    }

    private FilePath toPath(IdeaModule ideaModule, File file) {
        return file != null ? ideaModule.getPathFactory().path(file) : null;
    }

    private class IdeaDependenciesVisitor implements IdeDependencyVisitor {
        private final IdeaModule ideaModule;
        private final UnresolvedIdeDependencyHandler unresolvedIdeDependencyHandler = new UnresolvedIdeDependencyHandler();
        private final String scope;

        private final List<Dependency> projectDependencies = Lists.newLinkedList();
        private final List<Dependency> moduleDependencies = Lists.newLinkedList();
        private final List<Dependency> fileDependencies = Lists.newLinkedList();
        private final Map<ComponentSelector, UnresolvedDependencyResult> unresolvedDependencies = Maps.newLinkedHashMap();

        private IdeaDependenciesVisitor(IdeaModule ideaModule, String scope) {
            this.ideaModule = ideaModule;
            this.scope = scope;
        }

        @Override
        public boolean isOffline() {
            return ideaModule.isOffline();
        }

        @Override
        public boolean downloadSources() {
            return ideaModule.isDownloadSources();
        }

        @Override
        public boolean downloadJavaDoc() {
            return ideaModule.isDownloadJavadoc();
        }

        @Override
        public void visitProjectDependency(ResolvedArtifactResult artifact, boolean testDependency, boolean asJavaModule) {
            ProjectComponentIdentifier projectId = (ProjectComponentIdentifier) artifact.getId().getComponentIdentifier();
            if (!projectId.equals(currentProjectId)) {
                projectDependencies.add(moduleDependencyBuilder.create(projectId, scope));
            }
        }

        @Override
        public void visitModuleDependency(ResolvedArtifactResult artifact, Set<ResolvedArtifactResult> sources, Set<ResolvedArtifactResult> javaDoc, boolean testDependency, boolean asJavaModule) {
            ModuleComponentIdentifier moduleId = (ModuleComponentIdentifier) artifact.getId().getComponentIdentifier();
            SingleEntryModuleLibrary library = new SingleEntryModuleLibrary(toPath(ideaModule, artifact.getFile()), scope);
            library.setModuleVersion(DefaultModuleVersionIdentifier.newId(moduleId.getModuleIdentifier(), moduleId.getVersion()));
            Set<Path> sourcePaths = Sets.newLinkedHashSet();
            for (ResolvedArtifactResult sourceArtifact : sources) {
                sourcePaths.add(toPath(ideaModule, sourceArtifact.getFile()));
            }
            library.setSources(sourcePaths);
            Set<Path> javaDocPaths = Sets.newLinkedHashSet();
            for (ResolvedArtifactResult javaDocArtifact : javaDoc) {
                javaDocPaths.add(toPath(ideaModule, javaDocArtifact.getFile()));
            }
            library.setJavadoc(javaDocPaths);
            moduleDependencies.add(library);
        }

        @Override
        public void visitFileDependency(ResolvedArtifactResult artifact, boolean testDependency) {
            fileDependencies.add(new SingleEntryModuleLibrary(toPath(ideaModule, artifact.getFile()), scope));
        }

        @Override
        public void visitGradleApiDependency(ResolvedArtifactResult artifact, File sources, boolean testDependency) {
            fileDependencies.add(new SingleEntryModuleLibrary(toPath(ideaModule, artifact.getFile()), null, toPath(ideaModule, sources), scope));
        }

        /*
         * Remembers the unresolved dependency for later logging and also adds a fake
         * file dependency, with the file path pointing to the attempted component selector.
         * This shows up in the IDE as a red flag in the dependencies view. That's not the best
         * usability and it also muddies the API contract, because we disguise an unresolved
         * dependency as a file dependency, even though that file really doesn't exist.
         *
         * Instead, when generating files on the command line, the logged warning is enough.
         * When using the Tooling API, a dedicated "unresolved dependency" object would be better
         * and could be shown in a notification. The command line warning should probably be omitted in that case.
         */
        @Override
        public void visitUnresolvedDependency(UnresolvedDependencyResult unresolvedDependency) {
            File unresolvedFile = unresolvedIdeDependencyHandler.asFile(unresolvedDependency, ideaModule.getContentRoot());
            fileDependencies.add(new SingleEntryModuleLibrary(toPath(ideaModule, unresolvedFile), scope));
            unresolvedDependencies.put(unresolvedDependency.getAttempted(), unresolvedDependency);
        }

        /*
         * This method returns the dependencies in buckets (projects first, then modules, then files),
         * because that's what we used to do since 1.0. It would be better to return the dependencies
         * in the same order as they come from the resolver, but we'll need to change all the tests for
         * that, so defer that until later.
         */
        public Collection<Dependency> getDependencies() {
            Collection<Dependency> dependencies = Sets.newLinkedHashSet();
            dependencies.addAll(projectDependencies);
            dependencies.addAll(moduleDependencies);
            dependencies.addAll(fileDependencies);
            return dependencies;
        }

        public Map<ComponentSelector, UnresolvedDependencyResult> getUnresolvedDependencies() {
            return unresolvedDependencies;
        }
    }
}
