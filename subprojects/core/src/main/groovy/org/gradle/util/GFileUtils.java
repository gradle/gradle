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
import org.apache.commons.io.LineIterator;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.gradle.api.UncheckedIOException;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.zip.Checksum;

/**
 * @author Hans Dockter
 */
public class GFileUtils {

    public static final String FILE_SEPARATOR = System.getProperty("file.separator");

    public static FileInputStream openInputStream(File file) {
        try {
            return FileUtils.openInputStream(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static FileOutputStream openOutputStream(File file) {
        try {
            return FileUtils.openOutputStream(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String byteCountToDisplaySize(long size) {
        return FileUtils.byteCountToDisplaySize(size);
    }

    public static void touch(File file) {
        try {
            FileUtils.touch(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static File[] convertFileCollectionToFileArray(Collection files) {
        return FileUtils.convertFileCollectionToFileArray(files);
    }

    public static Collection listFiles(File directory, IOFileFilter fileFilter, IOFileFilter dirFilter) {
        return FileUtils.listFiles(directory, fileFilter, dirFilter);
    }

    public static Iterator iterateFiles(File directory, IOFileFilter fileFilter, IOFileFilter dirFilter) {
        return FileUtils.iterateFiles(directory, fileFilter, dirFilter);
    }

    public static Collection listFiles(File directory, String[] extensions, boolean recursive) {
        return FileUtils.listFiles(directory, extensions, recursive);
    }

    public static Iterator iterateFiles(File directory, String[] extensions, boolean recursive) {
        return FileUtils.iterateFiles(directory, extensions, recursive);
    }

    public static boolean contentEquals(File file1, File file2) {
        try {
            return FileUtils.contentEquals(file1, file2);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static File toFile(String... filePathParts) {
        final StringWriter filePathBuffer = new StringWriter();

        for (int i = 0; i < filePathParts.length; i++) {
            filePathBuffer.write(filePathParts[i]);
            if (i + 1 < filePathParts.length) {
                filePathBuffer.write(FILE_SEPARATOR);
            }
        }

        return new File(filePathBuffer.toString());
    }

    public static File toFile(URL url) {
        return FileUtils.toFile(url);
    }

    public static File[] toFiles(URL[] urls) {
        return FileUtils.toFiles(urls);
    }

    public static List<String> toPaths(Collection<File> files) {
        List<String> paths = new ArrayList<String>();
        for (File file : files) {
            paths.add(file.getAbsolutePath());
        }
        return paths;
    }

    public static List<URL> toURLs(Iterable<File> files) {
        List<URL> urls = new ArrayList<URL>();
        for (File file : files) {
            try {
                urls.add(file.toURI().toURL());
            } catch (MalformedURLException e) {
                throw new UncheckedIOException(e);
            }
        }
        return urls;
    }

    public static URL[] toURLArray(Collection<File> files) {
        return toURLs(files.toArray(new File[files.size()]));
    }

    public static URL[] toURLs(File[] files) {
        try {
            URL[] urls = new URL[files.length];
            for (int i = 0; i < files.length; i++) {
                File file = files[i];
                urls[i] = file.toURI().toURL();
            }
            return urls;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void copyFileToDirectory(File srcFile, File destDir) {
        try {
            FileUtils.copyFileToDirectory(srcFile, destDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void copyFileToDirectory(File srcFile, File destDir, boolean preserveFileDate) {
        try {
            FileUtils.copyFileToDirectory(srcFile, destDir, preserveFileDate);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void copyFile(File srcFile, File destFile) {
        try {
            FileUtils.copyFile(srcFile, destFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void copyFile(File srcFile, File destFile, boolean preserveFileDate) {
        try {
            FileUtils.copyFile(srcFile, destFile, preserveFileDate);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void copyDirectoryToDirectory(File srcDir, File destDir) {
        try {
            FileUtils.copyDirectoryToDirectory(srcDir, destDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void copyDirectory(File srcDir, File destDir) {
        try {
            FileUtils.copyDirectory(srcDir, destDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void copyDirectory(File srcDir, File destDir, boolean preserveFileDate) {
        try {
            FileUtils.copyDirectory(srcDir, destDir, preserveFileDate);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void copyDirectory(File srcDir, File destDir, FileFilter filter) {
        try {
            FileUtils.copyDirectory(srcDir, destDir, filter);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void copyDirectory(File srcDir, File destDir, FileFilter filter, boolean preserveFileDate) {
        try {
            FileUtils.copyDirectory(srcDir, destDir, filter, preserveFileDate);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void copyURLToFile(URL source, File destination) {
        try {
            FileUtils.copyURLToFile(source, destination);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void deleteDirectory(File directory) {
        try {
            FileUtils.deleteDirectory(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static boolean deleteQuietly(File file) {
        return FileUtils.deleteQuietly(file);
    }

    public static void cleanDirectory(File directory) {
        try {
            FileUtils.cleanDirectory(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static boolean waitFor(File file, int seconds) {
        return FileUtils.waitFor(file, seconds);
    }

    public static String readFileToString(File file, String encoding) {
        try {
            return FileUtils.readFileToString(file, encoding);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String readFileToString(File file) {
        try {
            return FileUtils.readFileToString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] readFileToByteArray(File file) {
        try {
            return FileUtils.readFileToByteArray(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List readLines(File file, String encoding) {
        try {
            return FileUtils.readLines(file, encoding);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List readLines(File file) {
        try {
            return FileUtils.readLines(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static LineIterator lineIterator(File file, String encoding) {
        try {
            return FileUtils.lineIterator(file, encoding);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static LineIterator lineIterator(File file) {
        try {
            return FileUtils.lineIterator(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void writeStringToFile(File file, String data, String encoding) {
        try {
            FileUtils.writeStringToFile(file, data, encoding);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void writeStringToFile(File file, String data) {
        try {
            FileUtils.writeStringToFile(file, data);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void writeByteArrayToFile(File file, byte[] data) {
        try {
            FileUtils.writeByteArrayToFile(file, data);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void writeLines(File file, String encoding, Collection lines) {
        try {
            FileUtils.writeLines(file, encoding, lines);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void writeLines(File file, Collection lines) {
        try {
            FileUtils.writeLines(file, lines);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void writeLines(File file, String encoding, Collection lines, String lineEnding) {
        try {
            FileUtils.writeLines(file, encoding, lines, lineEnding);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void writeLines(File file, Collection lines, String lineEnding) {
        try {
            FileUtils.writeLines(file, lines, lineEnding);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void forceDelete(File file) {
        try {
            FileUtils.forceDelete(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void forceDeleteOnExit(File file) {
        try {
            FileUtils.forceDeleteOnExit(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void forceMkdir(File directory) {
        try {
            FileUtils.forceMkdir(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static long sizeOfDirectory(File directory) {
        return FileUtils.sizeOfDirectory(directory);
    }

    public static boolean isFileNewer(File file, File reference) {
        return FileUtils.isFileNewer(file, reference);
    }

    public static boolean isFileNewer(File file, Date date) {
        return FileUtils.isFileNewer(file, date);
    }

    public static boolean isFileNewer(File file, long timeMillis) {
        return FileUtils.isFileNewer(file, timeMillis);
    }

    public static boolean isFileOlder(File file, File reference) {
        return FileUtils.isFileOlder(file, reference);
    }

    public static boolean isFileOlder(File file, Date date) {
        return FileUtils.isFileOlder(file, date);
    }

    public static boolean isFileOlder(File file, long timeMillis) {
        return FileUtils.isFileOlder(file, timeMillis);
    }

    public static long checksumCRC32(File file) {
        try {
            return FileUtils.checksumCRC32(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Checksum checksum(File file, Checksum checksum) {
        try {
            return FileUtils.checksum(file, checksum);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void moveDirectory(File srcDir, File destDir) {
        try {
            FileUtils.moveDirectory(srcDir, destDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void moveDirectoryToDirectory(File src, File destDir, boolean createDestDir) {
        try {
            FileUtils.moveDirectoryToDirectory(src, destDir, createDestDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void moveFile(File srcFile, File destFile) {
        try {
            FileUtils.moveFile(srcFile, destFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void moveFileToDirectory(File srcFile, File destDir, boolean createDestDir) {
        try {
            FileUtils.moveFileToDirectory(srcFile, destDir, createDestDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void moveToDirectory(File src, File destDir, boolean createDestDir) {
        try {
            FileUtils.moveToDirectory(src, destDir, createDestDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static File canonicalise(File src) {
        try {
            return src.getCanonicalFile();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<File> getSubDirectories(File directory) {
        final List<File> subDirectories = new ArrayList<File>();

        addSubDirectories(directory, subDirectories);

        return subDirectories;
    }

    public static void addSubDirectories(final File directory, final Collection<File> subDirectories) {
        final File[] subFiles = directory.listFiles();

        if (subFiles != null && subFiles.length > 0) {
            for (final File subFile : subFiles) {
                if (subFile.isDirectory()) {
                    subDirectories.add(subFile);
                    addSubDirectories(subFile, subDirectories);
                }
                // ignore files
            }
        }
    }

    public static List<File> getSubFiles(File directory) {
        final List<File> subFilesList = new ArrayList<File>();

        final File[] subFiles = directory.listFiles();
        if (subFiles != null && subFiles.length > 0) {
            for (final File subFile : subFiles) {
                if (subFile.isFile()) {
                    subFilesList.add(subFile);
                }
            }
        }

        return subFilesList;
    }

    public static boolean createDirectoriesWhenNotExistent(File... directories) {
        if (directories != null && directories.length > 0) {
            boolean directoriesCreated = true;
            int directoriesIndex = 0;

            while (directoriesCreated && directoriesIndex < directories.length) {
                final File currentDirectory = directories[directoriesIndex];

                if (!currentDirectory.exists()) {
                    directoriesCreated = currentDirectory.mkdirs();
                }

                directoriesIndex++;
            }

            return directoriesCreated;
        } else {
            return true;
        }
    }
}
