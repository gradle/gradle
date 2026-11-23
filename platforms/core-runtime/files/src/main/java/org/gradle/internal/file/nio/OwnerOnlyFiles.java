/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.file.nio;

import com.google.common.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.Set;

/**
 * Creates files and directories that only their owner can access, for content other local users must not read.
 *
 * <p>On POSIX file systems the permissions are applied by the call that creates the file or directory.
 * Restricting them afterwards would not be enough: a descriptor another user opens while the permissions are
 * still the umask default stays usable after the change.
 *
 * <p>On other file systems, such as Windows, a new file inherits the access rules of its directory atomically,
 * so files are created as usual.
 */
public final class OwnerOnlyFiles {
    private static final Set<PosixFilePermission> OWNER_ONLY_FILE_PERMISSIONS = EnumSet.of(
        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    private static final Set<PosixFilePermission> OWNER_ONLY_DIRECTORY_PERMISSIONS = EnumSet.of(
        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
    private static final FileAttribute<Set<PosixFilePermission>> OWNER_ONLY_FILE = PosixFilePermissions.asFileAttribute(OWNER_ONLY_FILE_PERMISSIONS);
    private static final FileAttribute<Set<PosixFilePermission>> OWNER_ONLY_DIRECTORY = PosixFilePermissions.asFileAttribute(OWNER_ONLY_DIRECTORY_PERMISSIONS);

    private OwnerOnlyFiles() {
    }

    /**
     * Creates the directory, accessible only to its owner, and its missing parents with default permissions.
     * An existing directory is restricted to its owner.
     */
    public static void createDirectory(File directory) throws IOException {
        Path path = directory.toPath().toAbsolutePath();
        try {
            createOwnerOnlyDirectory(path);
        } catch (IOException e) {
            throw new IOException(String.format("Failed to create directory '%s'", path), e);
        }
    }

    private static void createOwnerOnlyDirectory(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent == null) {
            return;
        }
        createDirectoryFollowingLinks(parent);
        if (!isPosix(parent)) {
            createDirectoryFollowingLinks(path);
            return;
        }
        try {
            Files.createDirectory(path, OWNER_ONLY_DIRECTORY);
        } catch (FileAlreadyExistsException e) {
            if (!Files.isDirectory(path)) {
                throw e;
            }
            Files.setPosixFilePermissions(path, OWNER_ONLY_DIRECTORY_PERMISSIONS);
        }
    }

    /**
     * Creates the directory, accessible only to its owner, or verifies that the existing one can be trusted.
     * Missing parents are created with default permissions.
     *
     * @throws UntrustedDirectoryException when something exists at that location that cannot be trusted
     */
    public static void createOrVerifyPrivateDirectory(File directory) throws IOException {
        Path path = directory.toPath().toAbsolutePath();
        Path parent = path.getParent();
        if (parent == null) {
            verifyPrivateDirectory(path, currentUser(path));
            return;
        }
        createDirectoryFollowingLinks(parent);
        if (!isPosix(parent)) {
            try {
                Files.createDirectory(path);
            } catch (FileAlreadyExistsException e) {
                verifyPrivateDirectory(path, currentUser(parent));
            }
            return;
        }
        try {
            Files.createDirectory(path, OWNER_ONLY_DIRECTORY);
        } catch (FileAlreadyExistsException e) {
            // Another user may have created it: only verify, as changing it would not undo what they already did with it
            verifyPrivateDirectory(path, currentUser(parent));
        }
    }

    @VisibleForTesting
    static void verifyPrivateDirectory(Path path, UserPrincipal currentUser) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (attributes.isSymbolicLink()) {
            throw new UntrustedDirectoryException(path, "it is a symbolic link");
        }
        if (!attributes.isDirectory()) {
            throw new UntrustedDirectoryException(path, "it is not a directory");
        }
        if (!Files.getOwner(path, LinkOption.NOFOLLOW_LINKS).equals(currentUser)) {
            throw new UntrustedDirectoryException(path, "it is owned by another user");
        }
        PosixFileAttributeView posixView = Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (posixView == null) {
            return;
        }
        Set<PosixFilePermission> permissions = posixView.readAttributes().permissions();
        if (permissions.contains(PosixFilePermission.GROUP_WRITE) || permissions.contains(PosixFilePermission.OTHERS_WRITE)) {
            throw new UntrustedDirectoryException(path, "it is writable by other users");
        }
    }

    // The owner of a file this process creates, which may differ from the user named by the user.name system property
    private static UserPrincipal currentUser(Path directory) throws IOException {
        Path probe = Files.createTempFile(directory, ".owner", null);
        try {
            return Files.getOwner(probe, LinkOption.NOFOLLOW_LINKS);
        } finally {
            Files.deleteIfExists(probe);
        }
    }

    /**
     * Opens a new file accessible only to its owner, replacing an existing one.
     *
     * <p>The new file is created next to it and moved over it atomically, so the file exists at all times,
     * and a descriptor opened on the old file cannot read the new content.
     */
    public static OutputStream newOutputStream(File file) throws IOException {
        Path directory = file.getAbsoluteFile().getParentFile().toPath();
        // Written in place on Windows, which cannot replace a file another process holds open.
        if (!isPosix(directory)) {
            return new FileOutputStream(file);
        }
        Path tempFile = createOwnerOnlyTempFile(directory, ".new", ".tmp");
        try {
            Files.move(tempFile, file.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.deleteIfExists(tempFile);
            throw e;
        }
        return new FileOutputStream(file);
    }

    /**
     * Creates a new empty file with a unique name in the directory, accessible only to its owner.
     */
    public static File createTempFile(File directory, String prefix, @Nullable String suffix) throws IOException {
        Path path = directory.toPath();
        if (!isPosix(path)) {
            return Files.createTempFile(path, prefix, suffix).toFile();
        }
        return createOwnerOnlyTempFile(path, prefix, suffix).toFile();
    }

    private static Path createOwnerOnlyTempFile(Path posixDirectory, String prefix, @Nullable String suffix) throws IOException {
        return Files.createTempFile(posixDirectory, prefix, suffix, OWNER_ONLY_FILE);
    }

    // Before JDK 20 and 17.0.14, Files.createDirectories throws FileAlreadyExistsException for a symlink to a directory (JDK-8294193)
    private static void createDirectoryFollowingLinks(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            Files.createDirectories(directory);
        }
    }

    private static boolean isPosix(Path existingDirectory) {
        try {
            return Files.getFileStore(existingDirectory).supportsFileAttributeView("posix");
        } catch (IOException e) {
            // Workaround for https://bugs.openjdk.org/browse/JDK-8232092
            return existingDirectory.getFileSystem().supportedFileAttributeViews().contains("posix");
        }
    }

    /**
     * Thrown when an existing directory cannot be trusted, as other users may control it.
     */
    public static final class UntrustedDirectoryException extends IOException {
        private final String reason;

        UntrustedDirectoryException(Path directory, String reason) {
            super(String.format("Directory '%s' cannot be trusted: %s", directory, reason));
            this.reason = reason;
        }

        /**
         * Why the directory cannot be trusted, such as "it is a symbolic link".
         */
        public String getReason() {
            return reason;
        }
    }
}
