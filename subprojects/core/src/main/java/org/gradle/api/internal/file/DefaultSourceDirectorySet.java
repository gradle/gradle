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
package org.gradle.api.internal.file;

import groovy.lang.Closure;
import org.gradle.api.Buildable;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.DirectoryTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.file.collections.FileCollectionAdapter;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public class DefaultSourceDirectorySet extends CompositeFileTree implements SourceDirectorySet {
    private final List<Object> source = new ArrayList<Object>();
    private final String name;
    private final String displayName;
    private final FileCollectionFactory fileCollectionFactory;
    private final DirectoryFileTreeFactory directoryFileTreeFactory;
    private final PatternSet patterns;
    private final PatternSet filter;
    private final FileCollection dirs;
    private final DirectoryProperty destinationDirectory; // the user configurable output directory
    private final DirectoryProperty classesDirectory;     // bound to the compile task output

    private TaskProvider<?> compileTaskProvider;

    public DefaultSourceDirectorySet(String name, String displayName, Factory<PatternSet> patternSetFactory, FileCollectionFactory fileCollectionFactory, DirectoryFileTreeFactory directoryFileTreeFactory, ObjectFactory objectFactory) {
        this.name = name;
        this.displayName = displayName;
        this.fileCollectionFactory = fileCollectionFactory;
        this.directoryFileTreeFactory = directoryFileTreeFactory;
        this.patterns = patternSetFactory.create();
        this.filter = patternSetFactory.create();
        this.dirs = new FileCollectionAdapter(new SourceDirectories());
        this.destinationDirectory = objectFactory.directoryProperty();
        this.classesDirectory = objectFactory.directoryProperty();
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public FileCollection getSourceDirectories() {
        return dirs;
    }

    @Override
    public Set<File> getSrcDirs() {
        Set<File> dirs = new LinkedHashSet<File>();
        for (DirectoryTree tree : getSrcDirTrees()) {
            dirs.add(tree.getDir());
        }
        return dirs;
    }

    @Override
    public Set<String> getIncludes() {
        return patterns.getIncludes();
    }

    @Override
    public Set<String> getExcludes() {
        return patterns.getExcludes();
    }

    @Override
    public PatternFilterable setIncludes(Iterable<String> includes) {
        patterns.setIncludes(includes);
        return this;
    }

    @Override
    public PatternFilterable setExcludes(Iterable<String> excludes) {
        patterns.setExcludes(excludes);
        return this;
    }

    @Override
    public PatternFilterable include(String... includes) {
        patterns.include(includes);
        return this;
    }

    @Override
    public PatternFilterable include(Iterable<String> includes) {
        patterns.include(includes);
        return this;
    }

    @Override
    public PatternFilterable include(Spec<FileTreeElement> includeSpec) {
        patterns.include(includeSpec);
        return this;
    }

    @Override
    public PatternFilterable include(Closure includeSpec) {
        patterns.include(includeSpec);
        return this;
    }

    @Override
    public PatternFilterable exclude(Iterable<String> excludes) {
        patterns.exclude(excludes);
        return this;
    }

    @Override
    public PatternFilterable exclude(String... excludes) {
        patterns.exclude(excludes);
        return this;
    }

    @Override
    public PatternFilterable exclude(Spec<FileTreeElement> excludeSpec) {
        patterns.exclude(excludeSpec);
        return this;
    }

    @Override
    public PatternFilterable exclude(Closure excludeSpec) {
        patterns.exclude(excludeSpec);
        return this;
    }

    @Override
    public PatternFilterable getFilter() {
        return filter;
    }

    @Override
    public File getOutputDir() {
        return destinationDirectory.getAsFile().get();
    }

    @Override
    public void setOutputDir(Provider<File> provider) {
        destinationDirectory.set(classesDirectory.fileProvider(provider));
    }

    @Override
    public void setOutputDir(File outputDir) {
        destinationDirectory.set(outputDir);
    }

    @Override
    public DirectoryProperty getDestinationDirectory() {
        return destinationDirectory;
    }

    @Override
    public Provider<Directory> getClassesDirectory() {
        return classesDirectory;
    }

    @Override
    public <T extends Task> void compiledBy(TaskProvider<T> taskProvider, Function<T, DirectoryProperty> mapping) {
        this.compileTaskProvider = taskProvider;
        taskProvider.configure(task -> {
            if (taskProvider == this.compileTaskProvider) {
                mapping.apply(task).set(destinationDirectory);
            }
        });
        classesDirectory.set(taskProvider.flatMap(mapping::apply));
    }

    @Override
    public Set<DirectoryTree> getSrcDirTrees() {
        // This implementation is broken. It does not consider include and exclude patterns
        Map<File, DirectoryTree> trees = new LinkedHashMap<File, DirectoryTree>();
        for (DirectoryTree tree : getSourceTrees()) {
            if (!trees.containsKey(tree.getDir())) {
                trees.put(tree.getDir(), tree);
            }
        }
        return new LinkedHashSet<>(trees.values());
    }

    protected Set<DirectoryFileTree> getSourceTrees() {
        Set<DirectoryFileTree> result = new LinkedHashSet<>();
        for (Object path : source) {
            if (path instanceof DefaultSourceDirectorySet) {
                DefaultSourceDirectorySet nested = (DefaultSourceDirectorySet) path;
                result.addAll(nested.getSourceTrees());
            } else {
                for (File srcDir : fileCollectionFactory.resolving(path)) {
                    if (srcDir.exists() && !srcDir.isDirectory()) {
                        throw new InvalidUserDataException(String.format("Source directory '%s' is not a directory.", srcDir));
                    }
                    result.add(directoryFileTreeFactory.create(srcDir, patterns));
                }
            }
        }
        return result;
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        for (Object path : source) {
            if (path instanceof SourceDirectorySet) {
                context.add(((SourceDirectorySet) path).getBuildDependencies());
            } else {
                context.add(fileCollectionFactory.resolving(path));
            }
        }
    }

    @Override
    protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
        for (DirectoryFileTree directoryTree : getSourceTrees()) {
            visitor.accept(fileCollectionFactory.treeOf(directoryTree.filter(filter)));
        }
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public SourceDirectorySet srcDir(Object srcDir) {
        source.add(srcDir);
        return this;
    }

    @Override
    public SourceDirectorySet srcDirs(Object... srcDirs) {
        for (Object srcDir : srcDirs) {
            source.add(srcDir);
        }
        return this;
    }

    @Override
    public SourceDirectorySet setSrcDirs(Iterable<?> srcPaths) {
        source.clear();
        GUtil.addToCollection(source, srcPaths);
        return this;
    }

    @Override
    public SourceDirectorySet source(SourceDirectorySet source) {
        this.source.add(source);
        return this;
    }

    private class SourceDirectories implements MinimalFileSet, Buildable {
        @Override
        public TaskDependency getBuildDependencies() {
            return DefaultSourceDirectorySet.this.getBuildDependencies();
        }

        @Override
        public Set<File> getFiles() {
            return getSrcDirs();
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }
    }
}
