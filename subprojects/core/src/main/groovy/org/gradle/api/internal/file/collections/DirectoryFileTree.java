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
import org.gradle.api.file.*;
import org.gradle.api.internal.file.DefaultFileTreeElement;
import org.gradle.api.internal.file.DefaultFileVisitDetails;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.nativeintegration.services.FileSystems;
import org.gradle.util.GFileUtils;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Directory walker supporting {@link Spec}s for includes and excludes.
 * The file system is traversed in depth-first prefix order - all files in a directory will be
 * visited before any child directory is visited.
 *
 * A file or directory will only be visited if it matches all includes and no
 * excludes.
 */
public class DirectoryFileTree implements MinimalFileTree, PatternFilterableFileTree, RandomAccessFileCollection, LocalFileTree, DirectoryTree {
    private static final Logger LOGGER = Logging.getLogger(DirectoryFileTree.class);

    private final File dir;
    private PatternSet patternSet;
    private boolean postfix;
    private final FileSystem fileSystem = FileSystems.getDefault();

    public DirectoryFileTree(File dir) {
        this(dir, new PatternSet());
    }

    public DirectoryFileTree(File dir, PatternSet patternSet) {
        this.patternSet = patternSet;
        this.dir = GFileUtils.canonicalise(dir);
    }

    public String getDisplayName() {
        String includes = patternSet.getIncludes().isEmpty() ? "" : String.format(" include %s", GUtil.toString(patternSet.getIncludes()));
        String excludes = patternSet.getExcludes().isEmpty() ? "" : String.format(" exclude %s", GUtil.toString(patternSet.getExcludes()));
        return String.format("directory '%s'%s%s", dir, includes, excludes);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public PatternSet getPatterns() {
        return patternSet;
    }

    public File getDir() {
        return dir;
    }

    public Collection<DirectoryFileTree> getLocalContents() {
        return Collections.singletonList(this);
    }

    public DirectoryFileTree filter(PatternFilterable patterns) {
        PatternSet patternSet = this.patternSet.intersect();
        patternSet.copyFrom(patterns);
        return new DirectoryFileTree(dir, patternSet);
    }

    public boolean contains(File file) {
        String prefix = dir.getAbsolutePath() + File.separator;
        if (!file.getAbsolutePath().startsWith(prefix)) {
            return false;
        }
        if (!file.isFile()) {
            return false;
        }
        RelativePath path = new RelativePath(true, file.getAbsolutePath().substring(prefix.length()).split(
                Pattern.quote(File.separator)));
        return patternSet.getAsSpec().isSatisfiedBy(new DefaultFileTreeElement(file, path, fileSystem, fileSystem));
    }

    /**
     * Process the specified file or directory.  Note that the startFile parameter
     * may be either a directory or a file.  If it is a directory, then its contents
     * (but not the directory itself) will be checked with isAllowed and notified to
     * the listener.  If it is a file, the file will be checked and notified.
     */
    public void visit(FileVisitor visitor) {
        visitFrom(visitor, dir, new RelativePath(false));
    }

    public void visitFrom(FileVisitor visitor, File dir, RelativePath path) {
        AtomicBoolean stopFlag = new AtomicBoolean();
        Spec<FileTreeElement> spec = patternSet.getAsSpec();
        if (dir.exists()) {
            if (dir.isFile()) {
                processSingleFile(dir, visitor, spec, stopFlag);
            } else {
                walkDir(dir, path, visitor, spec, stopFlag);
            }
        } else {
            LOGGER.info("file or directory '" + dir + "', not found");
        }
    }

    private void processSingleFile(File file, FileVisitor visitor, Spec<FileTreeElement> spec, AtomicBoolean stopFlag) {
        RelativePath path = new RelativePath(true, file.getName());
        FileVisitDetails details = new DefaultFileVisitDetails(file, path, stopFlag, fileSystem, fileSystem);
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
        List<FileVisitDetails> dirs = new ArrayList<FileVisitDetails>();
        for (int i = 0; !stopFlag.get() && i < children.length; i++) {
            File child = children[i];
            boolean isFile = child.isFile();
            RelativePath childPath = path.append(isFile, child.getName());
            FileVisitDetails details = new DefaultFileVisitDetails(child, childPath, stopFlag, fileSystem, fileSystem);
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
            FileVisitDetails dir = dirs.get(i);
            if (postfix) {
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

    /**
     * Traverse directories (but not files) in postfix rather than prefix order.
     *
     * @return {@code this}
     */
    public DirectoryFileTree postfix() {
        postfix = true;
        return this;
    }
}
