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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.gradle.api.*;
import org.gradle.api.tasks.copy.CopyAction;
import org.gradle.api.tasks.copy.CopySpec;

import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.io.File;
import java.io.IOException;
import java.io.FilterReader;

import groovy.lang.Closure;

/**
 * @author Steve Appling
 */
public class CopyActionImpl implements CopyAction {
    private static Logger logger = LoggerFactory.getLogger(CopyActionImpl.class);
    private static String[] globalExcludes;

    CopySpecImpl rootSpec;
    private boolean caseSensitive = true;

    private Project project;
    private boolean didWork;

    // following are only injected for test purposes
    private DirectoryWalker testWalker;
    private FileVisitor testVisitor;



    public CopyActionImpl(Project project) {
        rootSpec = new CopySpecImpl(project);
        this.project = project;
    }

    // Only used for testing
    public CopyActionImpl(Project project, FileVisitor testVisitor, DirectoryWalker testWalker) {
        this(project);
        this.testVisitor = testVisitor;
        this.testWalker = testWalker;
    }

    public void configureRootSpec() {
        if (globalExcludes != null && !rootSpec.getAllExcludes().containsAll(Arrays.asList(globalExcludes))) {
            rootSpec.exclude(globalExcludes);
        }
    }

    public void execute() {
        didWork = false;
        copyAllSpecs();
    }

    public boolean getDidWork() {
        return didWork;
    }

    private void copyAllSpecs() {
        configureRootSpec();

        List<CopySpecImpl> specList = (List<CopySpecImpl>) getLeafSyncSpecs();
        for (CopySpecImpl spec : specList) {
            copySingleSpec(spec);
        }
    }


    private void copySingleSpec(CopySpecImpl spec) {
        File destDir = spec.getDestDir();
        if (destDir == null) {
            logger.error("No destination dir for Copy task");
            throw new InvalidUserDataException("Error - no destination for Copy task, use 'into' to specify a target directory.");
        }
        else {
            List<File> sources = spec.getAllSourceDirs();
            for (File source : sources) {
                copySingleSource(spec, source);
            }
        }
    }

    private void copySingleSource(CopySpecImpl spec, File source) {
        FileVisitor visitor = testVisitor;
        if (visitor == null) {
            visitor = new CopyVisitor(spec.getDestDir(),
                    spec.getRemapClosures(),
                    spec.getRenameMappers(),
                    spec.getFilterChain() );
        }
        DirectoryWalker walker = testWalker;
        if (walker == null) {
            walker = new BreadthFirstDirectoryWalker(caseSensitive, visitor);
        }
        walker.addIncludes(spec.getAllIncludes());
        walker.addExcludes(spec.getAllExcludes());

        try {
            walker.start(source);
        } catch (IOException e) {
            throw new GradleException("IO Error during copy", e);
        }
        didWork = visitor.getDidWork();
    }

   public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    public List<? extends CopySpec> getLeafSyncSpecs() {
        return rootSpec.getLeafSyncSpecs();
    }

    public CopySpec getRootSyncSpec() {
        return rootSpec;
    }

    /**
     * Set the exclude patterns used by all Copy tasks.
     * This is typically used to set VCS type excludes like:
     * <pre>
     * Copy.globalExclude( '**\\.svn\\' )
     * </pre>
     * Note that there are no global excludes by default.
     * @param excludes
     */
    public static void globalExclude(String... excludes) {
        globalExcludes = excludes;
    }

    // Following 4 methods are used only to get convention src and dest
    public List getSrcDirs() {
        List<File> srcDirs = rootSpec.getSourceDirs();
        return srcDirs.size()>0 ? srcDirs : null;
    }

    public void setSrcDirs(List srcDirs) {
        rootSpec.from(srcDirs);
    }

    public File getDestinationDir() {
        return rootSpec.getDestDir();
    }

    public void setDestinationDir(File destinationDir) {
        rootSpec.into(destinationDir);
    }

    // -------------------------------------------------
    // ------ Delegate SyncSpec methods to rootSpec ----
    // -------------------------------------------------

    public CopySpec from(Object... sourcePaths) {
        return rootSpec.from(sourcePaths);
    }

    public CopySpec from(Object sourcePath, Closure c) {
        return rootSpec.from(sourcePath, c);
    }

    public CopySpec from(Iterable<Object> sourcePaths) {
        return rootSpec.from(sourcePaths);
    }

    public CopySpec from(Iterable<Object> sourcePaths, Closure c) {
        return rootSpec.from(sourcePaths, c);
    }

    public CopySpec into(Object destDir) {
        return rootSpec.into(destDir);
    }

    public CopySpec include(String... includes) {
        return rootSpec.include(includes);
    }

    public CopySpec exclude(String... excludes) {
        return rootSpec.exclude(excludes);
    }

    public CopySpec remapTarget(Closure closure) {
        return rootSpec.remapTarget(closure);
    }

    public CopySpec rename(String sourceRegEx, String replaceWith) {
        return rootSpec.rename(sourceRegEx, replaceWith);
    }

    public CopySpec filter(Map<String, Object> map, Class<FilterReader> filterType) {
        return rootSpec.filter(map, filterType);
    }

    public CopySpec filter(Class<FilterReader> filterType) {
        return rootSpec.filter(filterType);
    }
}
