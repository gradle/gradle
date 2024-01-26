/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.jvm.toolchain.internal.install;

import com.google.common.io.Files;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.FilenameUtils;
import org.apache.tools.bzip2.CBZip2InputStream;
import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.tasks.bundling.Compression;
import org.gradle.cache.FileLock;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.internal.filelock.DefaultLockOptions;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.internal.file.PathTraversalChecker;
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata;
import org.gradle.internal.jvm.inspection.JvmMetadataDetector;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.internal.InstallationLocation;
import org.gradle.jvm.toolchain.internal.JvmInstallationMetadataMatcher;
import org.gradle.util.internal.GFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static java.lang.String.format;

public class JdkCacheDirectory {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdkCacheDirectory.class);
    private static final String MARKER_FILE = "provisioned.ok";
    private static final String MAC_OS_JAVA_HOME_FOLDER = "Contents/Home";

    private final FileOperations operations;
    private final File jdkDirectory;
    private final FileLockManager lockManager;

    private final JvmMetadataDetector detector;

    @Inject
    public JdkCacheDirectory(GradleUserHomeDirProvider homeDirProvider, FileOperations operations, FileLockManager lockManager, JvmMetadataDetector detector) {
        this.operations = operations;
        this.jdkDirectory = new File(homeDirProvider.getGradleUserHomeDirectory(), "jdks");
        this.lockManager = lockManager;
        this.detector = detector;
        jdkDirectory.mkdir();
    }

    public Set<File> listJavaHomes() {
        final File[] candidates = jdkDirectory.listFiles();
        if (candidates != null) {
            return Arrays.stream(candidates)
                    .flatMap(this::allMarkedLocations)
                    .map(this::getJavaHome)
                    .collect(Collectors.toSet());
        }
        return Collections.emptySet();
    }

    private Stream<File> allMarkedLocations(File candidate) {
        if (isMarkedLocation(candidate)) {
            return Stream.of(candidate);
        }

        File[] subFolders = candidate.listFiles();
        if (subFolders == null) {
            return Stream.empty();
        }

        return Arrays.stream(subFolders).filter(this::isMarkedLocation);
    }

    private boolean isMarkedLocation(File candidate) {
        return candidate.isDirectory() && new File(candidate, MARKER_FILE).exists();
    }

    private File getJavaHome(File markedLocation) {
        if (OperatingSystem.current().isMacOsX()) {
            if (new File(markedLocation, MAC_OS_JAVA_HOME_FOLDER).exists()) {
                return new File(markedLocation, MAC_OS_JAVA_HOME_FOLDER);
            }

            File[] subfolders = markedLocation.listFiles(File::isDirectory);
            if (subfolders != null) {
                for(File subfolder : subfolders) {
                    if (new File(subfolder, MAC_OS_JAVA_HOME_FOLDER).exists()) {
                        return new File(subfolder, MAC_OS_JAVA_HOME_FOLDER);
                    }
                }
            }
        }

        return markedLocation;
    }

    /**
     * Unpacks and installs the given JDK archive. Returns a file pointing to the java home directory.
     */
    public File provisionFromArchive(JavaToolchainSpec spec, File jdkArchive, URI uri) {
        final File[] unpackFolder = new File[1];
        final File[] installFolder = new File[1];
        try {
            unpackFolder[0] = unpack(jdkArchive);

            //put the marker file in the unpack folder from where it will get in its proper place
            //when the contents are copied to the installation folder
            File markedLocation = markLocationInsideFolder(unpackFolder[0]);

            //probe unpacked installation for its metadata
            JvmInstallationMetadata metadata = getMetadata(markedLocation);

            validateMetadataMatchesSpec(spec, uri, metadata);

            String installFolderName = getInstallFolderName(metadata);
            installFolder[0] = new File(jdkDirectory, installFolderName);

            //make sure the install folder is empty
            checkInstallFolderForLeftoverContent(installFolder[0], uri, spec, metadata);
            operations.delete(installFolder[0]);

            //copy content of unpack folder to install folder, including the marker file
            copyDirectoryWithSymlinks(unpackFolder[0].toPath(), installFolder[0].toPath());

            LOGGER.info("Installed toolchain from {} into {}", uri, installFolder[0]);
            return getJavaHome(markedLocation(installFolder[0]));
        } catch (Throwable t) {
            // provisioning failed, clean up leftovers
            if (installFolder[0] != null) {
                operations.delete(installFolder[0]);
            }
            throw t;
        } finally {
            // clean up temporary unpack folder, regardless if provisioning succeeded or not
            if (unpackFolder[0] != null) {
                operations.delete(unpackFolder[0]);
            }
        }
    }


    public void copyDirectoryWithSymlinks(Path sourcePath, Path targetPath) {
        try {
            java.nio.file.Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path targetDir = targetPath.resolve(sourcePath.relativize(dir));
                    java.nio.file.Files.createDirectories(targetDir);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path newLocation = targetPath.resolve(sourcePath.relativize(file));
                    if (attrs.isSymbolicLink()) {
                        Path symlinkTarget = java.nio.file.Files.readSymbolicLink(file);
                        java.nio.file.Files.createSymbolicLink(newLocation, symlinkTarget);
                    } else {
                        java.nio.file.Files.copy(file, newLocation, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void checkInstallFolderForLeftoverContent(File installFolder, URI uri, JavaToolchainSpec spec, JvmInstallationMetadata metadata) {
        if (!installFolder.exists()) {
            return; //install folder doesn't even exist
        }

        File[] filesInInstallFolder = installFolder.listFiles();
        if (filesInInstallFolder == null || filesInInstallFolder.length == 0) {
            return; //no files in install folder
        }

        File markerLocation = markedLocation(installFolder);
        if (!isMarkedLocation(markerLocation)) {
            return; //no marker found
        }

        String leftoverMetadata;
        try {
            leftoverMetadata = getMetadata(markerLocation).toString();
        } catch (Exception e) {
            LOGGER.debug("Failed determining metadata of installation leftover", e);
            leftoverMetadata = "Could not be determined due to: " + e.getMessage();
        }
        LOGGER.warn("While provisioning Java toolchain from '{}' to satisfy spec '{}' (with metadata '{}'), " +
                "leftover content (with metadata '{}') was found in the install folder '{}'. " +
                "The existing installation will be replaced by the new download.",
                uri, spec, metadata, leftoverMetadata, installFolder);
    }

    private JvmInstallationMetadata getMetadata(File markedLocation) {
        File javaHome = getJavaHome(markedLocation);

        JvmInstallationMetadata metadata = detector.getMetadata(new InstallationLocation(javaHome, "provisioned toolchain", true));
        if (!metadata.isValidInstallation()) {
            throw new GradleException("Provisioned toolchain '" + javaHome + "' could not be probed: " + metadata.getErrorMessage(), metadata.getErrorCause());
        }

        return metadata;
    }

    private File markLocationInsideFolder(File unpackedInstallationFolder) {
        File markedLocation = markedLocation(unpackedInstallationFolder);
        markAsReady(markedLocation);
        return markedLocation;
    }

    private static void validateMetadataMatchesSpec(JavaToolchainSpec spec, URI uri, JvmInstallationMetadata metadata) {
        if (!new JvmInstallationMetadataMatcher(spec).test(metadata)) {
            throw new GradleException("Toolchain provisioned from '" + uri + "' doesn't satisfy the specification: " + spec.getDisplayName() + ".");
        }
    }

    private static String getInstallFolderName(JvmInstallationMetadata metadata) {
        String vendor = metadata.getJvmVendor();
        if (vendor == null || vendor.isEmpty()) {
            vendor = metadata.getVendor().getRawVendor();
        }
        String version = metadata.getLanguageVersion().getMajorVersion();
        String architecture = metadata.getArchitecture();
        String os = OperatingSystem.current().getFamilyName();
        return String.format("%s-%s-%s-%s", vendor, version, architecture, os)
                .replaceAll("[^a-zA-Z0-9\\-]", "_")
                .toLowerCase();
    }

    private File unpack(File jdkArchive) {
        String unpackFolderName = getNameWithoutExtension(jdkArchive);
        final File unpackFolder = new File(jdkDirectory, unpackFolderName);
        if (!unpackFolder.exists()) {
            unpack(jdkArchive, unpackFolder);
        }

        return unpackFolder;
    }

    private void unpack(File jdkArchive, File unpackFolder) {
        final String extension = FilenameUtils.getExtension(jdkArchive.getName());
        if (Objects.equals(extension, "zip")) {
            unzip(jdkArchive, unpackFolder);
        } else {
            untar(jdkArchive, unpackFolder);
        }
    }

    private File markedLocation(File unpackFolder) {
        File[] content = unpackFolder.listFiles();
        if (content == null) {
            //can't happen, the installation location is a directory, we have created it
            throw new RuntimeException("Programming error");
        }

        //mark the first directory since there should be only one
        for (File file : content) {
            if (file.isDirectory()) {
                return file;
            }
        }

        //there were no sub-directories in the installation location
        return unpackFolder;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private File markAsReady(File destination) {
        try {
            new File(destination, MARKER_FILE).createNewFile();
            return destination;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create .ok file", e);
        }
    }

    private FileTree asFileTree(File jdkArchive) {
        final String extension = FilenameUtils.getExtension(jdkArchive.getName());
        if (Objects.equals(extension, "zip")) {
            return operations.zipTree(jdkArchive);
        }
        return operations.tarTree(operations.getResources().gzip(jdkArchive));
    }

    private void unzip(File jdkZipArchive, File targetFolder) {
        try (ZipFile zip = new ZipFile(jdkZipArchive)) {
            Enumeration<ZipArchiveEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                File targetFile = new File(targetFolder, PathTraversalChecker.safePathName(entry.getName()));
                if (entry.isDirectory()) {
                    GFileUtils.mkdirs(targetFile);
                } else if (entry.isUnixSymlink()) {
                    String target = zip.getUnixSymlink(entry);
                    java.nio.file.Files.createSymbolicLink(targetFile.toPath(), Paths.get(target));
                } else {
                    GFileUtils.mkdirs(targetFile.getParentFile());
                    try (InputStream inputStream = zip.getInputStream(entry)) {
                        java.nio.file.Files.copy(inputStream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (GradleException e) {
            throw e; // Gradle exceptions are already meant to be human-readable, so just rethrow it
        } catch (Exception e) {
            throw new GradleException(format("Cannot expand %s.", jdkZipArchive), e);
        }
    }

    private void untar(File jdkTarArchive, File targetFolder) {
        try (TarArchiveInputStream tar = new TarArchiveInputStream(maybeUncompressInputStream(jdkTarArchive))) {
            TarArchiveEntry entry;
            while ((entry = (TarArchiveEntry) tar.getNextEntry()) != null) {
                File targetFile = new File(targetFolder, PathTraversalChecker.safePathName(entry.getName()));
                if (entry.isDirectory()) {
                    GFileUtils.mkdirs(targetFile);
                } else if (entry.isSymbolicLink()) {
                    String target = entry.getLinkName();
                    java.nio.file.Files.createSymbolicLink(targetFile.toPath(), Paths.get(target));
                } else {
                    GFileUtils.mkdirs(targetFile.getParentFile());
                    java.nio.file.Files.copy(tar, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (GradleException e) {
            throw e; // Gradle exceptions are already meant to be human-readable, so just rethrow it
        } catch (Exception e) {
            throw new GradleException(format("Cannot expand %s.", jdkTarArchive), e);
        }
    }

    InputStream maybeUncompressInputStream(File inputFile) throws IOException {
        InputStream inputStream = java.nio.file.Files.newInputStream(inputFile.toPath());
        String ext = FilenameUtils.getExtension(inputFile.getName());
        if (Compression.BZIP2.getSupportedExtensions().contains(ext)) {
            // CBZip2InputStream expects the opening "BZ" to be skipped
            byte[] skip = new byte[2];
            inputStream.read(skip);
            return new CBZip2InputStream(inputStream);
        } else if (Compression.GZIP.getSupportedExtensions().contains(ext)) {
            return new GZIPInputStream(inputStream);
        } else {
            // Unrecognized extension
            return inputStream;
        }
    }

    public FileLock acquireWriteLock(File destinationFile, String operationName) {
        return lockManager.lock(destinationFile, DefaultLockOptions.mode(FileLockManager.LockMode.Exclusive), destinationFile.getName(), operationName);
    }

    public File getDownloadLocation() {
        return jdkDirectory;
    }

    private static String getNameWithoutExtension(File file) {
        //remove all extensions, for example for xxx.tar.gz files only xxx should be left
        String output = file.getName();
        String input;
        do {
            input = output;
            output = Files.getNameWithoutExtension(input);
        } while (!input.equals(output));
        return output;
    }

}
