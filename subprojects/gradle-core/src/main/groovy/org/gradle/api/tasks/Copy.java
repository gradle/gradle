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
import org.gradle.api.file.CopyAction;
import org.gradle.api.file.CopySpec;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.file.CopyActionImpl;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectInternal;

import java.io.File;
import java.io.FilterReader;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Task for copying files.  This task can also rename and filter files as it copies.
 * The task implements {@link org.gradle.api.file.CopySpec CopySpec} for specifying
 * what to copy.
 * <p>
 * Examples:
 * <pre>
 * task(mydoc, type:Copy) {
 *    from 'src/main/doc'
 *    into 'build/target/doc'
 * }
 *
 *
 * task(initconfig, type:Copy) {
 *    from('src/main/config') {
 *       include '**&#47;*.properties'
 *       include '**&#47;*.xml'
 *       filter(ReplaceTokens, tokens:[version:'2.3.1'])
 *    }
 *    from('src/main/config') {
 *       exclude '**&#47;*.properties', '**&#47;*.xml'  
 *    }
 *    from('src/main/languages') {
 *       rename 'EN_US_(*.)', '$1'
 *    }
 *    into 'build/target/config'
 *    exclude '**&#47;*.bak'
 * }
 * </pre>
 * @author Steve Appling
 */
public class Copy extends ConventionTask implements CopyAction {
    private CopyActionImpl copyAction;
    private boolean hasSrcBeenSet;
    private boolean hasDestBeenSet;

    public Copy() {
        FileResolver fileResolver = ((ProjectInternal) getProject()).getFileResolver();
        copyAction = new CopyActionImpl(fileResolver);
    }

    @TaskAction
    void copy() {
        configureRootSpec();
        copyAction.execute();
        setDidWork(copyAction.getDidWork());
    }

    void configureRootSpec() {
        if (!hasSrcBeenSet) {
            Object srcDirs = getSrcDirs();
            if (srcDirs != null) {
                from(srcDirs);
            }
        }

        if (!hasDestBeenSet) {
            File destDir = getDestinationDir();
            if (destDir != null) {
                into(destDir);
            }
        }
    }

    public CopyActionImpl getCopyAction() {
        return copyAction;
    }

    public void setCopyAction(CopyActionImpl copyAction) {
        this.copyAction = copyAction;
    }

    // Following 4 methods are used only to get convention src and dest
    public Object getSrcDirs() {
        return hasSrcBeenSet ? copyAction.getSourcePaths() : null;
    }

    public void setSrcDirs(Object srcDirs) {
        copyAction.from(srcDirs);
    }

    public File getDestinationDir() {
        return hasDestBeenSet ? copyAction.getDestDir() : null;
    }

    public void setDestinationDir(File destinationDir) {
        into(destinationDir);
    }

    // -------------------------------------------------
    // --- Delegate CopyAction methods to copyAction ---
    // -------------------------------------------------

    /**
     * {@inheritDoc}
     */
    public void setCaseSensitive(boolean caseSensitive) {
        copyAction.setCaseSensitive(caseSensitive);
    }

    public List<? extends CopySpec> getLeafSyncSpecs() {
        return copyAction.getLeafSyncSpecs();
    }

    // -------------------------------------------------
    // ---- Delegate CopySpec methods to copyAction ----
    // -------------------------------------------------

    /**
     * {@inheritDoc}
     */
    public CopySpec from(Object... sourcePaths) {
        hasSrcBeenSet = true;
        return copyAction.from(sourcePaths);
    }

    /**
     * {@inheritDoc}
     */
    public CopySpec from(Object sourcePath, Closure c) {
        hasSrcBeenSet = true;
        return copyAction.from(sourcePath, c);
    }

    /**
     * {@inheritDoc}
     */
    public CopySpec into(Object destDir) {
        hasDestBeenSet = true;
        return copyAction.into(destDir);
    }

    /**
     * {@inheritDoc}
     */
    public CopySpec include(String... includes) {
        return copyAction.include(includes);
    }

    /**
     * {@inheritDoc}
     */
    public CopySpec include(Iterable<String> includes) {
        return copyAction.include(includes);
    }
    
    /**
     * {@inheritDoc}
     */
    public CopySpec exclude(String... excludes) {
        return copyAction.exclude(excludes);
    }

    /**
     * {@inheritDoc}
     */
    public CopySpec exclude(Iterable<String> excludes) {
        return copyAction.exclude(excludes);
    }

    /**
     * {@inheritDoc}
     */
    public CopySpec setIncludes(Iterable<String> includes) {
        return copyAction.setIncludes(includes);
    }

    /**
     * {@inheritDoc}
     */
    public Set<String> getIncludes() {
        return copyAction.getIncludes();
    }

    /**
     * {@inheritDoc}
     */
    public CopySpec setExcludes(Iterable<String> excludes) {
        return copyAction.setExcludes(excludes);
    }

    /**
     * {@inheritDoc}
     */
    public Set<String> getExcludes() {
        return copyAction.getExcludes();
    }

    /**
     * {@inheritDoc}
     */
    public CopySpec remapTarget(Closure closure) {
        return copyAction.remapTarget(closure);
    }

    /**
     * {@inheritDoc}
     */
    public CopySpec rename(String sourceRegEx, String replaceWith) {
        return copyAction.rename(sourceRegEx, replaceWith);
    }

    /**
     * {@inheritDoc}
     */
    public CopySpec filter(Map<String, Object> map, Class<FilterReader> filterType) {
        return copyAction.filter(map, filterType);
    }

    /**
     * {@inheritDoc}
     */
    public CopySpec filter(Class<FilterReader> filterType) {
        return copyAction.filter(filterType);
    }

    /**
     * {@inheritDoc}
     */
    public CopySpec filter(Closure closure) {
        return copyAction.filter(closure);
    }
}
