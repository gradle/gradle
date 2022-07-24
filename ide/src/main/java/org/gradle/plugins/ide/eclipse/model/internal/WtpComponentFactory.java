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

import com.google.common.collect.Lists;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.plugins.ide.eclipse.model.EclipseWtpComponent;
import org.gradle.plugins.ide.eclipse.model.FileReference;
import org.gradle.plugins.ide.eclipse.model.WbDependentModule;
import org.gradle.plugins.ide.eclipse.model.WbModuleEntry;
import org.gradle.plugins.ide.eclipse.model.WbResource;
import org.gradle.plugins.ide.eclipse.model.WtpComponent;
import org.gradle.plugins.ide.internal.IdeArtifactRegistry;
import org.gradle.plugins.ide.internal.resolver.IdeDependencySet;
import org.gradle.plugins.ide.internal.resolver.IdeDependencyVisitor;
import org.gradle.plugins.ide.internal.resolver.UnresolvedIdeDependencyHandler;
import org.gradle.plugins.ide.internal.resolver.NullGradleApiSourcesResolver;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class WtpComponentFactory {
    private final ProjectDependencyBuilder projectDependencyBuilder;
    private final ProjectComponentIdentifier currentProjectId;

    public WtpComponentFactory(Project project, IdeArtifactRegistry artifactRegistry, ProjectStateRegistry projectRegistry) {
        projectDependencyBuilder = new ProjectDependencyBuilder(artifactRegistry);
        currentProjectId = projectRegistry.stateFor(project).getComponentIdentifier();
    }

    public void configure(final EclipseWtpComponent wtp, WtpComponent component) {
        List<WbModuleEntry> entries = Lists.newArrayList();
        entries.addAll(getEntriesFromSourceDirs(wtp));
        for (WbResource element : wtp.getResources()) {
            if (wtp.getProject().file(element.getSourcePath()).isDirectory()) {
                entries.add(element);
            }
        }
        entries.addAll(wtp.getProperties());
        Project project = wtp.getProject();
        entries.addAll(getEntriesFromConfigurations(project, configOrEmptySet(wtp.getRootConfigurations()), configOrEmptySet(wtp.getMinusConfigurations()), wtp, "/"));
        entries.addAll(getEntriesFromConfigurations(project, configOrEmptySet(wtp.getLibConfigurations()), configOrEmptySet(wtp.getMinusConfigurations()), wtp, wtp.getLibDeployPath()));
        component.configure(wtp.getDeployName(), wtp.getContextPath(), entries);
    }

    private Set<Configuration> configOrEmptySet(Set<Configuration> configuration) {
        if (configuration == null) {
            return Collections.emptySet();
        } else {
            return configuration;
        }
    }

    private List<WbResource> getEntriesFromSourceDirs(EclipseWtpComponent wtp) {
        List<WbResource> result = Lists.newArrayList();
        if (wtp.getSourceDirs() != null) {
            for (File dir : wtp.getSourceDirs()) {
                if (dir.isDirectory()) {
                    result.add(new WbResource(wtp.getClassesDeployPath(), wtp.getProject().relativePath(dir)));
                }
            }
        }
        return result;
    }

    private List<WbDependentModule> getEntriesFromConfigurations(Project project, Set<Configuration> plusConfigurations, Set<Configuration> minusConfigurations, EclipseWtpComponent wtp, String deployPath) {
        WtpDependenciesVisitor visitor = new WtpDependenciesVisitor(project, wtp, deployPath);
        new IdeDependencySet(project.getDependencies(), ((ProjectInternal) project).getServices().get(JavaModuleDetector.class),
            plusConfigurations, minusConfigurations, false, NullGradleApiSourcesResolver.INSTANCE).visit(visitor);
        return visitor.getEntries();
    }

    private class WtpDependenciesVisitor implements IdeDependencyVisitor {
        private final Project project;
        private final EclipseWtpComponent wtp;
        private final String deployPath;
        private final List<WbDependentModule> projectEntries = Lists.newArrayList();
        private final List<WbDependentModule> moduleEntries = Lists.newArrayList();
        private final List<WbDependentModule> fileEntries = Lists.newArrayList();

        private final UnresolvedIdeDependencyHandler unresolvedIdeDependencyHandler = new UnresolvedIdeDependencyHandler();

        private WtpDependenciesVisitor(Project project, EclipseWtpComponent wtp, String deployPath) {
            this.project = project;
            this.wtp = wtp;
            this.deployPath = deployPath;
        }

        @Override
        public boolean isOffline() {
            return !includeLibraries();
        }

        private boolean includeLibraries() {
            return !project.getPlugins().hasPlugin(JavaPlugin.class);
        }

        @Override
        public boolean downloadSources() {
            return false;
        }

        @Override
        public boolean downloadJavaDoc() {
            return false;
        }

        @Override
        public void visitProjectDependency(ResolvedArtifactResult artifact, boolean testDependency, boolean asJavaModule) {
            ProjectComponentIdentifier projectId = (ProjectComponentIdentifier) artifact.getId().getComponentIdentifier();
            if (!projectId.equals(currentProjectId)) {
                String targetProjectPath = projectDependencyBuilder.determineTargetProjectName(projectId);
                projectEntries.add(new WbDependentModule(deployPath, "module:/resource/" + targetProjectPath + "/" + targetProjectPath));
            }
        }

        @Override
        public void visitModuleDependency(ResolvedArtifactResult artifact, Set<ResolvedArtifactResult> sources, Set<ResolvedArtifactResult> javaDoc, boolean testDependency, boolean asJavaModule) {
            if (includeLibraries()) {
                moduleEntries.add(createWbDependentModuleEntry(artifact.getFile(), wtp.getFileReferenceFactory(), deployPath));
            }
        }

        @Override
        public void visitFileDependency(ResolvedArtifactResult artifact, boolean testDependency) {
            if (includeLibraries()) {
                fileEntries.add(createWbDependentModuleEntry(artifact.getFile(), wtp.getFileReferenceFactory(), deployPath));
            }
        }

        @Override
        public void visitGradleApiDependency(ResolvedArtifactResult artifact, File sources, boolean testDependency) {
            visitFileDependency(artifact, testDependency);
        }

        @Override
        public void visitUnresolvedDependency(UnresolvedDependencyResult unresolvedDependency) {
            unresolvedIdeDependencyHandler.log(unresolvedDependency);
        }

        /*
         * This method returns the dependencies in buckets (projects first, then modules, then files),
         * because that's what we used to do since 1.0. It would be better to return the dependencies
         * in the same order as they come from the resolver, but we'll need to change all the tests for
         * that, so defer that until later.
         */
        public List<WbDependentModule> getEntries() {
            List<WbDependentModule> entries = Lists.newArrayListWithCapacity(projectEntries.size() + moduleEntries.size() + fileEntries.size());
            entries.addAll(projectEntries);
            entries.addAll(moduleEntries);
            entries.addAll(fileEntries);
            return entries;
        }

        private WbDependentModule createWbDependentModuleEntry(File file, FileReferenceFactory fileReferenceFactory, String deployPath) {
            FileReference ref = fileReferenceFactory.fromFile(file);
            String handleSnippet = ref.isRelativeToPathVariable() ? "var/" + ref.getPath() : "lib/" + ref.getPath();
            return new WbDependentModule(deployPath, "module:/classpath/" + handleSnippet);
        }
    }

}
