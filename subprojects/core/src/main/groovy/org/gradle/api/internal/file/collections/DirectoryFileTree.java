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

package org.gradle.api.internal.file.collections;

import org.gradle.api.GradleException;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.DefaultFileTreeElement;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.util.GFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

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
public class DirectoryFileTree implements MinimalFileTree, PatternFilterableFileTree, RandomAccessFileCollection, LocalFileTree {
    private static Logger logger = LoggerFactory.getLogger(DirectoryFileTree.class);

    private final File root;
    private PatternSet patternSet;
    private boolean depthFirst;

    public DirectoryFileTree(File root) {
        this(root, new PatternSet());
    }

    public DirectoryFileTree(File root, PatternSet patternSet) {
        this.patternSet = patternSet;
        this.root = GFileUtils.canonicalise(root);
    }

    public String getDisplayName() {
        return String.format("directory '%s'", root);
    }

    public PatternSet getPatternSet() {
        return patternSet;
    }

    public File getRoot() {
        return root;
    }

    public Collection<DirectoryFileTree> getLocalContents() {
        return Collections.singletonList(this);
    }

    public DirectoryFileTree filter(PatternFilterable patterns) {
        PatternSet patternSet = this.patternSet.intersect();
        patternSet.copyFrom(patterns);
        return new DirectoryFileTree(root, patternSet);
    }

    public boolean contains(File file) {
        String prefix = root.getAbsolutePath() + File.separator;
        if (!file.getAbsolutePath().startsWith(prefix)) {
            return false;
        }
        if (!file.isFile()) {
            return false;
        }
        RelativePath path = new RelativePath(true, file.getAbsolutePath().substring(prefix.length()).split(
                Pattern.quote(File.separator)));
        return patternSet.getAsSpec().isSatisfiedBy(new DefaultFileTreeElement(file, path));
    }

    /**
     * Process the specified file or directory.  Note that the startFile parameter
     * may be either a directory or a file.  If it is a directory, then it's contents
     * (but not the directory itself) will be checked with isAllowed and notified to
     * the listener.  If it is a file, the file will be checked and notified.
     */
    public void visit(FileVisitor visitor) {
        AtomicBoolean stopFlag = new AtomicBoolean();
        Spec<FileTreeElement> spec = patternSet.getAsSpec();
        if (root.exists()) {
            if (root.isFile()) {
                processSingleFile(root, visitor, spec, stopFlag);
            } else {
                walkDir(root, new RelativePath(false), visitor, spec, stopFlag);
            }
        } else {
            logger.info("file or directory '" + root + "', not found");
        }
    }

    private void processSingleFile(File file, FileVisitor visitor, Spec<FileTreeElement> spec, AtomicBoolean stopFlag) {
        RelativePath path = new RelativePath(true, file.getName());
        FileVisitDetailsImpl details = new FileVisitDetailsImpl(file, path, stopFlag);
        if (isAllowed(details, spec)) {
            visitor.visitFile(details);
        }
    }

    private void walkDir(File file, RelativePath path, FileVisitor visitor, Spec<FileTreeElement> spec, AtomicBoolean stopFlag) {
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
            if (isAllowed(details, spec)) {
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
                walkDir(dir.getFile(), dir.getRelativePath(), visitor, spec, stopFlag);
                visitor.visitDir(dir);
            } else {
                visitor.visitDir(dir);
                walkDir(dir.getFile(), dir.getRelativePath(), visitor, spec, stopFlag);
            }
        }
    }

    boolean isAllowed(FileTreeElement element, Spec<FileTreeElement> spec) {
        return spec.isSatisfiedBy(element);
    }

    public DirectoryFileTree depthFirst() {
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
