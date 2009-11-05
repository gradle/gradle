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
package org.gradle.api.tasks;

import groovy.lang.Closure;
import org.gradle.api.file.*;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.file.CopyActionImpl;
import org.gradle.api.internal.file.CopySpecImpl;
import org.gradle.api.specs.Spec;

import java.io.FilterReader;
import java.util.Map;
import java.util.Set;

/**
 * {@code AbstractCopyTask} is the base class for all copy tasks.
 */
public abstract class AbstractCopyTask extends ConventionTask implements CopyAction {

    @TaskAction
    void copy() {
        configureRootSpec();
        getCopyAction().execute();
        setDidWork(getCopyAction().getDidWork());
    }

    protected void configureRootSpec() {
        if (!getCopyAction().hasSource()) {
            Object srcDirs = getDefaultSource();
            if (srcDirs != null) {
                from(srcDirs);
            }
        }
    }
    
    public FileCollection getDefaultSource() {
        return null;
    }

    @InputFiles @SkipWhenEmpty @Optional
    public FileCollection getSource() {
        return getCopyAction().hasSource() ? getCopyAction().getAllSource() : getDefaultSource();
    }
    
    protected abstract CopyActionImpl getCopyAction();

    // -------------------------------------------------
    // --- Delegate CopyAction methods to copyAction ---
    // -------------------------------------------------

    /**
     * {@inheritDoc}
     */
    public void setCaseSensitive(boolean caseSensitive) {
        getCopyAction().setCaseSensitive(caseSensitive);
    }

    protected CopySpecImpl getRootSpec() {
        return getCopyAction();
    }

    // -------------------------------------------------
    // ---- Delegate CopySpec methods to copyAction ----
    // -------------------------------------------------

    /**
     * {@inheritDoc}
     */
    public CopySpec from(Object... sourcePaths) {
        return getRootSpec().from(sourcePaths);
    }

    /**
     * {@inheritDoc}
     */
    public CopySpec from(Object sourcePath, Closure c) {
        return getRootSpec().from(sourcePath, c);
    }

    /**
     * {@inheritDoc}
     */
    public CopySpec into(Object destDir) {
        return getRootSpec().into(destDir);
    }

    /**
     * {@inheritDoc}
     */
    public CopySpec into(Object destPath, Closure configureClosure) {
        return getRootSpec().into(destPath, configureClosure);
    }

    /**
     * {@inheritDoc}
     */
    public CopySpec include(String... includes) {
        return getRootSpec().include(includes);
    }

    /**
     * {@inheritDoc}
     */
    public CopySpec include(Iterable<String> includes) {
        return getRootSpec().include(includes);
    }

    /**
     * {@inheritDoc}
     */
    public CopySpec include(Spec<FileTreeElement> includeSpec) {
        return getRootSpec().include(includeSpec);
    }

    /**
     * {@inheritDoc}
     */
    public CopySpec include(Closure includeSpec) {
        return getRootSpec().include(includeSpec);
    }

    /**
     * {@inheritDoc}
     */
    public CopySpec exclude(String... excludes) {
        return getRootSpec().exclude(excludes);
    }

    /**
     * {@inheritDoc}
     */
    public CopySpec exclude(Iterable<String> excludes) {
        return getRootSpec().exclude(excludes);
    }

    /**
     * {@inheritDoc}
     */
    public CopySpec exclude(Spec<FileTreeElement> excludeSpec) {
        return getRootSpec().exclude(excludeSpec);
    }

    /**
     * {@inheritDoc}
     */
    public CopySpec exclude(Closure excludeSpec) {
        return getRootSpec().exclude(excludeSpec);
    }

    /**
     * {@inheritDoc}
     */
    public CopySpec setIncludes(Iterable<String> includes) {
        return getRootSpec().setIncludes(includes);
    }

    /**
     * {@inheritDoc}
     */
    public Set<String> getIncludes() {
        return getRootSpec().getIncludes();
    }

    /**
     * {@inheritDoc}
     */
    public CopySpec setExcludes(Iterable<String> excludes) {
        return getRootSpec().setExcludes(excludes);
    }

    /**
     * {@inheritDoc}
     */
    public Set<String> getExcludes() {
        return getRootSpec().getExcludes();
    }

    /**
     * {@inheritDoc}
     */
    public CopySpec rename(Closure closure) {
        return getRootSpec().rename(closure);
    }

    /**
     * {@inheritDoc}
     */
    public CopySpec rename(String sourceRegEx, String replaceWith) {
        return getRootSpec().rename(sourceRegEx, replaceWith);
    }

    /**
     * {@inheritDoc}
     */
    public CopySpec filter(Map<String, Object> map, Class<FilterReader> filterType) {
        return getRootSpec().filter(map, filterType);
    }

    /**
     * {@inheritDoc}
     */
    public CopySpec filter(Class<FilterReader> filterType) {
        return getRootSpec().filter(filterType);
    }

    /**
     * {@inheritDoc}
     */
    public CopySpec filter(Closure closure) {
        return getRootSpec().filter(closure);
    }

    /**
     * {@inheritDoc}
     */
    public int getDirMode() {
        return getRootSpec().getDirMode();
    }

    /**
     * {@inheritDoc}
     */
    public int getFileMode() {
        return getRootSpec().getFileMode();
    }

    /**
     * {@inheritDoc}
     */
    public CopyProcessingSpec setDirMode(int mode) {
        return getRootSpec().setDirMode(mode);
    }

    /**
     * {@inheritDoc}
     */
    public CopyProcessingSpec setFileMode(int mode) {
        return getRootSpec().setFileMode(mode);
    }
}
