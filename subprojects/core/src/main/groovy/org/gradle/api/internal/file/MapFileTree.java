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

import groovy.lang.Closure;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.RelativePath;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link FileTree} which is composed using a mapping from relative path to file source.
 */
public class MapFileTree extends AbstractFileTree {
    private final Map<RelativePath, Closure> elements = new LinkedHashMap<RelativePath, Closure>();
    private final File tmpDir;

    public MapFileTree(File tmpDir) {
        this.tmpDir = tmpDir;
    }

    public String getDisplayName() {
        return "file tree";
    }

    @Override
    protected Collection<DefaultConfigurableFileTree> getAsFileTrees() {
        visitAll();
        return Collections.singleton(new DefaultConfigurableFileTree(tmpDir, null, null));
    }

    public FileTree visit(FileVisitor visitor) {
        AtomicBoolean stopFlag = new AtomicBoolean();
        Visit visit = new Visit(visitor, stopFlag);
        for (Map.Entry<RelativePath, Closure> entry : elements.entrySet()) {
            if (stopFlag.get()) {
                break;
            }
            RelativePath path = entry.getKey();
            Closure generator = entry.getValue();
            visit.visit(path, generator);
        }
        return this;
    }


    /**
     * Adds an element to this tree. The given closure is passed an OutputStream which it can use to write the content
     * of the element to.
     */
    public void add(String path, Closure contentClosure) {
        elements.put(RelativePath.parse(true, path), contentClosure);
    }

    private class Visit {
        private final Set<RelativePath> visitedDirs = new LinkedHashSet<RelativePath>();
        private final FileVisitor visitor;
        private final AtomicBoolean stopFlag;

        public Visit(FileVisitor visitor, AtomicBoolean stopFlag) {
            this.visitor = visitor;
            this.stopFlag = stopFlag;
        }

        private void visitDirs(RelativePath path, FileVisitor visitor) {
            if (path == null || path.getParent() == null || !visitedDirs.add(path)) {
                return;
            }

            visitDirs(path.getParent(), visitor);
            visitor.visitDir(new FileVisitDetailsImpl(path, null, stopFlag));
        }

        public void visit(RelativePath path, Closure generator) {
            visitDirs(path.getParent(), visitor);
            visitor.visitFile(new FileVisitDetailsImpl(path, generator, stopFlag));
        }
    }

    private class FileVisitDetailsImpl extends AbstractFileTreeElement implements FileVisitDetails {
        private final RelativePath path;
        private final Closure generator;
        private final long lastModified;
        private final AtomicBoolean stopFlag;
        private File file;

        public FileVisitDetailsImpl(RelativePath path, Closure generator, AtomicBoolean stopFlag) {
            this.path = path;
            this.generator = generator;
            this.stopFlag = stopFlag;
            // round to nearest second
            lastModified = System.currentTimeMillis() / 1000 * 1000;
        }

        public String getDisplayName() {
            return path.toString();
        }

        public void stopVisiting() {
            stopFlag.set(true);
        }

        public File getFile() {
            if (file == null) {
                file = path.getFile(tmpDir);
                copyTo(file);
            }
            return file;
        }

        public boolean isDirectory() {
            return !path.isFile();
        }

        public long getLastModified() {
            return lastModified;
        }

        public long getSize() {
            return getFile().length();
        }

        public void copyTo(OutputStream outstr) {
            generator.call(outstr);
        }

        public InputStream open() {
            throw new UnsupportedOperationException();
        }

        public RelativePath getRelativePath() {
            return path;
        }
    }
}
