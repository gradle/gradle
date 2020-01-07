package org.gradle.caching.internal.packaging.impl;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Helps dealing with file permissions.
 */
public class PermissionUtils {

    /**
     * XXX: When using standard Java permissions, we treat 'owner' and 'group' equally and give no
     * permissions for 'others'.
     */
    private enum StandardFilePermission {
        EXECUTE(0110), WRITE(0220), READ(0440);

        private int mode;

        StandardFilePermission(int mode) {
            this.mode = mode;
        }
    }

    private static Map<PosixFilePermission, Integer> posixPermissionToInteger = new EnumMap<>(PosixFilePermission.class);

    static {
        posixPermissionToInteger.put(PosixFilePermission.OWNER_EXECUTE, 0100);
        posixPermissionToInteger.put(PosixFilePermission.OWNER_WRITE, 0200);
        posixPermissionToInteger.put(PosixFilePermission.OWNER_READ, 0400);

        posixPermissionToInteger.put(PosixFilePermission.GROUP_EXECUTE, 0010);
        posixPermissionToInteger.put(PosixFilePermission.GROUP_WRITE, 0020);
        posixPermissionToInteger.put(PosixFilePermission.GROUP_READ, 0040);

        posixPermissionToInteger.put(PosixFilePermission.OTHERS_EXECUTE, 0001);
        posixPermissionToInteger.put(PosixFilePermission.OTHERS_WRITE, 0002);
        posixPermissionToInteger.put(PosixFilePermission.OTHERS_READ, 0004);
    }

    public static void setPermissions(File file, int mode) throws IOException {
        if (IS_POSIX) {
            Files.setPosixFilePermissions(file.toPath(), PermissionUtils.permissions(mode));
        } else if (IS_DOS) {
            Files.getFileAttributeView(file.toPath(), DosFileAttributeView.class)
                .setReadOnly((mode & posixPermissionToInteger.get(PosixFilePermission.OWNER_WRITE)) == 0);
        }
    }

    private static Set<PosixFilePermission> permissions(int mode) {
        Set<PosixFilePermission> result = new HashSet<>();
        for (Map.Entry<PosixFilePermission, Integer> entry : posixPermissionToInteger.entrySet()) {
            if ((mode & entry.getValue()) != 0) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * Get file permissions in octal mode, e.g. 0755.
     * <p>
     * Note: it uses `java.nio.file.attribute.PosixFilePermission` if OS supports this, otherwise reverts to
     * using standard Java file operations, e.g. `java.io.File#canExecute()`. In the first case permissions will
     * be precisely as reported by the OS, in the second case 'owner' and 'group' will have equal permissions and
     * 'others' will have no permissions, e.g. if file on Windows OS is `read-only` permissions will be `0550`.
     *
     * @throws NullPointerException     if file is null.
     * @throws IllegalArgumentException if file does not exist.
     */
    public static int permissions(File f) {
        if (f == null) {
            throw new NullPointerException("File is null.");
        }
        if (!f.exists()) {
            throw new IllegalArgumentException("File " + f + " does not exist.");
        }

        return IS_POSIX ? posixPermissions(f) : (IS_DOS ? dosPermissions(f) : defaultPermissions(f));
    }

    private static int defaultPermissions(File f) {
        return f.isDirectory() ? 0755 : 0644;
    }

    private static final boolean IS_POSIX = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    private static final boolean IS_DOS = FileSystems.getDefault().supportedFileAttributeViews().contains("dos");

    private static int posixPermissions(File f) {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(f.toPath());
            int number = 0;
            for (Map.Entry<PosixFilePermission, Integer> entry : posixPermissionToInteger.entrySet()) {
                if (permissions.contains(entry.getKey())) {
                    number += entry.getValue();
                }
            }
            return number;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Set<StandardFilePermission> readDosPermissions(File f) {
        Set<StandardFilePermission> permissions = EnumSet.allOf(StandardFilePermission.class);
        if (readDosFileAttributes(f).isReadOnly()) {
            permissions.remove(StandardFilePermission.WRITE);
        }
        return permissions;
    }

    private static DosFileAttributes readDosFileAttributes(File f) {
        try {
            return Files.getFileAttributeView(f.toPath(), DosFileAttributeView.class).readAttributes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Integer dosPermissions(File f) {
        int number = 0;
        Set<StandardFilePermission> permissions = readDosPermissions(f);
        for (StandardFilePermission permission : permissions) {
            number += permission.mode;
        }
        return number;
    }
}
