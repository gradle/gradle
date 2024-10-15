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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.Files;
import org.apache.commons.io.FilenameUtils;
import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.temp.GradleUserHomeTemporaryFileProvider;
import org.gradle.cache.FileLock;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.internal.filelock.DefaultLockOptions;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.internal.RenderingUtils;
import org.gradle.internal.jvm.inspection.JavaInstallationCapability;
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata;
import org.gradle.internal.jvm.inspection.JvmMetadataDetector;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.internal.InstallationLocation;
import org.gradle.jvm.toolchain.internal.JdkCacheDirectory;
import org.gradle.jvm.toolchain.internal.JvmInstallationMetadataMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultJdkCacheDirectory implements JdkCacheDirectory {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultJdkCacheDirectory.class);
    /**
     * Marker file used by Gradle 8.8 and earlier to indicate that a JDK has been provisioned. This is a flaky marker, as it may appear
     * before the JDK is fully provisioned, causing faulty detection of the JDK. It is replaced by {@value #MARKER_FILE}.
     */
    @VisibleForTesting
    static final String LEGACY_MARKER_FILE = "provisioned.ok";
    @VisibleForTesting
    static final String MARKER_FILE = ".ready";
    private static final String MAC_OS_JAVA_HOME_FOLDER = "Contents/Home";

    private static final class UnpackedRoot {
        private final File dir;
        private final JvmInstallationMetadata metadata;

        private UnpackedRoot(File dir, JvmInstallationMetadata metadata) {
            this.dir = dir;
            this.metadata = metadata;
        }
    }

    private final FileOperations operations;
    private final File jdkDirectory;
    private final FileLockManager lockManager;

    private final JvmMetadataDetector detector;
    // Specifically requesting the GradleUserHomeTemporaryFileProvider to ensure that the temporary files are created on the same file system as the target directory
    // This is a prerequisite for atomic moves in most cases, which are used in the provisionFromArchive method
    private final GradleUserHomeTemporaryFileProvider temporaryFileProvider;

    @Inject
    public DefaultJdkCacheDirectory(
        GradleUserHomeDirProvider homeDirProvider,
        FileOperations operations,
        FileLockManager lockManager,
        JvmMetadataDetector detector,
        GradleUserHomeTemporaryFileProvider temporaryFileProvider
    ) {
        this.operations = operations;
        this.jdkDirectory = new File(homeDirProvider.getGradleUserHomeDirectory(), "jdks");
        this.lockManager = lockManager;
        this.detector = detector;
        this.temporaryFileProvider = temporaryFileProvider;
    }

    @Override
    public Set<File> listJavaHomes() {
        final File[] candidates = jdkDirectory.listFiles();
        if (candidates != null) {
            return Arrays.stream(candidates)
                    .filter(this::isMarkedLocation)
                    .map(this::getJavaHome)
                    .collect(Collectors.toSet());
        }
        return Collections.emptySet();
    }

    private boolean isMarkedLocation(File candidate) {
        return new File(candidate, MARKER_FILE).exists();
    }

    private File getJavaHome(File location) {
        if (OperatingSystem.current().isMacOsX()) {
            if (new File(location, MAC_OS_JAVA_HOME_FOLDER).exists()) {
                return new File(location, MAC_OS_JAVA_HOME_FOLDER);
            }

            File[] subfolders = location.listFiles(File::isDirectory);
            if (subfolders != null) {
                for(File subfolder : subfolders) {
                    if (new File(subfolder, MAC_OS_JAVA_HOME_FOLDER).exists()) {
                        return new File(subfolder, MAC_OS_JAVA_HOME_FOLDER);
                    }
                }
            }
        }

        return location;
    }

    /**
     * Unpacks and installs the given JDK archive. Returns a file pointing to the java home directory.
     */
    public File provisionFromArchive(JavaToolchainSpec spec, File jdkArchive, URI uri) throws IOException {
        // Unpack into temporary directory (but on same file system as our target directory location)
        File unpackFolder = unpack(jdkArchive);
        try {
            // Get the folder that is the real root of the unpacked JDK, skipping any archive root folder
            UnpackedRoot unpackedRoot = determineUnpackedRoot(unpackFolder);

            validateMetadataMatchesSpec(spec, uri, unpackedRoot.metadata);

            // Check our target directory, to see if anything exists there, and if so, is it marked as ready?
            File installFolder = new File(jdkDirectory, getInstallFolderName(unpackedRoot.metadata));
            if (!installFolder.getParentFile().mkdirs() && !installFolder.getParentFile().isDirectory()) {
                throw new IOException("Failed to create install parent directory: " + installFolder.getParentFile());
            }
            // Before checking existence, lock the install folder name to prevent concurrent installations
            // An extra string is added to prevent the lock from being created inside the install folder
            try (FileLock ignored = acquireWriteLock(new File(installFolder.getParentFile(), installFolder.getName() + ".reserved"), "Provisioning JDK from " + uri)) {
                if (installFolder.exists()) {
                    if (isMarkedLocation(installFolder)) {
                        LOGGER.info("Toolchain from {} already installed at {}", uri, installFolder);
                        return getJavaHome(installFolder);
                    } else {
                        // This can happen if atomic moves are unsupported, and the JVM is forcibly killed during the copy
                        LOGGER.info("Found partially installed toolchain at {}, overwriting with toolchain from {}", installFolder, uri);
                        operations.delete(installFolder);
                    }
                }

                // Move the unpacked root to the install location, atomically if possible
                try {
                    java.nio.file.Files.move(unpackedRoot.dir.toPath(), installFolder.toPath(), StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    // In theory, we should never hit this code, but some more obscure file systems or OSes may not support atomic moves
                    LOGGER.info("Failed to use an atomic move for unpacked JDK from {} to {}. Will try to copy instead.", unpackedRoot.dir, installFolder, e);
                    try {
                        operations.copy(copySpec -> {
                            copySpec.from(unpackedRoot.dir);
                            copySpec.into(installFolder);
                        });
                    } catch (Throwable t) {
                        deleteWithoutThrowing(t, installFolder);
                        throw t;
                    }
                }

                // Now that the JDK is installed, mark it as ready
                try {
                    markAsReady(installFolder);
                } catch (Throwable t) {
                    deleteWithoutThrowing(t, installFolder);
                    throw t;
                }
            }
            return getJavaHome(installFolder);
        } finally {
            try {
                operations.delete(unpackFolder);
            } catch (Throwable t) {
                // Prevent Throwables from masking the original exception
                LOGGER.warn("Failed to delete temporary unpack folder: " + unpackFolder, t);
            }
        }
    }

    private void deleteWithoutThrowing(Throwable t, File installFolder) {
        try {
            operations.delete(installFolder);
        } catch (Throwable t2) {
            t.addSuppressed(t2);
        }
    }

    private UnpackedRoot determineUnpackedRoot(File unpackFolder) {
        JvmInstallationMetadata uncheckedMetadata = getUncheckedMetadata(unpackFolder);
        if (uncheckedMetadata.isValidInstallation()) {
            return new UnpackedRoot(unpackFolder, uncheckedMetadata);
        }
        File[] subFolders = unpackFolder.listFiles(File::isDirectory);
        if (subFolders == null) {
            throw new IllegalStateException("Unpacked JDK archive is not a directory: " + unpackFolder);
        }
        for (File subFolder : subFolders) {
            uncheckedMetadata = getUncheckedMetadata(subFolder);
            if (uncheckedMetadata.isValidInstallation()) {
                return new UnpackedRoot(subFolder, uncheckedMetadata);
            }
        }
        throw new IllegalStateException("Unpacked JDK archive does not contain a Java home: " + unpackFolder, uncheckedMetadata.getErrorCause());
    }

    private JvmInstallationMetadata getUncheckedMetadata(File root) {
        File javaHome = getJavaHome(root);
        return detector.getMetadata(InstallationLocation.autoProvisioned(javaHome, "provisioned toolchain"));
    }

    private static final String JDK_CAPABILITIES_DISPLAY = JavaInstallationCapability.JDK_CAPABILITIES.stream()
            .map(cap -> "the " + cap.toDisplayName())
            .collect(RenderingUtils.oxfordJoin("and"));

    /**
     * Validates that the metadata of the provisioned JDK matches the specification. This also requires {@link JavaInstallationCapability#JDK_CAPABILITIES} to be present.
     *
     * @param spec the specification to validate against
     * @param uri the URI of the JDK archive
     * @param metadata the metadata of the provisioned JDK
     */
    private static void validateMetadataMatchesSpec(JavaToolchainSpec spec, URI uri, JvmInstallationMetadata metadata) {
        if (!new JvmInstallationMetadataMatcher(spec, JavaInstallationCapability.JDK_CAPABILITIES).test(metadata)) {
            // Log the metadata for debugging purposes
            LOGGER.info("Provisioned JDK from '{}' does not satisfy the specification {} with metadata {} and capabilities {}", uri, spec.getDisplayName(), metadata, metadata.getCapabilities());
            // Make a readable version of the capabilities for the
            throw new GradleException("Toolchain provisioned from '" + uri + "' doesn't satisfy the specification: " + spec.getDisplayName() + " and must have " + JDK_CAPABILITIES_DISPLAY + ".");
        }
    }

    public static String getInstallFolderName(JvmInstallationMetadata metadata) {
        String vendor = metadata.getJvmVendor();
        if (vendor == null || vendor.isEmpty()) {
            vendor = metadata.getVendor().getRawVendor();
        }
        int version = metadata.getJavaMajorVersion();
        String architecture = metadata.getArchitecture();
        String os = OperatingSystem.current().getFamilyName();
        return String.format("%s-%d-%s-%s", vendor, version, architecture, os)
                .replaceAll("[^a-zA-Z0-9\\-]", "_")
                .toLowerCase(Locale.ROOT) + ".2";
    }

    private File unpack(File jdkArchive) {
        final FileTree fileTree = asFileTree(jdkArchive);
        String unpackFolderName = getNameWithoutExtension(jdkArchive);
        final File unpackFolder = temporaryFileProvider.createTemporaryDirectory(unpackFolderName, null, "jdks");
        unpackFolder.deleteOnExit();
        operations.copy(spec -> {
            spec.from(fileTree);
            spec.into(unpackFolder);
            spec.setDuplicatesStrategy(DuplicatesStrategy.WARN);
        });

        return unpackFolder;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void markAsReady(File root) {
        try {
            // Create the legacy marker so that older Gradle versions can use this JDK as well.
            new File(root, LEGACY_MARKER_FILE).createNewFile();
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create " + LEGACY_MARKER_FILE + " file", e);
        }
        try {
            new File(root, MARKER_FILE).createNewFile();
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create " + MARKER_FILE + " file", e);
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
