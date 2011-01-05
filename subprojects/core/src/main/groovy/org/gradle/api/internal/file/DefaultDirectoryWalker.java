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

package org.gradle.api.internal.file;

import org.gradle.api.GradleException;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.RelativePath;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.util.GFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Directory walker supporting {@link Spec}s for includes and excludes.
 * The file system is traversed breadth first - all files in a directory will be
 * visited before any child directory is visited.
 *
 * A file or directory will only be visited if it matches all includes and no
 * excludes.
 *
 * @author Steve Appling
 */
public class DefaultDirectoryWalker implements DirectoryWalker {
    private static Logger logger = LoggerFactory.getLogger(DefaultDirectoryWalker.class);

    private FileVisitor visitor;
    private Spec<FileTreeElement> spec;
    private boolean depthFirst;

    public DefaultDirectoryWalker(FileVisitor visitor) {
        spec = Specs.satisfyAll();
        this.visitor = visitor;
    }

    public DefaultDirectoryWalker match(PatternSet patternSet) {
        spec = patternSet.getAsSpec();
        return this;
    }

    /**
     * Process the specified file or directory.  Note that the startFile parameter
     * may be either a directory or a file.  If it is a directory, then it's contents
     * (but not the directory itself) will be checked with isAllowed and notified to
     * the listener.  If it is a file, the file will be checked and notified.
     */
    public void start(File startFile) {
        File root = GFileUtils.canonicalise(startFile);
        AtomicBoolean stopFlag = new AtomicBoolean();
        if (root.exists()) {
            if (root.isFile()) {
                processSingleFile(root, stopFlag);
            } else {
               walkDir(root, new RelativePath(false), stopFlag);
            }
        } else {
            logger.info("file or directory '"+startFile.toString()+"', not found");
        }
    }

    private void processSingleFile(File file, AtomicBoolean stopFlag) {
        RelativePath path = new RelativePath(true, file.getName());
        FileVisitDetailsImpl details = new FileVisitDetailsImpl(file, path, stopFlag);
        if (isAllowed(details)) {
            visitor.visitFile(details);
        }
    }

    private void walkDir(File file, RelativePath path, AtomicBoolean stopFlag) {
        File[] children = file.listFiles();
        if (children == null) {
            if (file.isDirectory() && !file.canRead()) {
                throw new GradleException(String.format("Could not list contents of directory '%s' as it is not readable.", file));
            }
            // else, might be a link which points to nothing, or has been removed while we're visiting, or ...
            throw new GradleException(String.format("Could not list contents of '%s'.", file));
        }
        List<FileVisitDetailsImpl> dirs = new ArrayList<FileVisitDetailsImpl>();
        for (int i = 0; !stopFlag.get() && i < children.length; i++) {
            File child = children[i];
            boolean isFile = child.isFile();
            RelativePath childPath = path.append(isFile, child.getName());
            FileVisitDetailsImpl details = new FileVisitDetailsImpl(child, childPath, stopFlag);
            if (isAllowed(details)) {
                if (isFile) {
                    visitor.visitFile(details);
                } else {
                    dirs.add(details);
                }
            }
        }

        // now handle dirs
        for (int i = 0; !stopFlag.get() && i < dirs.size(); i++) {
            FileVisitDetailsImpl dir = dirs.get(i);
            if (depthFirst) {
                walkDir(dir.getFile(), dir.getRelativePath(), stopFlag);
                visitor.visitDir(dir);
            } else {
                visitor.visitDir(dir);
                walkDir(dir.getFile(), dir.getRelativePath(), stopFlag);
            }
        }
    }

    boolean isAllowed(FileTreeElement element) {
        return spec.isSatisfiedBy(element);
    }

    public DirectoryWalker depthFirst() {
        depthFirst = true;
        return this;
    }

    private static class FileVisitDetailsImpl extends DefaultFileTreeElement implements FileVisitDetails {
        private final AtomicBoolean stop;

        private FileVisitDetailsImpl(File file, RelativePath relativePath, AtomicBoolean stop) {
            super(file, relativePath);
            this.stop = stop;
        }

        public void stopVisiting() {
            stop.set(true);
        }
    }
}
