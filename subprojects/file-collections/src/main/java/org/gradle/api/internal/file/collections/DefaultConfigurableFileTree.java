/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.file.collections;

import groovy.lang.Closure;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.ReadOnlyFileTreeElement;
import org.gradle.api.internal.file.CompositeFileTree;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;
import org.gradle.internal.file.PathToFileResolver;

import java.io.File;
import java.util.Set;
import java.util.function.Consumer;

public class DefaultConfigurableFileTree extends CompositeFileTree implements ConfigurableFileTree {
    private Object dir;
    private final PatternSet patternSet;
    private final PathToFileResolver resolver;
    private final DefaultTaskDependency buildDependency;
    private final FileCollectionObservationListener listener;
    private final DirectoryFileTreeFactory directoryFileTreeFactory;

    public DefaultConfigurableFileTree(
        PathToFileResolver resolver,
        FileCollectionObservationListener listener,
        Factory<PatternSet> patternSetFactory,
        TaskDependencyFactory taskDependencyFactory,
        DirectoryFileTreeFactory directoryFileTreeFactory
    ) {
        super(taskDependencyFactory);
        this.resolver = resolver;
        this.listener = listener;
        this.directoryFileTreeFactory = directoryFileTreeFactory;
        patternSet = patternSetFactory.create();
        buildDependency = taskDependencyFactory.configurableDependency();
    }

    @Override
    public Set<File> getFiles() {
        listener.fileCollectionObserved(this);
        return super.getFiles();
    }

    @Override
    public boolean isEmpty() {
        listener.fileCollectionObserved(this);
        return super.isEmpty();
    }

    @Override
    public boolean contains(File file) {
        listener.fileCollectionObserved(this);
        return super.contains(file);
    }

    @Override
    public FileTree visit(FileVisitor visitor) {
        listener.fileCollectionObserved(this);
        return super.visit(visitor);
    }

    @Override
    public PatternSet getPatterns() {
        return patternSet;
    }

    @Override
    public DefaultConfigurableFileTree setDir(Object dir) {
        from(dir);
        return this;
    }

    @Override
    public File getDir() {
        if (dir == null) {
            throw new InvalidUserDataException("A base directory must be specified in the task or via a method argument!");
        }
        return resolver.resolve(dir);
    }

    @Override
    public DefaultConfigurableFileTree from(Object dir) {
        this.dir = dir;
        return this;
    }

    @Override
    public String getDisplayName() {
        return "directory '" + dir + "'";
    }

    @Override
    public Set<String> getIncludes() {
        return patternSet.getIncludes();
    }

    @Override
    public DefaultConfigurableFileTree setIncludes(Iterable<String> includes) {
        patternSet.setIncludes(includes);
        return this;
    }

    @Override
    public Set<String> getExcludes() {
        return patternSet.getExcludes();
    }

    @Override
    public DefaultConfigurableFileTree setExcludes(Iterable<String> excludes) {
        patternSet.setExcludes(excludes);
        return this;
    }

    @Override
    public DefaultConfigurableFileTree include(String... includes) {
        patternSet.include(includes);
        return this;
    }

    @Override
    public DefaultConfigurableFileTree include(Iterable<String> includes) {
        patternSet.include(includes);
        return this;
    }

    @Override
    public DefaultConfigurableFileTree include(Closure includeSpec) {
        patternSet.include(includeSpec);
        return this;
    }

    @Override
    public DefaultConfigurableFileTree include(Spec<ReadOnlyFileTreeElement> includeSpec) {
        patternSet.include(includeSpec);
        return this;
    }

    @Override
    public DefaultConfigurableFileTree exclude(String... excludes) {
        patternSet.exclude(excludes);
        return this;
    }

    @Override
    public DefaultConfigurableFileTree exclude(Iterable<String> excludes) {
        patternSet.exclude(excludes);
        return this;
    }

    @Override
    public DefaultConfigurableFileTree exclude(Spec<ReadOnlyFileTreeElement> excludeSpec) {
        patternSet.exclude(excludeSpec);
        return this;
    }

    @Override
    public DefaultConfigurableFileTree exclude(Closure excludeSpec) {
        patternSet.exclude(excludeSpec);
        return this;
    }

    @Override
    protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
        File dir = getDir();
        visitor.accept(new FileTreeAdapter(directoryFileTreeFactory.create(dir, patternSet), listener, taskDependencyFactory, patternSetFactory));
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        context.add(buildDependency);
    }

    @Override
    public ConfigurableFileTree builtBy(Object... tasks) {
        buildDependency.add(tasks);
        return this;
    }

    @Override
    public Set<Object> getBuiltBy() {
        return buildDependency.getMutableValues();
    }

    @Override
    public ConfigurableFileTree setBuiltBy(Iterable<?> tasks) {
        buildDependency.setValues(tasks);
        return this;
    }
}
