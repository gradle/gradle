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
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.plugins.ide.eclipse.model.AbstractLibrary;
import org.gradle.plugins.ide.eclipse.model.ClasspathEntry;
import org.gradle.plugins.ide.eclipse.model.Container;
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath;
import org.gradle.plugins.ide.eclipse.model.FileReference;
import org.gradle.plugins.ide.eclipse.model.Library;
import org.gradle.plugins.ide.eclipse.model.Output;
import org.gradle.plugins.ide.eclipse.model.Variable;
import org.gradle.plugins.ide.internal.IdeDependenciesExtractor;
import org.gradle.plugins.ide.internal.resolver.model.IdeExtendedRepoFileDependency;
import org.gradle.plugins.ide.internal.resolver.model.IdeLocalFileDependency;
import org.gradle.plugins.ide.internal.resolver.model.IdeProjectDependency;
import org.gradle.plugins.ide.internal.resolver.model.UnresolvedIdeRepoFileDependency;
import org.gradle.util.DeprecationLogger;

import java.io.File;
import java.util.Collection;
import java.util.List;

public class ClasspathFactory {

    private interface ClasspathEntryBuilder {
        void update(List<ClasspathEntry> entries, EclipseClasspath eclipseClasspath);
    }

    private final ClasspathEntryBuilder outputCreator = new ClasspathEntryBuilder() {
        @Override
        public void update(List<ClasspathEntry> entries, EclipseClasspath eclipseClasspath) {
            entries.add(new Output(eclipseClasspath.getProject().relativePath(eclipseClasspath.getDefaultOutputDir())));
        }
    };

    private final ClasspathEntryBuilder containersCreator = new ClasspathEntryBuilder() {
        @Override
        public void update(List<ClasspathEntry> entries, EclipseClasspath eclipseClasspath) {
            for (String container : eclipseClasspath.getContainers()) {
                Container entry = new Container(container);
                entries.add(entry);
            }
        }
    };

    private final ClasspathEntryBuilder projectDependenciesCreator = new ClasspathEntryBuilder() {
        @Override
        public void update(List<ClasspathEntry> entries, EclipseClasspath eclipseClasspath) {
            Collection<IdeProjectDependency> projectDependencies = dependenciesExtractor.extractProjectDependencies(eclipseClasspath.getProject(), eclipseClasspath.getPlusConfigurations(), eclipseClasspath.getMinusConfigurations());
            for (IdeProjectDependency projectDependency : projectDependencies) {
                entries.add(new ProjectDependencyBuilder().build(projectDependency));
            }
        }
    };

    private final ClasspathEntryBuilder librariesCreator = new ClasspathEntryBuilder() {
        @Override
        public void update(List<ClasspathEntry> entries, EclipseClasspath classpath) {
            Collection<IdeExtendedRepoFileDependency> repoFileDependencies = dependenciesExtractor.extractRepoFileDependencies(
                classpath.getProject().getDependencies(), classpath.getPlusConfigurations(), classpath.getMinusConfigurations(), classpath.isDownloadSources(), classpath.isDownloadJavadoc());
            for (IdeExtendedRepoFileDependency dependency : repoFileDependencies) {
                entries.add(ClasspathFactory.createLibraryEntry(dependency.getFile(), dependency.getSourceFile(), dependency.getJavadocFile(), dependency.getDeclaredConfiguration(), classpath, dependency.getId()));
            }

            Collection<IdeLocalFileDependency> localFileDependencies = dependenciesExtractor.extractLocalFileDependencies(classpath.getPlusConfigurations(), classpath.getMinusConfigurations());
            for (IdeLocalFileDependency it : localFileDependencies) {
                entries.add(ClasspathFactory.createLibraryEntry(it.getFile(), null, null, it.getDeclaredConfiguration(), classpath, null));
            }
        }
    };

    private final SourceFoldersCreator sourceFoldersCreator = new SourceFoldersCreator();
    private final IdeDependenciesExtractor dependenciesExtractor = new IdeDependenciesExtractor();
    private final ClassFoldersCreator classFoldersCreator = new ClassFoldersCreator();

    public List<ClasspathEntry> createEntries(EclipseClasspath classpath) {
        List<ClasspathEntry> entries = Lists.newArrayList();
        outputCreator.update(entries, classpath);
        sourceFoldersCreator.populateForClasspath(entries, classpath);
        containersCreator.update(entries, classpath);

        if (classpath.isProjectDependenciesOnly()) {
            projectDependenciesCreator.update(entries, classpath);
        } else {
            projectDependenciesCreator.update(entries, classpath);
            librariesCreator.update(entries, classpath);
            entries.addAll(classFoldersCreator.create(classpath));
        }
        return entries;
    }

    public Collection<UnresolvedIdeRepoFileDependency> getUnresolvedDependencies(EclipseClasspath classpath) {
        return dependenciesExtractor.unresolvedExternalDependencies(classpath.getPlusConfigurations(), classpath.getMinusConfigurations());
    }

    private static AbstractLibrary createLibraryEntry(File binary, File source, File javadoc, final String declaredConfigurationName, EclipseClasspath classpath, ModuleVersionIdentifier id) {
        FileReferenceFactory referenceFactory = classpath.getFileReferenceFactory();

        FileReference binaryRef = referenceFactory.fromFile(binary);
        FileReference sourceRef = referenceFactory.fromFile(source);
        FileReference javadocRef = referenceFactory.fromFile(javadoc);

        final AbstractLibrary out = binaryRef.isRelativeToPathVariable() ? new Variable(binaryRef) : new Library(binaryRef);

        out.setJavadocPath(javadocRef);
        out.setSourcePath(sourceRef);
        out.setExported(false);
        DeprecationLogger.whileDisabled(new Runnable() {
            @Override
            public void run() {
                out.setDeclaredConfigurationName(declaredConfigurationName);
            }
        });
        out.setModuleVersion(id);
        return out;
    }
}
