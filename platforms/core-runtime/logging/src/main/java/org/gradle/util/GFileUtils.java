/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.util;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.IoActions;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.util.internal.CollectionUtils;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.Checksum;

/**
 * This class is only here to maintain binary compatibility with existing plugins.
 * <p>
 * Plugins should prefer java.io, java.nio or external packages over this class.
 *
 * @deprecated Will be removed in Gradle 9.0.
 */
@Deprecated
public class GFileUtils {

    private static void logDeprecation() {
        DeprecationLogger.deprecateType(GFileUtils.class)
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(7, "org_gradle_util_reports_deprecations")
            .nagUser();
    }

    public GFileUtils() {
        logDeprecation();
    }

    public static FileInputStream openInputStream(File file) {
        logDeprecation();
        try {
            return FileUtils.openInputStream(file);
        } catch (IOException e) {
            throw new RuntimeException("Problems opening file input stream for file: " + file, e);
        }
    }

    /**
     * Ensures that the given file (or directory) is marked as modified. If the file
     * (or directory) does not exist, a new file is created.
     */
    public static void touch(File file) {
        logDeprecation();
        try {
            if (!file.createNewFile()) {
                touchExistingInternal(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Ensures that the given file (or directory) is marked as modified. The file
     * (or directory) must exist.
     */
    public static void touchExisting(File file) {
        logDeprecation();
        touchExistingInternal(file);
    }

    private static void touchExistingInternal(File file) {
        try {
            Files.setLastModifiedTime(file.toPath(), FileTime.fromMillis(System.currentTimeMillis()));
        } catch (IOException e) {
            if (file.isFile() && file.length() == 0) {
                // On Linux, users cannot touch files they don't own but have write access to
                // because the JDK uses futimes() instead of futimens() [note the 'n'!]
                // see https://github.com/gradle/gradle/issues/7873
                touchFileByWritingEmptyByteArray(file);
            } else {
                throw new UncheckedIOException("Could not update timestamp for " + file, e);
            }
        }
    }

    private static void touchFileByWritingEmptyByteArray(File file) {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            out.write(ArrayUtils.EMPTY_BYTE_ARRAY);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not update timestamp for " + file, e);
        } finally {
            IoActions.closeQuietly(out);
        }
    }

    public static void moveFile(File source, File destination) {
        logDeprecation();
        moveFileInternal(source, destination);
    }

    private static void moveFileInternal(File source, File destination) {
        try {
            FileUtils.moveFile(source, destination);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void moveExistingFile(File source, File destination) {
        logDeprecation();
        boolean rename = source.renameTo(destination);
        if (!rename) {
            moveFileInternal(source, destination);
        }
    }

    /**
     * If the destination file exists, then this method will overwrite it.
     *
     * @see FileUtils#copyFile(File, File)
     */
    public static void copyFile(File source, File destination) {
        logDeprecation();
        try {
            FileUtils.copyFile(source, destination);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void copyDirectory(File source, File destination) {
        logDeprecation();
        try {
            FileUtils.copyDirectory(source, destination);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void moveDirectory(File source, File destination) {
        logDeprecation();
        moveDirectoryInternal(source, destination);
    }

    private static void moveDirectoryInternal(File source, File destination) {
        try {
            FileUtils.moveDirectory(source, destination);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void moveExistingDirectory(File source, File destination) {
        logDeprecation();
        boolean rename = source.renameTo(destination);
        if (!rename) {
            moveDirectory(source, destination);
        }
    }

    public static String readFile(File file) {
        return readFile(file, Charset.defaultCharset().name());
    }

    public static String readFile(File file, String encoding) {
        logDeprecation();
        try {
            return FileUtils.readFileToString(file, encoding);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Reads and returns file contents. If some exception is triggered the method returns information about this exception.
     * Useful for including file contents in debug trace / exception messages.
     *
     * @param file to read
     * @return content of the file or the problem description in case file cannot be read.
     */
    public static String readFileQuietly(File file) {
        try {
            return readFile(file);
        } catch (Exception e) {
            return "Unable to read file '" + file + "' due to: " + e.toString();
        }
    }

    public static void writeFile(String content, File destination) {
        writeFile(content, destination, Charset.defaultCharset().name());
    }

    public static void writeFile(String content, File destination, String encoding) {
        logDeprecation();
        try {
            FileUtils.writeStringToFile(destination, content, encoding);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Collection<File> listFiles(File directory, String[] extensions, boolean recursive) {
        logDeprecation();
        return FileUtils.listFiles(directory, extensions, recursive);
    }

    public static List<String> toPaths(Collection<File> files) {
        logDeprecation();
        List<String> paths = new ArrayList<String>();
        for (File file : files) {
            paths.add(file.getAbsolutePath());
        }
        return paths;
    }

    public static void copyURLToFile(URL source, File destination) {
        logDeprecation();
        try {
            FileUtils.copyURLToFile(source, destination);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void deleteDirectory(File directory) {
        logDeprecation();
        try {
            FileUtils.deleteDirectory(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static boolean deleteQuietly(@Nullable File file) {
        logDeprecation();
        return FileUtils.deleteQuietly(file);
    }

    /**
     * Tail reading exception.
     */
    @Deprecated
    public static class TailReadingException extends RuntimeException {

        public TailReadingException(Throwable throwable) {
            this(throwable, true);
        }

        private TailReadingException(Throwable throwable, boolean logDeprecation) {
            super(throwable);
            if (logDeprecation) {
                logDeprecation();
            }
        }
    }

    /**
     * Returns the tail of a file.
     *
     * @param file to read from tail
     * @param maxLines max lines to read
     * @return tail content
     * @throws org.gradle.util.GFileUtils.TailReadingException when reading failed
     */
    @SuppressWarnings("DefaultCharset")
    public static String tail(File file, int maxLines) throws TailReadingException {
        logDeprecation();
        BufferedReader reader = null;
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(file);
            reader = new BufferedReader(fileReader);

            org.gradle.util.internal.LimitedDescription description = new org.gradle.util.internal.LimitedDescription(maxLines);
            String line = reader.readLine();
            while (line != null) {
                description.append(line);
                line = reader.readLine();
            }
            return description.toString();
        } catch (Exception e) {
            throw new TailReadingException(e, false);
        } finally {
            IoActions.closeQuietly(fileReader);
            IoActions.closeQuietly(reader);
        }
    }

    public static void forceDelete(File file) {
        logDeprecation();
        try {
            FileUtils.forceDelete(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Checksum checksum(File file, Checksum checksum) {
        logDeprecation();
        try {
            return FileUtils.checksum(file, checksum);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Makes the parent directory of the file, and any non existent parents.
     *
     * @param child The file to create the parent dir for
     * @return The parent dir file
     * @see #mkdirs(java.io.File)
     */
    public static File parentMkdirs(File child) {
        File parent = child.getParentFile();
        mkdirs(parent);
        return parent;
    }

    /**
     * Like {@link java.io.File#mkdirs()}, except throws an informative error if a dir cannot be created.
     *
     * @param dir The dir to create, including any non existent parent dirs.
     */
    public static void mkdirs(File dir) {
        logDeprecation();
        dir = dir.getAbsoluteFile();
        if (dir.isDirectory()) {
            return;
        }

        if (dir.exists() && !dir.isDirectory()) {
            throw new UncheckedIOException(String.format("Cannot create directory '%s' as it already exists, but is not a directory", dir));
        }

        List<File> toCreate = new LinkedList<File>();
        File parent = dir.getParentFile();
        while (!parent.exists()) {
            toCreate.add(parent);
            parent = parent.getParentFile();
        }

        Collections.reverse(toCreate);
        for (File parentDirToCreate : toCreate) {
            if (parentDirToCreate.isDirectory()) {
                continue;
            }

            File parentDirToCreateParent = parentDirToCreate.getParentFile();
            if (!parentDirToCreateParent.isDirectory()) {
                throw new UncheckedIOException(String.format("Cannot create parent directory '%s' when creating directory '%s' as '%s' is not a directory", parentDirToCreate, dir, parentDirToCreateParent));
            }

            if (!parentDirToCreate.mkdir() && !parentDirToCreate.isDirectory()) {
                throw new UncheckedIOException(String.format("Failed to create parent directory '%s' when creating directory '%s'", parentDirToCreate, dir));
            }
        }

        if (!dir.mkdir() && !dir.isDirectory()) {
            throw new UncheckedIOException(String.format("Failed to create directory '%s'", dir));
        }
    }

    /**
     * Returns the path of target relative to base.
     *
     * @param target target file or directory
     * @param base base directory
     * @return the path of target relative to base.
     */
    public static String relativePathOf(File target, File base) {
        logDeprecation();
        String separatorChars = "/" + File.separator;
        List<String> basePath = splitAbsolutePathOf(base, separatorChars);
        List<String> targetPath = new ArrayList<String>(splitAbsolutePathOf(target, separatorChars));

        // Find and remove common prefix
        int maxDepth = Math.min(basePath.size(), targetPath.size());
        int prefixLen = 0;
        while (prefixLen < maxDepth && basePath.get(prefixLen).equals(targetPath.get(prefixLen))) {
            prefixLen++;
        }
        basePath = basePath.subList(prefixLen, basePath.size());
        targetPath = targetPath.subList(prefixLen, targetPath.size());

        for (int i = 0; i < basePath.size(); i++) {
            targetPath.add(0, "..");
        }
        if (targetPath.isEmpty()) {
            return ".";
        }
        return CollectionUtils.join(File.separator, targetPath);
    }

    private static List<String> splitAbsolutePathOf(File baseDir, String separatorChars) {
        return Arrays.asList(StringUtils.split(baseDir.getAbsolutePath(), separatorChars));
    }
}
