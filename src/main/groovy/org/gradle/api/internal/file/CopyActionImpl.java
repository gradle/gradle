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

import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.CopyAction;
import org.gradle.api.tasks.util.PatternSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * @author Steve Appling
 */
public class CopyActionImpl extends CopySpecImpl implements CopyAction {
    private static Logger logger = LoggerFactory.getLogger(CopyActionImpl.class);
    private static String[] globalExcludes;

    private boolean caseSensitive = true;

    private boolean didWork;

    // following are only injected for test purposes
    private DirectoryWalker directoryWalker;
    private CopyVisitor visitor;

    public CopyActionImpl(FileResolver resolver) {
        super(resolver);
    }

    public void configureRootSpec() {
        if (globalExcludes != null && !getAllExcludes().containsAll(Arrays.asList(globalExcludes))) {
            exclude(globalExcludes);
        }
    }

    public void setVisitor(CopyVisitor visitor) {
        this.visitor = visitor;
    }

    public void setDirectoryWalker(DirectoryWalker directoryWalker) {
        this.directoryWalker = directoryWalker;
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

        List<CopySpecImpl> specList = getLeafSyncSpecs();
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
            for (File source : spec.getAllSourceDirs()) {
                copySingleSource(spec, source);
            }
        }
    }

    private void copySingleSource(CopySpecImpl spec, File source) {
        CopyVisitor visitor = this.visitor;
        if (visitor == null) {
            visitor = new CopyVisitor(spec.getDestDir(),
                    spec.getRemapClosures(),
                    spec.getRenameMappers(),
                    spec.getFilterChain() );
        }
        DirectoryWalker walker = directoryWalker;
        if (walker == null) {
            walker = new BreadthFirstDirectoryWalker(visitor);
        }
        PatternSet patterns = new PatternSet();
        patterns.setCaseSensitive(caseSensitive);
        patterns.include(spec.getAllIncludes());
        patterns.exclude(spec.getAllExcludes());
        walker.match(patterns);

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
}
