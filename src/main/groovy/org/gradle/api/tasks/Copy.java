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

import org.gradle.api.internal.tasks.copy.*;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.*;
import org.gradle.api.TaskAction;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.CopyAction;
import org.gradle.api.internal.tasks.copy.CopyActionImpl;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import groovy.lang.Closure;

import java.io.*;
import java.util.List;
import java.util.Map;

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
 *    from('src/main/languages') {
 *       rename 'EN_US_(*.)', '$1'
 *    }
 *    into 'build/target/config'
 *    exclude '**&#47;*.bak', '**&#47;CVS/'
 * }
 * </pre>
 * @author Steve Appling
 */
public class Copy extends ConventionTask implements CopyAction {
    private static Logger logger = LoggerFactory.getLogger(Copy.class);

    CopyActionImpl copyAction;

    public Copy(Project project, String name) {
        this(project, name, null, null);
    }

    // Only used for testing
    Copy(Project project, String name, FileVisitor testVisitor, DirectoryWalker testWalker) {
        super(project, name);

        FileResolver fileResolver = ((ProjectInternal) project).getFileResolver();
        copyAction = new CopyActionImpl(fileResolver, testVisitor, testWalker);

        doLast(new TaskAction() {
            public void execute(Task task) {
                configureRootSpec();
                copyAction.execute();
                setDidWork(copyAction.getDidWork());
            }
        });
    }

    void configureRootSpec() {
        CopySpecImpl rootSpec = (CopySpecImpl) copyAction.getRootSyncSpec();

        List srcDirs = getSrcDirs();
        List<File> rootSrcDirs = rootSpec.getSourceDirs();
        if ((srcDirs != null) &&
            (rootSrcDirs==null || !rootSrcDirs.equals(srcDirs)) ) {
            rootSpec.from(srcDirs);
        }

        File destDir = getDestinationDir();
        File rootDestDir = rootSpec.getDestDir();
        if ((destDir != null) &&
            (rootDestDir==null || !rootDestDir.equals(destDir)) ) {
            rootSpec.into(destDir);
        }
        copyAction.configureRootSpec();
    }


    /**
     * Set the exclude patterns used by all Copy tasks.
     * This is typically used to set VCS type excludes like:
     * <pre>
     * Copy.globalExclude( '**&#47;.svn/' )
     * </pre>
     * Note that there are no global excludes by default.
     * Unlike CopySpec.exclude, this does not add a new exclude pattern, it sets
     * (or resets) the exclude patterns.  You can't use sequential calls to
     * this method to add multiple global exclude patterns.
     * @param excludes exclude patterns to use
     */
    public static void globalExclude(String... excludes) {
        CopyActionImpl.globalExclude(excludes);
    }


    // Following 4 methods are used only to get convention src and dest
    public List getSrcDirs() {
        CopySpecImpl rootSpec = (CopySpecImpl) copyAction.getRootSyncSpec();
        List<File> srcDirs = rootSpec.getSourceDirs();
        return srcDirs.size()>0 ? srcDirs : null;
    }

    public void setSrcDirs(List srcDirs) {
        CopySpecImpl rootSpec = (CopySpecImpl) copyAction.getRootSyncSpec();
        rootSpec.from(srcDirs);
    }

    public File getDestinationDir() {
        CopySpecImpl rootSpec = (CopySpecImpl) copyAction.getRootSyncSpec();
        return rootSpec.getDestDir();
    }

    public void setDestinationDir(File destinationDir) {
        CopySpecImpl rootSpec = (CopySpecImpl) copyAction.getRootSyncSpec();
        rootSpec.into(destinationDir);
    }


    // -------------------------------------------------
    // --- Delegate CopyAction methods to copyAction ---
    // -------------------------------------------------

    /**
     * Set case sensitivity for comparisons.
     * @param caseSensitive
     */
    public void setCaseSensitive(boolean caseSensitive) {
        copyAction.setCaseSensitive(caseSensitive);
    }

    public List<? extends CopySpec> getLeafSyncSpecs() {
        return copyAction.getLeafSyncSpecs();
    }

    public CopySpec getRootSyncSpec() {
        return copyAction.getRootSyncSpec();
    }

    // -------------------------------------------------
    // ---- Delegate CopySpec methods to copyAction ----
    // -------------------------------------------------

    /**
     * Specifies sources for a copy.
     * The toString() method of each sourcePath is used to get a path.
     * The paths are evaluated like {@link org.gradle.api.Project#file(Object) Project.file() }.
     * Relative paths will be evaluated relative to the project directory.
     * @param sourcePaths Paths to source directories for the copy
     */
    public CopySpec from(Object... sourcePaths) {
        return copyAction.from(sourcePaths);
    }

    /**
     * Specifies the source for a copy and creates a child CopySpec.
     * SourcePath.toString is used as the path.
     * The source is set on the child CopySpec, not on this one.
     * This may be a path to a single file to copy or to a directory.  If the path is to a directory,
     * then the contents of the directory will be copied.
     * The paths are evaluated like {@link org.gradle.api.Project#file(Object) Project.file() }.
     * @param sourcePath Path to source for the copy
     * @param c closure for configuring the child CopySpec
     */
    public CopySpec from(Object sourcePath, Closure c) {
        return copyAction.from(sourcePath, c);
    }

