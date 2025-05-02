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

import com.google.common.collect.ImmutableList;
import org.gradle.plugins.ide.eclipse.model.AbstractClasspathEntry;
import org.gradle.plugins.ide.eclipse.model.ClasspathEntry;
import org.gradle.plugins.ide.eclipse.model.Container;
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath;
import org.gradle.plugins.ide.eclipse.model.Output;
import org.gradle.plugins.ide.eclipse.model.SourceFolder;
import org.gradle.plugins.ide.internal.IdeArtifactRegistry;
import org.gradle.plugins.ide.internal.resolver.GradleApiSourcesResolver;

import java.util.Collections;
import java.util.List;

import static com.google.common.collect.ImmutableList.toImmutableList;

public class ClasspathFactory {

    private final EclipseClasspath classpath;
    private final EclipseDependenciesCreator dependenciesCreator;

    public ClasspathFactory(EclipseClasspath classpath, IdeArtifactRegistry ideArtifactRegistry, GradleApiSourcesResolver gradleApiSourcesResolver, boolean inferModulePath) {
        this.classpath = classpath;
        this.dependenciesCreator = new EclipseDependenciesCreator(classpath, ideArtifactRegistry, gradleApiSourcesResolver, inferModulePath);
    }

    public List<ClasspathEntry> createEntries() {
        ImmutableList.Builder<ClasspathEntry> entries = ImmutableList.builder();
        entries.add(createOutput());
        entries.addAll(createSourceFolders());
        entries.addAll(createContainers());
        entries.addAll(createDependencies());
        entries.addAll(createClassFolders());
        return entries.build();
    }

    private ClasspathEntry createOutput() {
        return new Output(classpath.getProject().relativePath(classpath.getDefaultOutputDir()));
    }

    private List<SourceFolder> createSourceFolders() {
        return new SourceFoldersCreator().createSourceFolders(classpath);
    }

    private List<ClasspathEntry> createContainers() {
        return classpath.getContainers().stream()
            .map(Container::new)
            .collect(toImmutableList());
    }

    private List<AbstractClasspathEntry> createDependencies() {
        return dependenciesCreator.createDependencyEntries();
    }

    private List<? extends ClasspathEntry> createClassFolders() {
        return classpath.isProjectDependenciesOnly() ? Collections.<ClasspathEntry>emptyList() : new ClassFoldersCreator().create(classpath);
    }
}
