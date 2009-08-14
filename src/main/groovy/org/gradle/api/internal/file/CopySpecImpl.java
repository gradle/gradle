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
import org.gradle.api.Transformer;
import org.gradle.api.file.CopySpec;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.ReflectionUtil;

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
    private PathResolvingFileCollection sourceDirs;
    private File destDir;
    private PatternSet patternSet;
    private List<Closure> remapClosureList;
    private List<CopySpecImpl> childSpecs;
    private CopySpecImpl parentSpec;
    private List<Transformer<String>> renameMappers;
    private FilterChain filterChain;

    public CopySpecImpl(FileResolver resolver, CopySpecImpl parentSpec) {
        this(resolver);
        this.parentSpec = parentSpec;
    }

    public CopySpecImpl(FileResolver resolver) {
        this.resolver = resolver;
        sourceDirs = new PathResolvingFileCollection(resolver);
        remapClosureList = new ArrayList<Closure>();
        childSpecs = new ArrayList<CopySpecImpl>();
        patternSet = new PatternSet();
        renameMappers  = new ArrayList<Transformer<String>>();
        filterChain = new FilterChain();
    }

    public CopySpec from(Object... sourcePaths) {
        for (Object sourcePath : sourcePaths) {
            sourceDirs.add(sourcePath);
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

    public Set<File> getSourceDirs() {
        return sourceDirs.getFiles();
    }

    public Set<File> getAllSourceDirs() {
        Set<File> result = new LinkedHashSet<File>();
        if (parentSpec != null) {
            result.addAll(parentSpec.getAllSourceDirs());
        }
        result.addAll(getSourceDirs());
        return result;
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
        this.destDir = resolver.resolve(destDir);
        return this;
    }

    public File getDestDir() {
        File result = destDir;
        if (result == null && parentSpec != null) {
            result = parentSpec.getDestDir();
        }
        return result;
    }

    public CopySpec include(String ... includes) {
        patternSet.include(includes);
        return this;
    }

    public CopySpec include(Iterable<String> includes) {
        patternSet.include(includes);
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

    public Set<String> getExcludes() {
        return patternSet.getExcludes();
    }

    public CopySpecImpl setExcludes(Iterable<String> excludes) {
        patternSet.setExcludes(excludes);
        return this;
    }

    public CopySpec rename(String sourceRegEx, String replaceWith) {
        renameMappers.add(new RegExpNameMapper(sourceRegEx,  replaceWith));
        return this;
    }

    public FilterChain getFilterChain() {
        return filterChain;
    }

    public CopySpec filter(Class<FilterReader> filterType) {
        filter(null, filterType);
        return this;
    }

    public CopySpec filter(Map<String, Object> map, Class<FilterReader> filterType ) {
        try {
            Constructor<FilterReader> constructor = filterType.getConstructor(Reader.class);
            FilterReader result = constructor.newInstance(  filterChain.getChain() );
            filterChain.setChain(result);

            if (map != null) {
                ReflectionUtil.setFromMap(result, map);
            }
        } catch (Throwable th) {
            throw new InvalidUserDataException("Error - Invalid filter specification for "+filterType.getName());
        }
        return this;
    }

    public List<Transformer<String>> getRenameMappers() {
        return renameMappers;
    }

    public CopySpec remapTarget(Closure closure) {
        remapClosureList.add(closure);
        return this;
    }

    public List<Closure> getRemapClosures() {
        return remapClosureList;
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
