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
import org.gradle.api.specs.Spec;
import org.gradle.api.file.*;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.ReflectionUtil;
import org.apache.tools.zip.UnixStat;

import java.io.File;
import java.io.FilterReader;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.util.*;

/**
 * @author Steve Appling
 */
public class CopySpecImpl implements CopySpec {
    private final FileResolver resolver;
    private final boolean root;
    private final Set<Object> sourcePaths;
    private Object destDir;
    private final PatternSet patternSet;
    private final List<CopySpecImpl> childSpecs;
    private final CopySpecImpl parentSpec;
    private final FilterChain filterChain;
    private final DefaultCopyDestinationMapper destinationMapper = new DefaultCopyDestinationMapper();
    private Integer dirMode;
    private Integer fileMode;

    private CopySpecImpl(FileResolver resolver, CopySpecImpl parentSpec, boolean root) {
        this.parentSpec = parentSpec;
        this.resolver = resolver;
        this.root = root;
        sourcePaths = new LinkedHashSet<Object>();
        childSpecs = new ArrayList<CopySpecImpl>();
        patternSet = new PatternSet();
        filterChain = new FilterChain();
    }

    public CopySpecImpl(FileResolver resolver) {
        this(resolver, null, true);
    }

    protected FileResolver getResolver() {
        return resolver;
    }

    public CopySpec from(Object... sourcePaths) {
        for (Object sourcePath : sourcePaths) {
            this.sourcePaths.add(sourcePath);
        }
        return this;
    }

    public CopySpec from(Object sourcePath, Closure c) {
        if (c == null) {
            from(sourcePath);
            return this;
        } else {
            CopySpecImpl child = addChild();
            child.from(sourcePath);
            ConfigureUtil.configure(c, child);
            return child;
        }
    }

    private CopySpecImpl addChild() {
        CopySpecImpl child = new CopySpecImpl(resolver, this, false);
        childSpecs.add(child);
        return child;
    }

    public CopySpecImpl addNoInheritChild() {
        CopySpecImpl child = new CopySpecImpl(resolver, null, false);
        childSpecs.add(child);
        return child;
    }

    public Set<Object> getSourcePaths() {
        return sourcePaths;
    }

    public FileTree getSource() {
        return resolver.resolveFilesAsTree(sourcePaths);
    }

    public List<CopySpecImpl> getAllSpecs() {
        List<CopySpecImpl> result = new ArrayList<CopySpecImpl>();
        result.add(this);
        for (CopySpecImpl childSpec : childSpecs) {
            result.addAll(childSpec.getAllSpecs());
        }
        return result;
    }

    public CopySpec into(Object destDir) {
        this.destDir = destDir;
        return this;
    }

    public CopySpec into(Object destPath, Closure configureClosure) {
        if (configureClosure == null) {
            into(destPath);
            return this;
        } else {
            CopySpecImpl child = addChild();
            child.into(destPath);
            ConfigureUtil.configure(configureClosure, child);
            return child;
        }
    }

    public RelativePath getDestPath() {
        if (root) {
            return new RelativePath(false);
        }
        RelativePath parentPath;
        if (parentSpec == null) {
            parentPath = new RelativePath(false);
        } else {
            parentPath = parentSpec.getDestPath();
        }
        if (destDir == null) {
            return parentPath;
        }

        String path = destDir.toString();
        if (path.startsWith("/") || path.startsWith(File.separator)) {
            return RelativePath.parse(false, path);
        }

        return RelativePath.parse(false, parentPath, path);
    }

    public File getDestDir() {
        if (parentSpec == null) {
            return destDir == null ? null : resolver.resolve(destDir);
        }
        return parentSpec.getDestDir();
    }

    public CopySpec include(String... includes) {
        patternSet.include(includes);
        return this;
    }

    public CopySpec include(Iterable<String> includes) {
        patternSet.include(includes);
        return this;
    }

    public CopySpec include(Spec<FileTreeElement> includeSpec) {
        patternSet.include(includeSpec);
        return this;
    }

    public CopySpec include(Closure includeSpec) {
        patternSet.include(includeSpec);
        return this;
    }

    public Set<String> getIncludes() {
        return patternSet.getIncludes();
    }

    public CopySpec setIncludes(Iterable<String> includes) {
        patternSet.setIncludes(includes);
        return this;
    }

