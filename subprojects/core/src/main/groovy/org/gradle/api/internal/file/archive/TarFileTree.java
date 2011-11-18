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
import org.gradle.api.internal.file.archive.compression.FileExtensionBasedDecompressor;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.FileSystemMirroringFileTree;
import org.gradle.api.internal.file.collections.MinimalFileTree;
import org.gradle.api.tasks.bundling.Decompressor;
import org.gradle.util.GFileUtils;
import org.gradle.util.HashUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public class TarFileTree implements MinimalFileTree, FileSystemMirroringFileTree {
    private final File tarFile;
    private Decompressor decompressor;
    private final File tmpDir;

    public TarFileTree(File tarFile, File tmpDir) {
            this(tarFile, tmpDir, new FileExtensionBasedDecompressor());
    }

    public TarFileTree(File tarFile, File tmpDir, Decompressor decompressor) {
        this.tarFile = tarFile;
        this.decompressor = decompressor;
        String expandDirName = String.format("%s_%s", tarFile.getName(), HashUtil.createHash(tarFile.getAbsolutePath()));
        this.tmpDir = new File(tmpDir, expandDirName);
    }

    public Decompressor getDecompressor() {
        return decompressor;
    }

    public void setDecompressor(Decompressor decompressor) {
        assert decompressor != null: "decompressor cannot be null!";
        this.decompressor = decompressor;
    }

    public String getDisplayName() {
        return String.format("TAR '%s'", tarFile);
    }

    public DirectoryFileTree getMirror() {
        return new DirectoryFileTree(tmpDir);
    }

    public void visit(FileVisitor visitor) {
        if (!tarFile.exists()) {
            return;
        }
        if (!tarFile.isFile()) {
            throw new InvalidUserDataException(String.format("Cannot expand %s as it is not a file.", getDisplayName()));
        }

        try {
            InputStream inputStream = null;
            try {
                inputStream = decompressor.decompress(tarFile);
                visitImpl(visitor, inputStream);
            } finally {
                assert inputStream != null;
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
        while (!stopFlag.get() && (entry = tar.getNextEntry()) != null) {
            if (entry.isDirectory()) {
                visitor.visitDir(new DetailsImpl(entry, tar, stopFlag));
            } else {
                visitor.visitFile(new DetailsImpl(entry, tar, stopFlag));
            }

        }
    }

    private class DetailsImpl extends AbstractFileTreeElement implements FileVisitDetails {
        private final TarEntry entry;
        private final NoCloseTarInputStream tar;
        private final AtomicBoolean stopFlag;
        private File file;
        private boolean read;

        public DetailsImpl(TarEntry entry, NoCloseTarInputStream tar, AtomicBoolean stopFlag) {
            this.entry = entry;
            this.tar = tar;
            this.stopFlag = stopFlag;
        }

        public String getDisplayName() {
            return String.format("tar entry %s!%s", tarFile, entry.getName());
        }

        public void stopVisiting() {
            stopFlag.set(true);
        }

        public File getFile() {
            if (file == null) {
                file = new File(tmpDir, entry.getName());
                copyTo(file);
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
