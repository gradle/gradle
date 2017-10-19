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
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.SourceSet;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.plugins.ide.eclipse.internal.EclipsePluginConstants;
import org.gradle.plugins.ide.eclipse.model.AbstractClasspathEntry;
import org.gradle.plugins.ide.eclipse.model.AbstractLibrary;
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath;
import org.gradle.plugins.ide.eclipse.model.FileReference;
import org.gradle.plugins.ide.eclipse.model.Library;
import org.gradle.plugins.ide.eclipse.model.Variable;
import org.gradle.plugins.ide.internal.IdeDependenciesExtractor;
import org.gradle.plugins.ide.internal.resolver.model.IdeExtendedRepoFileDependency;
import org.gradle.plugins.ide.internal.resolver.model.IdeLocalFileDependency;
import org.gradle.plugins.ide.internal.resolver.model.IdeProjectDependency;
import org.gradle.plugins.ide.internal.resolver.model.UnresolvedIdeRepoFileDependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class EclipseDependenciesCreator {

    private static final Logger LOGGER = LoggerFactory.getLogger(EclipseDependenciesCreator.class);

    private final IdeDependenciesExtractor dependenciesExtractor;
    private final EclipseClasspath classpath;
    private final ProjectDependencyBuilder projectDependencyBuilder;

    public EclipseDependenciesCreator(EclipseClasspath classpath) {
        this.dependenciesExtractor = new IdeDependenciesExtractor();
        this.classpath = classpath;
        ServiceRegistry serviceRegistry = ((ProjectInternal) classpath.getProject()).getServices();
        this.projectDependencyBuilder = new ProjectDependencyBuilder(serviceRegistry.get(LocalComponentRegistry.class));
    }

    public List<AbstractClasspathEntry> createDependencyEntries() {
        List<AbstractClasspathEntry> result = Lists.newArrayList();
        result.addAll(createProjectDependencies());
        if (!classpath.isProjectDependenciesOnly()) {
            result.addAll(createLibraryDependencies());
        }
        return result;
    }

    public Collection<UnresolvedIdeRepoFileDependency> unresolvedExternalDependencies() {
        return dependenciesExtractor.unresolvedExternalDependencies(classpath.getPlusConfigurations(), classpath.getMinusConfigurations());
    }

    private List<AbstractClasspathEntry> createProjectDependencies() {
        ArrayList<AbstractClasspathEntry> projects = Lists.newArrayList();

        Collection<IdeProjectDependency> projectDependencies = dependenciesExtractor.extractProjectDependencies(classpath.getProject(), classpath.getPlusConfigurations(), classpath.getMinusConfigurations());
        for (IdeProjectDependency projectDependency : projectDependencies) {
            projects.add(projectDependencyBuilder.build(projectDependency));
        }
        return projects;
    }

    private List<AbstractClasspathEntry> createLibraryDependencies() {
        ArrayList<AbstractClasspathEntry> libraries = Lists.newArrayList();
        boolean downloadSources = classpath.isDownloadSources();
        boolean downloadJavadoc = classpath.isDownloadJavadoc();

        Multimap<String, String> pathToSourceSets = collectLibraryToSourceSetMapping();

        Collection<IdeExtendedRepoFileDependency> repoFileDependencies = dependenciesExtractor.extractRepoFileDependencies(classpath.getProject().getDependencies(), classpath.getPlusConfigurations(), classpath.getMinusConfigurations(), downloadSources, downloadJavadoc);
        for (IdeExtendedRepoFileDependency dependency : repoFileDependencies) {
            libraries.add(createLibraryEntry(dependency.getFile(), dependency.getSourceFile(), dependency.getJavadocFile(), classpath, dependency.getId(), pathToSourceSets));
        }

        Collection<IdeLocalFileDependency> localFileDependencies = dependenciesExtractor.extractLocalFileDependencies(classpath.getPlusConfigurations(), classpath.getMinusConfigurations());
        for (IdeLocalFileDependency it : localFileDependencies) {
            libraries.add(createLibraryEntry(it.getFile(), null, null, classpath, null, pathToSourceSets));
        }
        return libraries;
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

    private Collection<File> collectClasspathFiles(SourceSet sourceSet) {
        // SourceSet has no access to configurations where we could ask for a lenient view. This
        // means we have to deal with possible dependency resolution issues here. We catch and
        // log the exceptions here so that the Eclipse model could be generated even if there are
        // unresolvable dependencies defined in the configuration.
        ImmutableList.Builder<File> result = ImmutableList.builder();
        try {
               result.addAll(sourceSet.getRuntimeClasspath());
        } catch (Exception e) {
            LOGGER.debug("Failed to collect source sets for Eclipse dependencies", e);
        }
        return result.build();
    }

    private static AbstractLibrary createLibraryEntry(File binary, File source, File javadoc, EclipseClasspath classpath, ModuleVersionIdentifier id, Multimap<String, String> pathToSourceSets) {
        FileReferenceFactory referenceFactory = classpath.getFileReferenceFactory();

        FileReference binaryRef = referenceFactory.fromFile(binary);
        FileReference sourceRef = referenceFactory.fromFile(source);
        FileReference javadocRef = referenceFactory.fromFile(javadoc);

        final AbstractLibrary out = binaryRef.isRelativeToPathVariable() ? new Variable(binaryRef) : new Library(binaryRef);

        out.setJavadocPath(javadocRef);
        out.setSourcePath(sourceRef);
        out.setExported(false);
        out.setModuleVersion(id);

        Collection<String> sourceSets = pathToSourceSets.get(binary.getAbsolutePath());
        if (sourceSets != null) {
            out.getEntryAttributes().put(EclipsePluginConstants.GRADLE_USED_BY_SCOPE_ATTRIBUTE_NAME, Joiner.on(',').join(sourceSets));
        }
        return out;
    }
}
