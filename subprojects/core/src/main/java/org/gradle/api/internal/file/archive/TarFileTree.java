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
package org.gradle.api.internal.file.archive;

import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.AbstractFileTreeElement;
import org.gradle.api.internal.file.DefaultFileVisitDetails;
import org.gradle.api.internal.file.FileSystemSubset;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.file.collections.FileSystemMirroringFileTree;
import org.gradle.api.internal.file.collections.MinimalFileTree;
import org.gradle.api.internal.file.collections.SingletonFileTree;
import org.gradle.api.resources.ResourceException;
import org.gradle.api.resources.internal.ReadableResourceInternal;
import org.gradle.internal.IoActions;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.nativeintegration.filesystem.Chmod;
import org.gradle.internal.nativeintegration.filesystem.Stat;
import org.gradle.util.GFileUtils;

import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public class TarFileTree implements MinimalFileTree, FileSystemMirroringFileTree {
    private final File tarFile;
    private final ReadableResourceInternal resource;
    private final Chmod chmod;
    private final Stat stat;
    private final DirectoryFileTreeFactory directoryFileTreeFactory;
    private final File tmpDir;
    private final StreamHasher streamHasher;

    public TarFileTree(@Nullable File tarFile, ReadableResourceInternal resource, File tmpDir, Chmod chmod, Stat stat, DirectoryFileTreeFactory directoryFileTreeFactory, StreamHasher streamHasher) {
        this.tarFile = tarFile;
        this.resource = resource;
        this.chmod = chmod;
        this.stat = stat;
        this.directoryFileTreeFactory = directoryFileTreeFactory;
        this.tmpDir = tmpDir;
        this.streamHasher = streamHasher;
    }

    public String getDisplayName() {
        return String.format("TAR '%s'", resource.getDisplayName());
    }

    public DirectoryFileTree getMirror() {
        return directoryFileTreeFactory.create(getExpandedDir());
    }

    public void visit(FileVisitor visitor) {
        InputStream inputStream;
        try {
            inputStream = new BufferedInputStream(resource.read());
        } catch (ResourceException e) {
            throw cannotExpand(e);
        }

        try {
            try {
                visitImpl(visitor, inputStream);
            } finally {
                inputStream.close();
            }
        } catch (Exception e) {
            String message = "Unable to expand " + getDisplayName() + "\n"
                + "  The tar might be corrupted or it is compressed in an unexpected way.\n"
                + "  By default the tar tree tries to guess the compression based on the file extension.\n"
                + "  If you need to specify the compression explicitly please refer to the DSL reference.";
            throw new GradleException(message, e);
        }
    }

    private void visitImpl(FileVisitor visitor, InputStream inputStream) throws IOException {
        AtomicBoolean stopFlag = new AtomicBoolean();
        NoCloseTarInputStream tar = new NoCloseTarInputStream(inputStream);
        TarEntry entry;
        File expandedDir = getExpandedDir();
        while (!stopFlag.get() && (entry = tar.getNextEntry()) != null) {
            if (entry.isDirectory()) {
                visitor.visitDir(new DetailsImpl(resource, expandedDir, entry, tar, stopFlag, chmod));
            } else {
                visitor.visitFile(new DetailsImpl(resource, expandedDir, entry, tar, stopFlag, chmod));
            }
        }
    }

    private File getBackingFile() {
        if (tarFile != null) {
            return tarFile;
        }
        return resource.getBackingFile();
    }

    private File getExpandedDir() {
        InputStream inputStream = null;
        try {
            inputStream = new BufferedInputStream(resource.read());
            String expandedDirName = resource.getBaseName() + "_" + streamHasher.hash(inputStream);
            return new File(tmpDir, expandedDirName);
        } catch (ResourceException e) {
            throw cannotExpand(e);
        } finally {
            IoActions.closeQuietly(inputStream);
        }
    }

    private RuntimeException cannotExpand(Exception e) {
        throw new InvalidUserDataException(String.format("Cannot expand %s.", getDisplayName()), e);
    }

    @Override
    public void registerWatchPoints(FileSystemSubset.Builder builder) {
        File backingFile = getBackingFile();
        if (backingFile != null) {
            builder.add(backingFile);
        }
    }

    @Override
    public void visitTreeOrBackingFile(final FileVisitor visitor) {
        File backingFile = getBackingFile();
        if (backingFile != null) {
            new SingletonFileTree(backingFile).visit(visitor);
        } else {
            // We need to wrap the visitor so that the file seen by the visitor has already
            // been extracted from the archive and we do not try to extract it again.
            // It's unsafe to keep the FileVisitDetails provided by TarFileTree directly
            // because we do not expect to visit the same paths again (after extracting everything).
            visit(new FileVisitor() {
                @Override
                public void visitDir(FileVisitDetails dirDetails) {
                    visitor.visitDir(new DefaultFileVisitDetails(dirDetails.getFile(), chmod, stat));
                }

                @Override
                public void visitFile(FileVisitDetails fileDetails) {
                    visitor.visitFile(new DefaultFileVisitDetails(fileDetails.getFile(), chmod, stat));
                }
            });
        }
    }

    private static class DetailsImpl extends AbstractFileTreeElement implements FileVisitDetails {
        private final TarEntry entry;
        private final NoCloseTarInputStream tar;
        private final AtomicBoolean stopFlag;
        private final ReadableResourceInternal resource;
        private final File expandedDir;
        private File file;
        private boolean read;

        public DetailsImpl(ReadableResourceInternal resource, File expandedDir, TarEntry entry, NoCloseTarInputStream tar, AtomicBoolean stopFlag, Chmod chmod) {
            super(chmod);
            this.resource = resource;
            this.expandedDir = expandedDir;
            this.entry = entry;
            this.tar = tar;
            this.stopFlag = stopFlag;
        }

        public String getDisplayName() {
            return String.format("tar entry %s!%s", resource.getDisplayName(), entry.getName());
        }

        public void stopVisiting() {
            stopFlag.set(true);
        }

        public File getFile() {
            if (file == null) {
                file = new File(expandedDir, entry.getName());
                if (!file.exists()) {
                    copyTo(file);
                }
            }
            return file;
        }

        public long getLastModified() {
            return entry.getModTime().getTime();
        }

        public boolean isDirectory() {
            return entry.isDirectory();
        }

        public long getSize() {
            return entry.getSize();
        }

        public InputStream open() {
            if (read && file != null) {
                return GFileUtils.openInputStream(file);
            }
            if (read || tar.getCurrent() != entry) {
                throw new UnsupportedOperationException(String.format("The contents of %s has already been read.", this));
            }
            read = true;
            return tar;
        }

        public RelativePath getRelativePath() {
            return new RelativePath(!entry.isDirectory(), entry.getName().split("/"));
        }

        public int getMode() {
            return entry.getMode() & 0777;
        }
    }

    private static class NoCloseTarInputStream extends TarInputStream {
        public NoCloseTarInputStream(InputStream is) {
            super(is);
        }

        @Override
        public void close() throws IOException {
        }

        public TarEntry getCurrent() {
            return currEntry;
        }
    }
}
