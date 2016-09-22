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
import org.gradle.api.file.DirectoryTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.collections.DefaultDirectoryFileTreeFactory;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.file.collections.FileCollectionAdapter;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultSourceDirectorySet extends CompositeFileTree implements SourceDirectorySet {
    private final List<Object> source = new ArrayList<Object>();
    private final String name;
    private final String displayName;
    private final FileResolver fileResolver;
    private final DirectoryFileTreeFactory directoryFileTreeFactory;
    private final PatternSet patterns;
    private final PatternSet filter;
    private final FileCollection dirs;

    public DefaultSourceDirectorySet(String name, String displayName, FileResolver fileResolver, DirectoryFileTreeFactory directoryFileTreeFactory) {
        this.name = name;
        this.displayName = displayName;
        this.fileResolver = fileResolver;
        this.directoryFileTreeFactory = directoryFileTreeFactory;
        this.patterns = fileResolver.getPatternSetFactory().create();
        this.filter = fileResolver.getPatternSetFactory().create();
        dirs = new FileCollectionAdapter(new SourceDirectories());
    }

    @Deprecated
    public DefaultSourceDirectorySet(String name, FileResolver fileResolver) {
        this(name, name, fileResolver, new DefaultDirectoryFileTreeFactory());
        DeprecationLogger.nagUserOfDeprecated("Constructor DefaultSourceDirectorySet(String, FileResolver)");
    }

    public DefaultSourceDirectorySet(String name, FileResolver fileResolver, DirectoryFileTreeFactory directoryFileTreeFactory) {
        this(name, name, fileResolver, directoryFileTreeFactory);
    }

    public String getName() {
        return this.name;
    }

    @Override
    public FileCollection getSourceDirectories() {
        return dirs;
    }

    public Set<File> getSrcDirs() {
        Set<File> dirs = new LinkedHashSet<File>();
        for (DirectoryTree tree : getSrcDirTrees()) {
            dirs.add(tree.getDir());
        }
        return dirs;
    }

    public Set<String> getIncludes() {
        return patterns.getIncludes();
    }

    public Set<String> getExcludes() {
        return patterns.getExcludes();
    }

    public PatternFilterable setIncludes(Iterable<String> includes) {
        patterns.setIncludes(includes);
        return this;
    }

    public PatternFilterable setExcludes(Iterable<String> excludes) {
        patterns.setExcludes(excludes);
        return this;
    }

    public PatternFilterable include(String... includes) {
        patterns.include(includes);
        return this;
    }

    public PatternFilterable include(Iterable<String> includes) {
        patterns.include(includes);
        return this;
    }

    public PatternFilterable include(Spec<FileTreeElement> includeSpec) {
        patterns.include(includeSpec);
        return this;
    }

    public PatternFilterable include(Closure includeSpec) {
        patterns.include(includeSpec);
        return this;
    }

    public PatternFilterable exclude(Iterable<String> excludes) {
        patterns.exclude(excludes);
        return this;
    }

    public PatternFilterable exclude(String... excludes) {
        patterns.exclude(excludes);
        return this;
    }

    public PatternFilterable exclude(Spec<FileTreeElement> excludeSpec) {
        patterns.exclude(excludeSpec);
        return this;
    }

    public PatternFilterable exclude(Closure excludeSpec) {
        patterns.exclude(excludeSpec);
        return this;
    }

    public PatternFilterable getFilter() {
        return filter;
    }

    public Set<DirectoryTree> getSrcDirTrees() {
        // This implementation is broken. It does not consider include and exclude patterns
        Map<File, DirectoryTree> trees = new LinkedHashMap<File, DirectoryTree>();
        for (DirectoryTree tree : doGetSrcDirTrees()) {
            if (!trees.containsKey(tree.getDir())) {
                trees.put(tree.getDir(), tree);
            }
        }
        return new LinkedHashSet<DirectoryTree>(trees.values());
    }

    private Set<DirectoryTree> doGetSrcDirTrees() {
        Set<DirectoryTree> result = new LinkedHashSet<DirectoryTree>();
        for (Object path : source) {
            if (path instanceof SourceDirectorySet) {
                SourceDirectorySet nested = (SourceDirectorySet) path;
                result.addAll(nested.getSrcDirTrees());
            } else {
                for (File srcDir : fileResolver.resolveFiles(path)) {
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
                context.add(fileResolver.resolveFiles(path));
            }
        }
    }

    @Override
    public void visitContents(FileCollectionResolveContext context) {
        for (DirectoryTree directoryTree : doGetSrcDirTrees()) {
            context.add(((DirectoryFileTree) directoryTree).filter(filter));
        }
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    public SourceDirectorySet srcDir(Object srcDir) {
        source.add(srcDir);
        return this;
    }

    public SourceDirectorySet srcDirs(Object... srcDirs) {
        for (Object srcDir : srcDirs) {
            source.add(srcDir);
        }
        return this;
    }

    public SourceDirectorySet setSrcDirs(Iterable<?> srcPaths) {
        source.clear();
        GUtil.addToCollection(source, srcPaths);
        return this;
    }

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