    public List<String> getAllIncludes() {
        List<String> result = new ArrayList<String>();
        if (parentSpec != null) {
            result.addAll(parentSpec.getAllIncludes());
        }
        result.addAll(getIncludes());
        return result;
    }

    public List<Spec<FileTreeElement>> getAllIncludeSpecs() {
        List<Spec<FileTreeElement>> result = new ArrayList<Spec<FileTreeElement>>();
        if (parentSpec != null) {
            result.addAll(parentSpec.getAllIncludeSpecs());
        }
        result.addAll(patternSet.getIncludeSpecs());
        return result;
    }

    public CopySpec exclude(String... excludes) {
        patternSet.exclude(excludes);
        return this;
    }

    public CopySpec exclude(Iterable<String> excludes) {
        patternSet.exclude(excludes);
        return this;
    }

    public CopySpec exclude(Spec<FileTreeElement> excludeSpec) {
        patternSet.exclude(excludeSpec);
        return this;
    }

    public CopySpec exclude(Closure excludeSpec) {
        patternSet.exclude(excludeSpec);
        return this;
    }

    public Set<String> getExcludes() {
        return patternSet.getExcludes();
    }

    public CopySpecImpl setExcludes(Iterable<String> excludes) {
        patternSet.setExcludes(excludes);
        return this;
    }

    public CopySpec rename(String sourceRegEx, String replaceWith) {
        destinationMapper.add(new RegExpNameMapper(sourceRegEx, replaceWith));
        return this;
    }

    public FilterChain getFilterChain() {
        if (parentSpec != null) {
            filterChain.setInputSource(parentSpec.getFilterChain());
        }
        return filterChain;
    }

    public CopySpec filter(Class<FilterReader> filterType) {
        filter(null, filterType);
        return this;
    }

    public CopySpec filter(Closure closure) {
        LineFilter newFilter = new LineFilter(filterChain.getLastFilter(), closure);
        filterChain.addFilter(newFilter);
        return this;
    }

    public CopySpec filter(Map<String, Object> map, Class<FilterReader> filterType) {
        try {
            Constructor<FilterReader> constructor = filterType.getConstructor(Reader.class);
            FilterReader result = constructor.newInstance(filterChain.getLastFilter());
            filterChain.addFilter(result);

            if (map != null) {
                ReflectionUtil.setFromMap(result, map);
            }
        } catch (Throwable th) {
            throw new InvalidUserDataException("Error - Invalid filter specification for " + filterType.getName());
        }
        return this;
    }

    public CopySpec rename(Closure closure) {
        destinationMapper.add(closure);
        return this;
    }

    public int getDirMode() {
        if (dirMode != null) {
            return dirMode;
        }
        if (parentSpec != null) {
            return parentSpec.getDirMode();
        }
        return UnixStat.DEFAULT_DIR_PERM;
    }

    public int getFileMode() {
        if (fileMode != null) {
            return fileMode;
        }
        if (parentSpec != null) {
            return parentSpec.getFileMode();
        }
        return UnixStat.DEFAULT_FILE_PERM;
    }

    public CopyProcessingSpec setDirMode(int mode) {
        dirMode = mode;
        return this;
    }

    public CopyProcessingSpec setFileMode(int mode) {
        fileMode = mode;
        return this;
    }

    public CopyDestinationMapper getDestinationMapper() {
        return destinationMapper;
    }

    public List<String> getAllExcludes() {
        List<String> result = new ArrayList<String>();
        if (parentSpec != null) {
            result.addAll(parentSpec.getAllExcludes());
        }
        result.addAll(getExcludes());
        return result;
    }

    public List<Spec<FileTreeElement>> getAllExcludeSpecs() {
        List<Spec<FileTreeElement>> result = new ArrayList<Spec<FileTreeElement>>();
        if (parentSpec != null) {
            result.addAll(parentSpec.getAllExcludeSpecs());
        }
        result.addAll(patternSet.getExcludeSpecs());
        return result;
    }

    public boolean hasSource() {
        if (!sourcePaths.isEmpty()) {
            return true;
        }
        for (CopySpecImpl spec : childSpecs) {
            if (spec.hasSource()) {
                return true;
            }
        }
        return false;
    }
}
