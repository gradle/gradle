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
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.AbstractFileTree;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.provider.HasConfigurableValueInternal;
import org.gradle.api.internal.provider.support.LazyGroovySupport;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.provider.SupportsConvention;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;
import org.gradle.internal.state.Managed;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Set;
import java.util.function.Consumer;

public class DefaultConfigurableFileTree extends AbstractFileTree implements ConfigurableFileTreeInternal, FileTreeInternal, Managed, HasConfigurableValueInternal, LazyGroovySupport {
    private final ConfigurableFileCollectionInternal roots;
    private final PatternSet patternSet;
    private final FileTreeInternal tree;
    private final FileCollectionObservationListener listener;

    public DefaultConfigurableFileTree(
        ConfigurableFileCollectionInternal roots,
        Factory<PatternSet> patternSetFactory,
        FileCollectionObservationListener listener,
        TaskDependencyFactory taskDependencyFactory
    ) {
        super(taskDependencyFactory, patternSetFactory);
        this.roots = roots;
        this.patternSet = patternSetFactory.create();
        assert patternSet != null;
        this.tree = roots.getAsFileTree().matching(patternSet);
        this.listener = listener;
    }

    @Override
    public String getDisplayName() {
        return roots.getDisplayName();
    }

    @Override
    public Set<File> getFiles() {
        listener.fileCollectionObserved(this);
        return tree.getFiles();
    }

    @Override
    public boolean isEmpty() {
        listener.fileCollectionObserved(this);
        return tree.isEmpty();
    }

    @Override
    public boolean contains(File file) {
        listener.fileCollectionObserved(this);
        return tree.contains(file);
    }

    @Override
    public ConfigurableFileTree visit(FileVisitor visitor) {
        listener.fileCollectionObserved(this);
        tree.visit(visitor);
        return this;
    }

    @Override
    public ConfigurableFileTree visit(Action<? super FileVisitDetails> visitor) {
        listener.fileCollectionObserved(this);
        tree.visit(visitor);
        return this;
    }

    @Override
    public DefaultConfigurableFileTree from(Object... dir) {
        roots.from(dir);
        return this;
    }

    @Override
    public Set<Object> getFrom() {
        return roots.getFrom();
    }

    @Override
    public void setFrom(Iterable<?> paths) {
        roots.setFrom(paths);
    }

    @Override
    public void setFrom(Object... paths) {
        roots.setFrom(paths);
    }

    @Override
    public void setFromAnyValue(Object object) {
        roots.setFrom(object);
    }

    public File getDir() {
        // TODO Deprecate
        return roots.getSingleFile();
    }

    public void setDir(Object dir) {
        // TODO Deprecate
        roots.setFrom(dir);
    }

    @Override
    public SupportsConvention unset() {
        return roots.unset();
    }

    @Override
    public SupportsConvention unsetConvention() {
        return roots.unsetConvention();
    }

    @Override
    @Incubating
    public ConfigurableFileTree convention(Iterable<?> paths) {
        roots.convention(paths);
        return this;
    }

    @Override
    @Incubating
    public ConfigurableFileTree convention(Object... paths) {
        roots.convention(paths);
        return this;
    }

    @Override
    public PatternSet getPatternSet() {
        return patternSet;
    }

    @Override
    public FileTree matching(Closure filterConfigClosure) {
        return tree.matching(filterConfigClosure);
    }

    @Override
    public FileTree matching(Action<? super PatternFilterable> filterConfigAction) {
        return tree.matching(filterConfigAction);
    }

    @Override
    public FileTree visit(Closure visitor) {
        return tree.visit(visitor);
    }

    @Override
    public FileTree plus(FileTree fileTree) {
        // TODO Is this how we want this to be implemented?
        return tree.plus(fileTree);
    }

    @Override
    public FileTreeInternal getAsFileTree() {
        return this;
    }

    @Override
    public FileTreeInternal matching(PatternFilterable patterns) {
        return tree.matching(patterns);
    }

    @Override
    public void visitContentsAsFileTrees(Consumer<FileTreeInternal> visitor) {
        tree.visitContentsAsFileTrees(visitor);
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        roots.visitDependencies(context);
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
    public DefaultConfigurableFileTree include(Spec<FileTreeElement> includeSpec) {
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
    public DefaultConfigurableFileTree exclude(Spec<FileTreeElement> excludeSpec) {
        patternSet.exclude(excludeSpec);
        return this;
    }

    @Override
    public DefaultConfigurableFileTree exclude(Closure excludeSpec) {
        patternSet.exclude(excludeSpec);
        return this;
    }

    @Override
    public ConfigurableFileTree builtBy(Object... tasks) {
        roots.builtBy(tasks);
        return this;
    }

    @Override
    public Set<Object> getBuiltBy() {
        return roots.getBuiltBy();
    }

    @Override
    public ConfigurableFileTree setBuiltBy(Iterable<?> tasks) {
        roots.setBuiltBy(tasks);
        return this;
    }

    @Override
    public void finalizeValue() {
        roots.finalizeValue();
    }

    @Override
    public void finalizeValueOnRead() {
        roots.finalizeValueOnRead();
    }

    @Override
    public void implicitFinalizeValue() {
        roots.implicitFinalizeValue();
        // TODO Finalize pattern set?
    }

    @Override
    public void disallowChanges() {
        roots.disallowChanges();
    }

    @Override
    public void disallowUnsafeRead() {
        roots.disallowUnsafeRead();
    }

    @Nullable
    @Override
    public Object unpackState() {
        return new State(roots.getFiles(), patternSet);
    }

    @Override
    public boolean isImmutable() {
        return false;
    }

    @Override
    public Class<?> publicType() {
        return ConfigurableFileTree.class;
    }

    @Override
    public int getFactoryId() {
        return ManagedFactories.ConfigurableFileCollectionManagedFactory.FACTORY_ID;
    }

    public static class State {
        public final Set<File> roots;
        public final PatternSet patternSet;

        public State(Set<File> roots, PatternSet patternSet) {
            this.roots = roots;
            this.patternSet = patternSet;
        }
    }
}
