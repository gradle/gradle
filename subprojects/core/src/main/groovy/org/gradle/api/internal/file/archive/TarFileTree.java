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
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.FileSystemMirroringFileTree;
import org.gradle.api.internal.file.collections.MinimalFileTree;
import org.gradle.api.resources.MissingResourceException;
import org.gradle.api.resources.ReadableResource;
import org.gradle.api.resources.ResourceException;
import org.gradle.internal.nativeplatform.filesystem.Chmod;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.GFileUtils;
import org.gradle.internal.hash.HashUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public class TarFileTree implements MinimalFileTree, FileSystemMirroringFileTree {
    private final ReadableResource resource;
    private final Chmod chmod;
    private final File tmpDir;

    public TarFileTree(ReadableResource resource, File tmpDir, Chmod chmod) {
        this.resource = resource;
        this.chmod = chmod;
        String expandDirName = String.format("%s_%s", resource.getBaseName(), HashUtil.createCompactMD5(resource.getURI().toString()));
        this.tmpDir = new File(tmpDir, expandDirName);
    }

    public String getDisplayName() {
        return String.format("TAR '%s'", resource.getDisplayName());
    }

    public DirectoryFileTree getMirror() {
        return new DirectoryFileTree(tmpDir);
    }

    public void visit(FileVisitor visitor) {
        InputStream inputStream;
        try {
            inputStream = resource.read();
            assert inputStream != null;
        } catch (MissingResourceException e) {
            DeprecationLogger.nagUserOfDeprecatedBehaviour(
                    String.format("The specified tar file %s does not exist and will be silently ignored", getDisplayName())
            );
            return;
        } catch (ResourceException e) {
            throw new InvalidUserDataException(String.format("Cannot expand %s.", getDisplayName()), e);
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
        while (!stopFlag.get() && (entry = tar.getNextEntry()) != null) {
            if (entry.isDirectory()) {
                visitor.visitDir(new DetailsImpl(entry, tar, stopFlag, chmod));
            } else {
                visitor.visitFile(new DetailsImpl(entry, tar, stopFlag, chmod));
            }
        }
    }

    private class DetailsImpl extends AbstractFileTreeElement implements FileVisitDetails {
        private final TarEntry entry;
        private final NoCloseTarInputStream tar;
        private final AtomicBoolean stopFlag;
        private File file;
        private boolean read;

        public DetailsImpl(TarEntry entry, NoCloseTarInputStream tar, AtomicBoolean stopFlag, Chmod chmod) {
            super(chmod);
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
