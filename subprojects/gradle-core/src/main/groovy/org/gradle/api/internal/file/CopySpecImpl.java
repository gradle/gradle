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
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.ReflectionUtil;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.FilterReader;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.util.*;

/**
 * @author Steve Appling
 */
public class CopySpecImpl implements CopySpec {
    private FileResolver resolver;
    private Set<Object> sourcePaths;
    private Object destDir;
    private final PatternSet patternSet;
    private final List<CopySpecImpl> childSpecs;
    private final CopySpecImpl parentSpec;
    private final FilterChain filterChain;
    private final DefaultCopyDestinationMapper destinationMapper = new DefaultCopyDestinationMapper();

    public CopySpecImpl(FileResolver resolver, CopySpecImpl parentSpec) {
        this.parentSpec = parentSpec;
        this.resolver = resolver;
        sourcePaths = new LinkedHashSet<Object>();
        childSpecs = new ArrayList<CopySpecImpl>();
        patternSet = new PatternSet();
        filterChain = new FilterChain();
    }

    public CopySpecImpl(FileResolver resolver) {
        this(resolver, null);
    }

    public CopySpec from(Object... sourcePaths) {
        for (Object sourcePath : sourcePaths) {
            this.sourcePaths.add(sourcePath);
        }
        return this;
    }

    public CopySpec from(Object sourcePath, Closure c) {
        CopySpec result = this;
        if (c==null) {
            from(sourcePath);
        } else {
            CopySpecImpl child = new CopySpecImpl(resolver, this);
            child.from(sourcePath);
            childSpecs.add(child);
            ConfigureUtil.configure(c, child);
            result = child;
        }
        return result;
    }

    public Set<Object> getSourcePaths() {
        return sourcePaths;
    }

    private void addSourcePaths(Collection<Object> dest) {
        if (parentSpec != null) {
            parentSpec.addSourcePaths(dest);
        }
        dest.addAll(sourcePaths);
    }

    public FileTree getSource() {
        Set<Object> allPaths = getAllSourcePaths();
        return resolver.resolveFilesAsTree(allPaths);
    }

    public Set<Object> getAllSourcePaths() {
        Set<Object> allPaths = new LinkedHashSet<Object>();
        addSourcePaths(allPaths);
        return allPaths;
    }

    public List<CopySpecImpl> getLeafSyncSpecs() {
        List<CopySpecImpl> result = new ArrayList<CopySpecImpl>();
        if (childSpecs.size() == 0) {
            result.add(this);
        } else {
            for (CopySpecImpl childSpec : childSpecs) {
                result.addAll(childSpec.getLeafSyncSpecs());
            }
        }
        return result;
    }

    public CopySpec into(Object destDir) {
        this.destDir = destDir;
        return this;
    }

    public CopySpec into(Object destPath, Closure configureClosure) {
        CopySpec result = this;
        if (configureClosure == null) {
            into(destPath);
        } else {
            CopySpecImpl child = new CopySpecImpl(resolver, this);
            child.into(destPath);
            childSpecs.add(child);
            ConfigureUtil.configure(configureClosure, child);
            result = child;
        }
        return result;
    }

    public String getDestPath() {
        if (parentSpec == null) {
            return "/";
        }
        String parentPath = parentSpec.getDestPath();
        if (destDir == null) {
            return parentPath;
        }

        String path = destDir.toString();
        if (path.startsWith("/") || path.startsWith(File.separator)) {
            return path;
        }
        if (parentPath.equals("/")) {
            return "/" + path;
        }
        return parentPath + '/' + path;
    }

    private File getRootDir() {
        if (parentSpec == null) {
            return destDir == null ? null : resolver.resolve(destDir);
        }
        return parentSpec.getRootDir();
    }

    public File getDestDir() {
        if (parentSpec == null) {
            return getRootDir();
        }
        
        File rootDir = parentSpec.getRootDir();
        if (rootDir == null) {
            return null;
        }
        String destPath = getDestPath();
        if (destPath.equals("/") || destPath.equals(File.separator)) {
            return rootDir;
        }
        return GFileUtils.canonicalise(new File(rootDir, destPath));
    }

    public CopySpec include(String ... includes) {
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

    public CopySpec exclude(String ... excludes) {
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

    public CopySpec filter(Map<String, Object> map, Class<FilterReader> filterType ) {
        try {
            Constructor<FilterReader> constructor = filterType.getConstructor(Reader.class);
            FilterReader result = constructor.newInstance(  filterChain.getLastFilter() );
            filterChain.addFilter(result);

            if (map != null) {
                ReflectionUtil.setFromMap(result, map);
            }
        } catch (Throwable th) {
            throw new InvalidUserDataException("Error - Invalid filter specification for "+filterType.getName());
        }
        return this;
    }

    public CopySpec rename(Closure closure) {
        destinationMapper.add(closure);
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
}
