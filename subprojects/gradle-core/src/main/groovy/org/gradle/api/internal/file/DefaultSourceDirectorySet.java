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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.tasks.util.FileSet;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultSourceDirectorySet extends CompositeFileTree implements SourceDirectorySet {
    private final PathResolvingFileCollection srcDirs;
    private final String displayName;
    private final FileResolver resolver;
    private final PatternSet patternSet = new PatternSet();

    public DefaultSourceDirectorySet(FileResolver resolver) {
        this("source set", resolver);
    }

    public DefaultSourceDirectorySet(String displayName, FileResolver resolver) {
        this.displayName = displayName;
        this.resolver = resolver;
        srcDirs = new PathResolvingFileCollection(resolver);
    }

    public Set<File> getSrcDirs() {
        return srcDirs.getFiles();
    }

    public Set<String> getIncludes() {
        return patternSet.getIncludes();
    }

    public Set<String> getExcludes() {
        return patternSet.getExcludes();
    }

    public PatternFilterable setIncludes(Iterable<String> includes) {
        patternSet.setIncludes(includes);
        return this;
    }

    public PatternFilterable setExcludes(Iterable<String> excludes) {
        patternSet.setExcludes(excludes);
        return this;
    }

    public PatternFilterable include(String... includes) {
        patternSet.include(includes);
        return this;
    }

    public PatternFilterable include(Iterable<String> includes) {
        patternSet.include(includes);
        return this;
    }

    public PatternFilterable exclude(Iterable<String> excludes) {
        patternSet.exclude(excludes);
        return this;
    }

    public PatternFilterable exclude(String... excludes) {
        patternSet.exclude(excludes);
        return this;
    }

    @Override
    protected void addSourceCollections(Collection<FileCollection> sources) {
        for (File sourceDir : getExistingSourceDirs()) {
            FileSet fileset = new FileSet(sourceDir, resolver);
            fileset.getPatternSet().copyFrom(patternSet);
            sources.add(fileset);
        }
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    protected Set<File> getExistingSourceDirs() {
        Set<File> existingSourceDirs = new LinkedHashSet<File>();
        for (File srcDir : srcDirs) {
            if (srcDir.isDirectory()) {
                existingSourceDirs.add(srcDir);
            } else if (srcDir.exists()) {
                throw new InvalidUserDataException(String.format("Source directory '%s' is not a directory.", srcDir));
            }
        }
        return existingSourceDirs;
    }

    public SourceDirectorySet srcDir(Object srcDir) {
        srcDirs.add(srcDir);
        return this;
    }

    public SourceDirectorySet srcDirs(Object... srcDirs) {
        for (Object srcDir : srcDirs) {
            this.srcDirs.add(srcDir);
        }
        return this;
    }

    public SourceDirectorySet setSrcDirs(Iterable<Object> srcPaths) {
        srcDirs.clear();
        for (Object srcPath : srcPaths) {
            srcDirs.add(srcPath);
        }
        return this;
    }
}
