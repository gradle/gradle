/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.file;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.DirectoryTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.model.ReplacedBy;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.util.PatternFilterable;

import java.io.File;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;

public class DelegatingSourceDirectorySet implements SourceDirectorySet {

    private final SourceDirectorySet delegate;

    public DelegatingSourceDirectorySet(SourceDirectorySet delegate) {
        this.delegate = delegate;
    }

    public SourceDirectorySet getDelegate() {
        return delegate;
    }

    @Override
    public String getDisplayName() {
        return delegate.getDisplayName();
    }

    @Override
    public File getSingleFile() throws IllegalStateException {
        return delegate.getSingleFile();
    }

    @Override
    public boolean contains(File file) {
        return delegate.contains(file);
    }

    @Override
    public String getAsPath() {
        return delegate.getAsPath();
    }

    @Override
    public FileCollection plus(FileCollection collection) {
        return delegate.plus(collection);
    }

    @Override
    public FileCollection minus(FileCollection collection) {
        return delegate.minus(collection);
    }

    @Override
    public FileCollection filter(Closure filterClosure) {
        return delegate.filter(filterClosure);
    }

    @Override
    public FileCollection filter(Spec<? super File> filterSpec) {
        return delegate.filter(filterSpec);
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public Provider<Set<FileSystemLocation>> getElements() {
        return delegate.getElements();
    }

    @Override
    public void addToAntBuilder(Object builder, String nodeName, AntType type) {
        delegate.addToAntBuilder(builder, nodeName, type);
    }

    @Override
    public Object addToAntBuilder(Object builder, String nodeName) {
        return delegate.addToAntBuilder(builder, nodeName);
    }

    @Override
    public FileTree matching(Closure filterConfigClosure) {
        return delegate.matching(filterConfigClosure);
    }

    @Override
    public FileTree matching(Action<? super PatternFilterable> filterConfigAction) {
        return delegate.matching(filterConfigAction);
    }

    @Override
    public FileTree matching(PatternFilterable patterns) {
        return delegate.matching(patterns);
    }

    @Override
    public FileTree visit(FileVisitor visitor) {
        return delegate.visit(visitor);
    }

    @Override
    public FileTree visit(Closure visitor) {
        return delegate.visit(visitor);
    }

    @Override
    public FileTree visit(Action<? super FileVisitDetails> visitor) {
        return delegate.visit(visitor);
    }

    @Override
    public FileTree plus(FileTree fileTree) {
        return delegate.plus(fileTree);
    }

    @Override
    public FileTree getAsFileTree() {
        return delegate.getAsFileTree();
    }

    @Override
    public Set<File> getFiles() {
        return delegate.getFiles();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public SourceDirectorySet srcDir(Object srcPath) {
        return delegate.srcDir(srcPath);
    }

    @Override
    public SourceDirectorySet srcDirs(Object... srcPaths) {
        return delegate.srcDirs(srcPaths);
    }

    @Override
    public Set<File> getSrcDirs() {
        return delegate.getSrcDirs();
    }

    @Override
    public SourceDirectorySet setSrcDirs(Iterable<?> srcPaths) {
        return delegate.setSrcDirs(srcPaths);
    }

    @Override
    public SourceDirectorySet source(SourceDirectorySet source) {
        return delegate.source(source);
    }

    @Override
    public FileCollection getSourceDirectories() {
        return delegate.getSourceDirectories();
    }

    @Override
    public Set<DirectoryTree> getSrcDirTrees() {
        return delegate.getSrcDirTrees();
    }

    @Override
    public PatternFilterable getFilter() {
        return delegate.getFilter();
    }

    @Override
    public DirectoryProperty getDestinationDirectory() {
        return delegate.getDestinationDirectory();
    }

    @Override
    public Provider<Directory> getClassesDirectory() {
        return delegate.getClassesDirectory();
    }

    @Override
    public <T extends Task> void compiledBy(TaskProvider<T> taskProvider, Function<T, DirectoryProperty> mapping) {
        delegate.compiledBy(taskProvider, mapping);
    }

    @Override
    @Deprecated
    @ReplacedBy("classesDirectory")
    public File getOutputDir() {
        return delegate.getOutputDir();
    }

    @Override
    @Deprecated
    public void setOutputDir(Provider<File> provider) {
        delegate.setOutputDir(provider);
    }

    @Override
    @Deprecated
    public void setOutputDir(File outputDir) {
        delegate.setOutputDir(outputDir);
    }

    @Override
    public Iterator<File> iterator() {
        return delegate.iterator();
    }

    @Override
    public void forEach(Consumer<? super File> action) {
        delegate.forEach(action);
    }

    @Override
    public Spliterator<File> spliterator() {
        return delegate.spliterator();
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return delegate.getBuildDependencies();
    }

    @Override
    public Set<String> getIncludes() {
        return delegate.getIncludes();
    }

    @Override
    public Set<String> getExcludes() {
        return delegate.getExcludes();
    }

    @Override
    public PatternFilterable setIncludes(Iterable<String> includes) {
        return delegate.setIncludes(includes);
    }

    @Override
    public PatternFilterable setExcludes(Iterable<String> excludes) {
        return delegate.setExcludes(excludes);
    }

    @Override
    public PatternFilterable include(String... includes) {
        return delegate.include(includes);
    }

    @Override
    public PatternFilterable include(Iterable<String> includes) {
        return delegate.include(includes);
    }

    @Override
    public PatternFilterable include(Spec<FileTreeElement> includeSpec) {
        return delegate.include(includeSpec);
    }

    @Override
    public PatternFilterable include(Closure includeSpec) {
        return delegate.include(includeSpec);
    }

    @Override
    public PatternFilterable exclude(String... excludes) {
        return delegate.exclude(excludes);
    }

    @Override
    public PatternFilterable exclude(Iterable<String> excludes) {
        return delegate.exclude(excludes);
    }

    @Override
    public PatternFilterable exclude(Spec<FileTreeElement> excludeSpec) {
        return delegate.exclude(excludeSpec);
    }

    @Override
    public PatternFilterable exclude(Closure excludeSpec) {
        return delegate.exclude(excludeSpec);
    }
}
