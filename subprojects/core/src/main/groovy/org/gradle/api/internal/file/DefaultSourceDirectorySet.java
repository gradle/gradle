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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.DirectoryTree;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.*;

public class DefaultSourceDirectorySet extends CompositeFileTree implements SourceDirectorySet {
    private final List<Object> source = new ArrayList<Object>();
    private final String name;
    private final String displayName;
    private final FileResolver fileResolver;
    private final PatternSet patterns = new PatternSet();
    private final PatternSet filter = new PatternSet();

    public DefaultSourceDirectorySet(String name, String displayName, FileResolver fileResolver) {
        this.name = name;
        this.displayName = displayName;
        this.fileResolver = fileResolver;
    }

    public DefaultSourceDirectorySet(String name, FileResolver fileResolver) {
        this(name, name, fileResolver);
    }

    public String getName() {
        return this.name;
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
                File srcDir = fileResolver.resolve(path);
                if (srcDir.exists() && !srcDir.isDirectory()) {
                    throw new InvalidUserDataException(String.format("Source directory '%s' is not a directory.", srcDir));
                }
                result.add(new DirectoryFileTree(srcDir, patterns));
            }
        }
        return result;
    }

    @Override
    public void resolve(FileCollectionResolveContext context) {
        for (DirectoryTree directoryTree : getSrcDirTrees()) {
            if (directoryTree.getDir().isDirectory()) {
                context.add(((DirectoryFileTree) directoryTree).filter(filter));
            }
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
}