    /**
     * Specifies sources for a copy.
     * The paths are evaluated like {@link org.gradle.api.Project#file(Object) Project.file() }.
     * @param sourcePaths Paths to source directories for the copy
     */
    public CopySpec from(Iterable<Object> sourcePaths) {
        return copyAction.from(sourcePaths);
    }

    /**
     * Specifies sources for a copy and creates a child
     * CopySpec which is configured with the Closure. The sources are
     * set on the child CopySpec, not on this one.
     * The paths are evaluated like {@link org.gradle.api.Project#file(Object) Project.file() }.
     * @param sourcePaths Paths to source directories for the copy
     * @param c Closure for configuring the child CopySpec
     */
    public CopySpec from(Iterable<Object> sourcePaths, Closure c) {
        return copyAction.from(sourcePaths, c);
    }


    /**
     * Specifies the destination directory for a copy.
     * @param destDir Destination directory
     */
    public CopySpec into(Object destDir) {
        return copyAction.into(destDir);
    }

    /**
     * Adds an ANT style include pattern to the copy specification.
     * This method may be called multiple times to append new patterns and
     * multiple patterns may be specified in a single call.
     *
     * Patterns may include:
     * <ul>
     *    <li>'*' to match any number of characters
     *    <li>'?' to match any single character
     *    <li>'**' to match any number of directories or files
     * </ul>
     *
     * Either '/' or '\' may be used in a pattern to separate directories.
     * Patterns ending with '/' or '\' will have '**' automatically appended.
     *
     * Examples:
     * <pre>
     * all files ending with 'jsp' (including subdirectories)
     *    **&#47;*.jsp
     *
     * all files beginning with 'template_' in the level1/level2 directory
     *    level1/level2/template_*
     *
     * all files (including subdirectories) beneath src/main/webapp
     *   src/main/webapp/
     *
     * all files beneath any CVS directory (including subdirectories) under src/main/java
     *   src/main/java/**&#47;CVS/**
     * </pre>
     *
     * If this method is not called, then all files beneath the source directory will be included.
     * If this method is called, then a file must match at least one of the include
     * patterns to be copied.
     * @param includes a vararg list of include patterns
     */
    public CopySpec include(String... includes) {
        return copyAction.include(includes);
    }


    /**
     * Adds an ANT style exclude pattern to the copy specification.
     * This method may be called multiple times to append new patterns and
     * multiple patterns may be specified in a single call.
     * See {@link #include(String[]) include} for a description of the
     * syntax for patterns.
     *
     * If this method is not called, then no files will be excluded.
     * If this method is called, then files must not match any exclude pattern
     * to be copied.
     *
     * @param excludes a vararg list of exclude patterns
     */
    public CopySpec exclude(String... excludes) {
        return copyAction.exclude(excludes);
    }


    /**
     * Maps a source file to a different relative location under the target directory.
     * The closure will be called with a single parameter, the File object
     * for the default location of the copy.  This File will have the same relative path
     * from the destination directory that the source file has from its source
     * directory.  The closure should return a File object with a new target destination.
     * @param closure remap closure
     */
    public CopySpec remapTarget(Closure closure) {
        return copyAction.remapTarget(closure);
    }


    /**
     * Renames files based on a regular expression.  Uses java.util.regex type of
     * regular expressions.  Note that the replace string should use the '$1' syntax
     * to refer to capture groups in the source regular expression.  Files that
     * do not match the source regular expression will be copied with the original name.
     *
     * <p>
     * Example:
     * <pre>
     * rename '(.*)_OEM_BLUE_(.*)', '$1$2'
     * </pre>
     * would map the file 'style_OEM_BLUE_.css' to 'style.css'
     * @param sourceRegEx Source regular expression
     * @param replaceWith Replacement string (use $ syntax for capture groups)
     */
    public CopySpec rename(String sourceRegEx, String replaceWith) {
        return copyAction.rename(sourceRegEx, replaceWith);
    }



    /**
     * Adds a content filter to be used during the copy.  Multiple calls to
     * filter, add additional filters to the filter chain.  Each filter should implement
     * java.io.FilterReader. Include org.apache.tools.ant.filters.* for access to
     * all the standard ANT filters.
     * <p>
     * Filter parameters may be specified using groovy map syntax.
     * <p>
     * Examples:
     * <pre>
     *    filter(HeadFilter, lines:25, skip:2)
     *    filter(ReplaceTokens, tokens:[copyright:'2009', version:'2.3.1'])
     * </pre>
     * @param map map of filter parameters
     * @param filterType Class of filter to add
     */
    public CopySpec filter(Map<String, Object> map, Class<FilterReader> filterType) {
        return copyAction.filter(map, filterType);
    }

    /**
     * Adds a content filter to be used during the copy.  Multiple calls to
     * filter, add additional filters to the filter chain.  Each filter should implement
     * java.io.FilterReader. Include org.apache.tools.ant.filters.* for access to
     * all the standard ANT filters.
     * <p>
     * Examples:
     * <pre>
     *    filter(StripJavaComments)
     *    filter(com.mycompany.project.CustomFilter)
     * </pre>
     * @param filterType Class of filter to add
     */
    public CopySpec filter(Class<FilterReader> filterType) {
        return copyAction.filter(filterType);
    }
}
