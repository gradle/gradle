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
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.file.FileException;
import org.gradle.internal.file.FileMetadata;
import org.gradle.internal.file.StatStatistics;
import org.gradle.internal.nativeintegration.filesystem.FileMetadataAccessor;
import org.gradle.internal.nativeintegration.filesystem.FileModeAccessor;
import org.gradle.internal.nativeintegration.filesystem.FileModeMutator;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.nativeintegration.filesystem.Symlink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

class GenericFileSystem implements FileSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(GenericFileSystem.class);

    private Boolean caseSensitive;
    private final boolean canCreateSymbolicLink;
    private final TemporaryFileProvider temporaryFileProvider;

    private final FileModeMutator chmod;
    private final FileModeAccessor stat;
    private final Symlink symlink;
    private final FileMetadataAccessor metadata;
    private final StatStatistics.Collector statisticsCollector;

    public GenericFileSystem(
        FileModeMutator chmod,
        FileModeAccessor stat,
        Symlink symlink,
        FileMetadataAccessor metadata,
        StatStatistics.Collector statisticsCollector,
        TemporaryFileProvider temporaryFileProvider
    ) {
        this.metadata = metadata;
        this.stat = stat;
        this.symlink = symlink;
        this.chmod = chmod;
        this.canCreateSymbolicLink = symlink.isSymlinkCreationSupported();
        this.statisticsCollector = statisticsCollector;
        this.temporaryFileProvider = temporaryFileProvider;
    }

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
        statisticsCollector.reportUnixModeQueried();
        try {
            return stat.getUnixMode(f);
        } catch (Exception e) {
            throw new FileException(String.format("Could not get file mode for '%s'.", f), e);
        }
    }

    @Override
    public FileMetadata stat(File f) throws FileException {
        statisticsCollector.reportFileStated();
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
        File file = temporaryFileProvider.createTemporaryFile("gradle_fs_probing", null);
        Files.asCharSink(file, Charsets.UTF_8).write(content);
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
        return file.exists() && Files.asCharSource(file, Charsets.UTF_8).readFirstLine().equals(content);
    }

    private void checkJavaIoTmpDirExists() throws IOException {
        @SuppressWarnings("deprecation")
        File dir = new File(SystemProperties.getInstance().getJavaIoTmpDir());
        if (!dir.exists()) {
            throw new IOException("java.io.tmpdir is set to a directory that doesn't exist: " + dir);
        }
    }

    static final class Factory {
        private final FileMetadataAccessor fileMetadataAccessor;
        private final StatStatistics.Collector statisticsCollector;
        private final TemporaryFileProvider temporaryFileProvider;

        @Inject
        Factory(FileMetadataAccessor fileMetadataAccessor, StatStatistics.Collector statisticsCollector, TemporaryFileProvider temporaryFileProvider) {
            this.fileMetadataAccessor = fileMetadataAccessor;
            this.statisticsCollector = statisticsCollector;
            this.temporaryFileProvider = temporaryFileProvider;
        }

        GenericFileSystem create(FileModeMutator chmod, FileModeAccessor stat, Symlink symlink) {
            return new GenericFileSystem(chmod, stat, symlink, fileMetadataAccessor, statisticsCollector, temporaryFileProvider);
        }
    }
}
