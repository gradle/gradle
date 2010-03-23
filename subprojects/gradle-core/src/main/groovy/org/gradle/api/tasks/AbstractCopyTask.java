/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.api.Action;
import org.gradle.api.file.*;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.file.copy.CopyActionImpl;
import org.gradle.api.internal.file.copy.CopySpecImpl;
import org.gradle.api.specs.Spec;

import java.io.FilterReader;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

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
    public boolean isCaseSensitive() {
        return getCopyAction().isCaseSensitive();
    }

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
    public AbstractCopyTask from(Object... sourcePaths) {
        getRootSpec().from(sourcePaths);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask from(Object sourcePath, Closure c) {
        getRootSpec().from(sourcePath, c);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public CopySpec with(CopySpec sourceSpec) {
        getRootSpec().with(sourceSpec);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask into(Object destDir) {
        getRootSpec().into(destDir);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask into(Object destPath, Closure configureClosure) {
        getRootSpec().into(destPath, configureClosure);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask include(String... includes) {
        getRootSpec().include(includes);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask include(Iterable<String> includes) {
        getRootSpec().include(includes);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask include(Spec<FileTreeElement> includeSpec) {
        getRootSpec().include(includeSpec);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask include(Closure includeSpec) {
        getRootSpec().include(includeSpec);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask exclude(String... excludes) {
        getRootSpec().exclude(excludes);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask exclude(Iterable<String> excludes) {
        getRootSpec().exclude(excludes);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask exclude(Spec<FileTreeElement> excludeSpec) {
        getRootSpec().exclude(excludeSpec);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask exclude(Closure excludeSpec) {
        getRootSpec().exclude(excludeSpec);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask setIncludes(Iterable<String> includes) {
        getRootSpec().setIncludes(includes);
        return this;
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
    public AbstractCopyTask setExcludes(Iterable<String> excludes) {
        getRootSpec().setExcludes(excludes);
        return this;
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
    public AbstractCopyTask rename(Closure closure) {
        getRootSpec().rename(closure);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask rename(String sourceRegEx, String replaceWith) {
        getRootSpec().rename(sourceRegEx, replaceWith);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask rename(Pattern sourceRegEx, String replaceWith) {
        getRootSpec().rename(sourceRegEx, replaceWith);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask filter(Map<String, ?> properties, Class<? extends FilterReader> filterType) {
        getRootSpec().filter(properties, filterType);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask filter(Class<? extends FilterReader> filterType) {
        getRootSpec().filter(filterType);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask filter(Closure closure) {
        getRootSpec().filter(closure);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask expand(Map<String, ?> properties) {
        getRootSpec().expand(properties);
        return this;
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
    public AbstractCopyTask setDirMode(int mode) {
        getRootSpec().setDirMode(mode);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask setFileMode(int mode) {
        getRootSpec().setFileMode(mode);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask eachFile(Action<? super FileCopyDetails> action) {
        getRootSpec().eachFile(action);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractCopyTask eachFile(Closure closure) {
        getRootSpec().eachFile(closure);
        return this;
    }
}
