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
package org.gradle.api.internal.tasks.copy;

import groovy.lang.Closure
import java.io.File
import java.io.FilterReader
import java.lang.reflect.Constructor
import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.file.CopySpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.*
import java.util.*
import org.gradle.api.Transformer
import org.gradle.api.internal.file.FileResolver
import org.gradle.util.ConfigureUtil

/**
 * @author Steve Appling
 */
public class CopySpecImpl implements CopySpec {

    private static Logger logger = LoggerFactory.getLogger(CopySpecImpl.class);

    private FileResolver resolver;
    private List<File> sourceDirs;
    private File destDir;
    private List<String> includeList;
    private List<String> excludeList;
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
        sourceDirs = new ArrayList<File>();
        remapClosureList = new ArrayList<Closure>();
        childSpecs = new ArrayList<CopySpecImpl>();
        includeList = new ArrayList<String>();
        excludeList = new ArrayList<String>();
        renameMappers  = new ArrayList<Transformer<String>>();
        filterChain = new FilterChain();
    }

    CopySpec from(Object... sourcePaths) {
        from(Arrays.asList(sourcePaths))
    }

    public CopySpec from(Object sourcePath, Closure c) {
        from([sourcePath], c);
    }

    CopySpec from(Iterable<Object> sourcePaths) {
        for (Iterator it = sourcePaths.iterator(); it.hasNext(); ) {
            sourceDirs.add(resolver.resolve(it.next()))
        }
        return this
    }

    CopySpec from(Iterable<Object> sourcePaths, Closure c) {
        CopySpec result = this;
        if (c==null) {
            from(sourcePaths)
        } else {
            CopySpecImpl child = new CopySpecImpl(resolver, this);
            child.from(sourcePaths);
            childSpecs.add(child);
            ConfigureUtil.configure(c, child)
            result = child;
        }
        return result;
    }

    public List<File> getSourceDirs() {
        return sourceDirs;
    }

    public List<File> getAllSourceDirs() {
        List<File> result = new ArrayList<File>();
        if (parentSpec != null) {
            result.addAll(parentSpec.getAllSourceDirs());
        }
        result.addAll(sourceDirs);
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
        this.destDir = resolver.resolve(destDir)
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
        includeList.addAll(Arrays.asList(includes));
        return this;
    }

    public List<String> getAllIncludes() {
        List<String> result = new ArrayList<String>();
        if (parentSpec != null) {
            result.addAll(parentSpec.getAllIncludes());
        }
        result.addAll(includeList);
        return result;
    }

    public CopySpec exclude(String ... excludes) {
        excludeList.addAll(Arrays.asList(excludes));
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
        filter( null, filterType)
        return this;
    }

    public CopySpec filter(Map<String, Object> map, Class<FilterReader> filterType ) {
        try {
            Constructor<FilterReader> constructor = filterType.getConstructor(Reader.class);
            FilterReader result = constructor.newInstance(  filterChain.getChain() );
            filterChain.setChain(result);

            if (map != null) {
                map.each { key, value ->
                    result[key] = value
                }
            }
        } catch (Throwable th) {
            throw new InvalidUserDataException("Error - Invalid filter specification for "+filterType.getName());
        }
        return this;
    }

    public List<Transformer<String>> getRenameMappers() {
        return renameMappers;
    }

    public CopySpec filterChain(Closure c) {
        ConfigureUtil.configure(c, filterDelegate)
        return this;
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
        result.addAll(excludeList);
        return result;
    }

}