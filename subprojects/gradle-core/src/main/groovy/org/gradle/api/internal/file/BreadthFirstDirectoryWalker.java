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
public class BreadthFirstDirectoryWalker implements DirectoryWalker {
    private static Logger logger = LoggerFactory.getLogger(BreadthFirstDirectoryWalker.class);

    private FileVisitor visitor;
    private Spec<RelativePath> spec;

    public BreadthFirstDirectoryWalker(FileVisitor visitor) {
        spec = Specs.satisfyAll();
        this.visitor = visitor;
    }

    public BreadthFirstDirectoryWalker match(PatternSet patternSet) {
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
               // need to get appropriate start dirs from the includes
               walkDir(root, new RelativePath(false), stopFlag);
            }
        } else {
            logger.info("file or directory '"+startFile.toString()+"', not found");
        }
    }

    private void processSingleFile(File file, AtomicBoolean stopFlag) {
        RelativePath path = new RelativePath(true, file.getName());
        if (isAllowed(path)) {
            notifyFile(file, path, stopFlag);
        }
    }

    private void walkDir(File file, RelativePath path, AtomicBoolean stopFlag) {
        File[] children = file.listFiles();
        List<File> dirs = new ArrayList<File>();
        for (int i = 0; !stopFlag.get() && i < children.length; i++) {
            File child = children[i];
            boolean isFile = child.isFile();
            RelativePath childPath = new RelativePath(isFile, path, child.getName());
            if (isAllowed(childPath)) {
                if (isFile) {
                    notifyFile(child, childPath, stopFlag);
                } else {
                    dirs.add(child);
                }
            }
        }

        // now handle dirs
        for (int i = 0; !stopFlag.get() && i < dirs.size(); i++) {
            File dir = dirs.get(i);
            RelativePath dirPath = new RelativePath(false, path, dir.getName());
            notifyDir(dir, dirPath, stopFlag);
            walkDir(dir, dirPath, stopFlag);
        }
    }

    boolean isAllowed(RelativePath path) {
        return spec.isSatisfiedBy(path);
    }

    private void notifyDir(File dir, RelativePath path, AtomicBoolean stopFalg) {
        visitor.visitDir(new FileVisitDetailsImpl(dir, path, stopFalg));
    }

    private void notifyFile(File file, RelativePath path, AtomicBoolean stopFlag) {
        visitor.visitFile(new FileVisitDetailsImpl(file, path, stopFlag));
    }

    private static class FileVisitDetailsImpl implements FileVisitDetails {
        private final File file;
        private final RelativePath relativePath;
        private final AtomicBoolean stop;

        private FileVisitDetailsImpl(File file, RelativePath relativePath, AtomicBoolean stop) {
            this.file = file;
            this.relativePath = relativePath;
            this.stop = stop;
        }

        public File getFile() {
            return file;
        }

        public RelativePath getRelativePath() {
            return relativePath;
        }

        public void stopVisiting() {
            stop.set(true);
        }
    }
}
