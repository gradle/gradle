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
import org.apache.commons.io.FilenameUtils;
import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.cache.FileLock;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.internal.filelock.LockOptionsBuilder;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata;
import org.gradle.internal.jvm.inspection.JvmMetadataDetector;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.internal.InstallationLocation;
import org.gradle.jvm.toolchain.internal.JavaToolchainMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
            operations.copy(copySpec -> {
                copySpec.from(unpackFolder[0]);
                copySpec.into(installFolder[0]);
            });

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
        if (!new JavaToolchainMatcher(spec).test(metadata)) {
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
        final FileTree fileTree = asFileTree(jdkArchive);
        String unpackFolderName = getNameWithoutExtension(jdkArchive);
        final File unpackFolder = new File(jdkDirectory, unpackFolderName);
        if (!unpackFolder.exists()) {
            operations.copy(spec -> {
                spec.from(fileTree);
                spec.into(unpackFolder);
                spec.setDuplicatesStrategy(DuplicatesStrategy.WARN);
            });
        }

        return unpackFolder;
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

    public FileLock acquireWriteLock(File destinationFile, String operationName) {
        return lockManager.lock(destinationFile, LockOptionsBuilder.mode(FileLockManager.LockMode.Exclusive), destinationFile.getName(), operationName);
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
