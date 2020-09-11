/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugins.ide.eclipse.model.internal;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.plugins.ide.eclipse.internal.EclipsePluginConstants;
import org.gradle.plugins.ide.eclipse.model.AbstractClasspathEntry;
import org.gradle.plugins.ide.eclipse.model.AbstractLibrary;
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath;
import org.gradle.plugins.ide.eclipse.model.FileReference;
import org.gradle.plugins.ide.eclipse.model.Library;
import org.gradle.plugins.ide.eclipse.model.UnresolvedLibrary;
import org.gradle.plugins.ide.eclipse.model.Variable;
import org.gradle.plugins.ide.internal.IdeArtifactRegistry;
import org.gradle.plugins.ide.internal.resolver.IdeDependencySet;
import org.gradle.plugins.ide.internal.resolver.IdeDependencyVisitor;
import org.gradle.plugins.ide.internal.resolver.GradleApiSourcesResolver;
import org.gradle.plugins.ide.internal.resolver.UnresolvedIdeDependencyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class EclipseDependenciesCreator {
    private static final Logger LOGGER = LoggerFactory.getLogger(EclipseDependenciesCreator.class);
    private final EclipseClasspath classpath;
    private final ProjectDependencyBuilder projectDependencyBuilder;
    private final ProjectComponentIdentifier currentProjectId;
    private final GradleApiSourcesResolver gradleApiSourcesResolver;
    private final boolean inferModulePath;

    public EclipseDependenciesCreator(EclipseClasspath classpath, IdeArtifactRegistry ideArtifactRegistry, ProjectStateRegistry projectRegistry, GradleApiSourcesResolver gradleApiSourcesResolver, boolean inferModulePath) {
        this.classpath = classpath;
        this.projectDependencyBuilder = new ProjectDependencyBuilder(ideArtifactRegistry);
        this.currentProjectId = projectRegistry.stateFor(classpath.getProject()).getComponentIdentifier();
        this.gradleApiSourcesResolver = gradleApiSourcesResolver;
        this.inferModulePath = inferModulePath;
    }

    public List<AbstractClasspathEntry> createDependencyEntries() {
        EclipseDependenciesVisitor visitor = new EclipseDependenciesVisitor(classpath.getProject());
        new IdeDependencySet(classpath.getProject().getDependencies(), ((ProjectInternal) classpath.getProject()).getServices().get(JavaModuleDetector.class), classpath.getPlusConfigurations(), classpath.getMinusConfigurations(), inferModulePath, gradleApiSourcesResolver).visit(visitor);
        return visitor.getDependencies();
    }

    private class EclipseDependenciesVisitor implements IdeDependencyVisitor {

        private final List<AbstractClasspathEntry> projects = Lists.newArrayList();
        private final List<AbstractClasspathEntry> modules = Lists.newArrayList();
        private final List<AbstractClasspathEntry> files = Lists.newArrayList();
        private final Multimap<String, String> pathToSourceSets = collectLibraryToSourceSetMapping();
        private final UnresolvedIdeDependencyHandler unresolvedIdeDependencyHandler = new UnresolvedIdeDependencyHandler();
        private final Project project;

        public EclipseDependenciesVisitor(Project project) {
            this.project = project;
        }

        @Override
        public boolean isOffline() {
            return classpath.isProjectDependenciesOnly();
        }

        @Override
        public boolean downloadSources() {
            return classpath.isDownloadSources();
        }

        @Override
        public boolean downloadJavaDoc() {
            return classpath.isDownloadJavadoc();
        }

        @Override
        public void visitProjectDependency(ResolvedArtifactResult artifact, boolean asJavaModule) {
            ProjectComponentIdentifier componentIdentifier = (ProjectComponentIdentifier) artifact.getId().getComponentIdentifier();
            if (componentIdentifier.equals(currentProjectId)) {
                return;
            }
            LibraryElements libraryElements = artifact.getVariant().getAttributes().getAttribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE);
            if (libraryElements == null || !libraryElements.getName().equals(LibraryElements.JAR)) {
                return;
            }
            ComponentArtifactIdentifier artifactId = artifact.getId();
            TaskDependency buildDependencies = null;
            if (artifactId instanceof ComponentArtifactMetadata) {
                buildDependencies = ((ComponentArtifactMetadata) artifactId).getBuildDependencies();
            }
            // TODO: Add handling for Test-only dependencies once https://github.com/gradle/gradle/pull/9484 is merged
            projects.add(projectDependencyBuilder.build(componentIdentifier, classpath.getFileReferenceFactory().fromFile(artifact.getFile()), buildDependencies, asJavaModule));
        }

        @Override
        public void visitModuleDependency(ResolvedArtifactResult artifact, Set<ResolvedArtifactResult> sources, Set<ResolvedArtifactResult> javaDoc, boolean testDependency, boolean asJavaModule) {
            File sourceFile = sources.isEmpty() ? null : sources.iterator().next().getFile();
            File javaDocFile = javaDoc.isEmpty() ? null : javaDoc.iterator().next().getFile();
            ModuleComponentIdentifier componentIdentifier = (ModuleComponentIdentifier) artifact.getId().getComponentIdentifier();
            ModuleVersionIdentifier moduleVersionIdentifier = DefaultModuleVersionIdentifier.newId(componentIdentifier.getModuleIdentifier(), componentIdentifier.getVersion());
            modules.add(createLibraryEntry(artifact.getFile(), sourceFile, javaDocFile, classpath, moduleVersionIdentifier, pathToSourceSets, testDependency, asJavaModule));
        }

        @Override
        public void visitFileDependency(ResolvedArtifactResult artifact, boolean testDependency) {
            files.add(createLibraryEntry(artifact.getFile(), null, null, classpath, null, pathToSourceSets, testDependency, false));
        }

        @Override
        public void visitGradleApiDependency(ResolvedArtifactResult artifact, File sources, boolean testConfiguration) {
            files.add(createLibraryEntry(artifact.getFile(), sources, null, classpath, null, pathToSourceSets, testConfiguration, false));
        }

        @Override
        public void visitUnresolvedDependency(UnresolvedDependencyResult unresolvedDependency) {
            File unresolvedFile = unresolvedIdeDependencyHandler.asFile(unresolvedDependency, project.getProjectDir());
            UnresolvedLibrary unresolvedLib = (UnresolvedLibrary) createUnresolvedLibraryEntry(unresolvedFile, classpath, pathToSourceSets, false, false);
            unresolvedLib.setAttemptedSelector(unresolvedDependency.getAttempted());
            files.add(unresolvedLib);
            unresolvedIdeDependencyHandler.log(unresolvedDependency);
        }

        /*
         * This method returns the dependencies in buckets (projects first, then modules, then files),
         * because that's what we used to do since 1.0. It would be better to return the dependencies
         * in the same order as they come from the resolver, but we'll need to change all the tests for
         * that, so defer that until later.
         */
        public List<AbstractClasspathEntry> getDependencies() {
            List<AbstractClasspathEntry> dependencies = Lists.newArrayListWithCapacity(projects.size() + modules.size() + files.size());
            dependencies.addAll(projects);
            dependencies.addAll(modules);
            dependencies.addAll(files);
            return dependencies;
        }

        private Multimap<String, String> collectLibraryToSourceSetMapping() {
            Multimap<String, String> pathToSourceSetNames = LinkedHashMultimap.create();
            Iterable<SourceSet> sourceSets = classpath.getSourceSets();

            // for non-java projects there are no source sets configured
            if (sourceSets == null) {
                return pathToSourceSetNames;
            }

            for (SourceSet sourceSet : sourceSets) {
                for (File f : collectClasspathFiles(sourceSet)) {
                    pathToSourceSetNames.put(f.getAbsolutePath(), sourceSet.getName().replace(",", ""));
                }
            }
            return pathToSourceSetNames;
        }

        /*
         * SourceSet has no access to configurations where we could ask for a lenient view. This
         * means we have to deal with possible dependency resolution issues here. We catch and
         * log the exceptions here so that the Eclipse model can be generated even if there are
         * unresolvable dependencies defined in the configuration.
         *
         * We can probably do better by inspecting the runtime classpath and finding out which
         * Configurations are part of it and only traversing any extra file collections manually.
         */
        private Collection<File> collectClasspathFiles(SourceSet sourceSet) {
            ImmutableList.Builder<File> result = ImmutableList.builder();
            try {
                result.addAll(sourceSet.getRuntimeClasspath());
            } catch (Exception e) {
                LOGGER.debug("Failed to collect source sets for Eclipse dependencies", e);
            }
            return result.build();
        }

        private AbstractLibrary createLibraryEntry(File binary, File source, File javadoc, EclipseClasspath classpath, ModuleVersionIdentifier id, Multimap<String, String> pathToSourceSets, boolean testDependency, boolean asJavaModule) {
            return createLibraryEntry(binary, source, javadoc, classpath, id, pathToSourceSets, testDependency, asJavaModule, true);
        }

        private AbstractLibrary createUnresolvedLibraryEntry(File binary, EclipseClasspath classpath, Multimap<String, String> pathToSourceSets, boolean testDependency, boolean asJavaModule) {
            return createLibraryEntry(binary, null, null, classpath, null, pathToSourceSets, testDependency, asJavaModule, false);
        }

        private AbstractLibrary createLibraryEntry(File binary, File source, File javadoc, EclipseClasspath classpath, ModuleVersionIdentifier id, Multimap<String, String> pathToSourceSets, boolean testDependency, boolean asJavaModule, boolean resolved) {
            FileReferenceFactory referenceFactory = classpath.getFileReferenceFactory();

            FileReference binaryRef = referenceFactory.fromFile(binary);
            FileReference sourceRef = referenceFactory.fromFile(source);
            FileReference javadocRef = referenceFactory.fromFile(javadoc);

            final AbstractLibrary out;
            if (binaryRef.isRelativeToPathVariable()) {
                out = new Variable(binaryRef);
            } else if (resolved) {
                out = new Library(binaryRef);
            } else {
                out = new UnresolvedLibrary(binaryRef);
            }

            out.setJavadocPath(javadocRef);
            out.setSourcePath(sourceRef);
            out.setExported(false);
            out.setModuleVersion(id);

            Collection<String> sourceSets = pathToSourceSets.get(binary.getAbsolutePath());
            if (sourceSets != null) {
                out.getEntryAttributes().put(EclipsePluginConstants.GRADLE_USED_BY_SCOPE_ATTRIBUTE_NAME, Joiner.on(',').join(sourceSets));
            }

            if (testDependency) {
                out.getEntryAttributes().put(EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_KEY, EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_VALUE);
            }

            if (asJavaModule) {
                out.getEntryAttributes().put(EclipsePluginConstants.MODULE_ATTRIBUTE_KEY, EclipsePluginConstants.MODULE_ATTRIBUTE_VALUE);
            }

            return out;
        }
    }
}
