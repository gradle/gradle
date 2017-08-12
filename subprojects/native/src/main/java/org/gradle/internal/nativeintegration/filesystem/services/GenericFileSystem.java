/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.internal.nativeintegration.filesystem.services;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.gradle.internal.file.FileMetadataSnapshot;
import org.gradle.internal.nativeintegration.filesystem.FileException;
import org.gradle.internal.nativeintegration.filesystem.FileMetadataAccessor;
import org.gradle.internal.nativeintegration.filesystem.FileModeAccessor;
import org.gradle.internal.nativeintegration.filesystem.FileModeMutator;
import org.gradle.internal.nativeintegration.filesystem.Symlink;
import org.gradle.internal.nativeplatform.filesystem.FileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

class GenericFileSystem implements FileSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(GenericFileSystem.class);

    private Boolean caseSensitive;
    private final boolean canCreateSymbolicLink;

    private final FileModeMutator chmod;
    private final FileModeAccessor stat;
    private final Symlink symlink;
    private final FileMetadataAccessor metadata;

    @Override
    public boolean isCaseSensitive() {
        initializeCaseSensitive();
        return caseSensitive;
    }

    @Override
    public boolean canCreateSymbolicLink() {
        return canCreateSymbolicLink;
    }

    @Override
    public void createSymbolicLink(File link, File target) {
        try {
            symlink.symlink(link, target);
        } catch (Exception e) {
            throw new FileException(String.format("Could not create symlink from '%s' to '%s'.", link.getPath(), target.getPath()), e);
        }
    }

    @Override
    public boolean isSymlink(File suspect) {
        return symlink.isSymlink(suspect);
    }

    @Override
    public int getUnixMode(File f) {
        try {
            return stat.getUnixMode(f);
        } catch (Exception e) {
            throw new FileException(String.format("Could not get file mode for '%s'.", f), e);
        }
    }

    @Override
    public FileMetadataSnapshot stat(File f) throws FileException {
        return metadata.stat(f);
    }

    @Override
    public void chmod(File f, int mode) {
        try {
            chmod.chmod(f, mode);
        } catch (Exception e) {
            throw new FileException(String.format("Could not set file mode %o on '%s'.", mode, f), e);
        }
    }

    public GenericFileSystem(FileModeMutator chmod, FileModeAccessor stat, Symlink symlink, FileMetadataAccessor metadata) {
        this.metadata = metadata;
        this.stat = stat;
        this.symlink = symlink;
        this.chmod = chmod;
        canCreateSymbolicLink = symlink.isSymlinkSupported();
    }

    private void initializeCaseSensitive() {
        if (caseSensitive == null) {

            String content = generateUniqueContent();
            File file = null;
            try {
                checkJavaIoTmpDirExists();
                file = createFile(content);
                caseSensitive = probeCaseSensitive(file, content);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                FileUtils.deleteQuietly(file);
            }
        }
    }

    private String generateUniqueContent() {
        return UUID.randomUUID().toString();
    }

    private File createFile(String content) throws IOException {
        File file = File.createTempFile("gradle_fs_probing", null, null);
        Files.write(content, file, Charsets.UTF_8);
        return file;
    }

    private boolean probeCaseSensitive(File file, String content) {
        try {
            File upperCased = new File(file.getPath().toUpperCase());
            return !hasContent(upperCased, content);
        } catch (IOException e) {
            // not fully accurate but a sensible fallback
            // see http://stackoverflow.com/questions/1288102/how-do-i-detect-whether-the-file-system-is-case-sensitive
            boolean result = !new File("foo").equals(new File("FOO"));
            LOGGER.info("Failed to determine if file system is case sensitive. Best guess is '{}'.", result);
            return result;
        }
    }

    private boolean hasContent(File file, String content) throws IOException {
        return file.exists() && Files.readFirstLine(file, Charsets.UTF_8).equals(content);
    }

    private void checkJavaIoTmpDirExists() throws IOException {
        File dir = new File(System.getProperty("java.io.tmpdir"));
        if (!dir.exists()) {
            throw new IOException("java.io.tmpdir is set to a directory that doesn't exist: " + dir);
        }
    }
}
