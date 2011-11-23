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
package org.gradle.os;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.UUID;

import com.google.common.base.Charsets;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import net.jcip.annotations.ThreadSafe;
import org.apache.commons.io.FileUtils;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 * Provides information on the current file system. For a system with multiple different
 * file systems, the information is accurate for the file system that holds the Java temp
 * directory.
 */
@ThreadSafe
public abstract class MyFileSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(MyFileSystem.class);
    private static final MyFileSystem INSTANCE = new ProbingFileSystem();

    public static MyFileSystem current() {
        return INSTANCE;
    }

    /**
     * Tells whether the file system is case sensitive.
     *
     * @return <tt>true</tt> if the file system is case sensitive, <tt>false</tt> otherwise
     */
    public abstract boolean isCaseSensitive();

    /**
     * Tells if the file system can resolve symlinks. If the answer cannot be determined reliably,
     * <tt>true</tt> is returned.
     *
     * @return <tt>true</tt> if the file system can resolve symlinks, <tt>false</tt> otherwise
     */
    public abstract boolean isSymlinkAware();

    /**
     * Tells if the file system can create symlinks. If the answer cannot be determined accurately,
     * <tt>false</tt> is returned.
     *
     * @return <tt>true</tt> if the file system can create symlinks, <tt>false</tt> otherwise
     */
    public abstract boolean canCreateSymlinks();

    /**
     * Tells whether the file system implicitly locks a file when it is opened.
     *
     * @return <tt>true</tt> if the file system implicitly locks a file when it is opened, <tt>false</tt> otherwise
     */
    public abstract boolean getImplicitlyLocksFileOnOpen();

    /**
     * Creates a symlink from one path to another.
     * 
     * @param from path of the symlink to be created
     * @param to path of the file to link to
     * @exception IOException if the operation fails
     */
    public abstract void createSymlink(File from, File to) throws IOException;

    /**
     * Tries to create a symlink from one path to another.
     *
     * @param from path of the symlink to be created
     * @param to path of the file to link to
     * @return <tt>true</tt> if the operation was successful, <tt>false</tt> otherwise
     */
    public abstract boolean tryCreateSymlink(File from, File to);

    private static class ProbingFileSystem extends MyFileSystem {
        final boolean caseSensitive;
        final boolean symlinkAware;
        final boolean canCreateSymlinks;
        final boolean implicitLock;

        @Override
        public boolean isCaseSensitive() {
            return caseSensitive;
        }

        @Override
        public boolean isSymlinkAware() {
            return symlinkAware;
        }

        @Override
        public boolean canCreateSymlinks() {
            return canCreateSymlinks;
        }

        @Override
        public boolean getImplicitlyLocksFileOnOpen() {
            return implicitLock;
        }

        @Override
        public void createSymlink(File from, File to) throws IOException {
            int returnCode = PosixUtil.current().symlink(to.getPath(), from.getPath());
            if (returnCode != 0) {
                throw new IOException("Failed to create symlink. Return code is: " + returnCode);
            }
        }
        
        @Override
        public boolean tryCreateSymlink(File from, File to) {
            return PosixUtil.current().symlink(to.getPath(), from.getPath()) == 0;    
        }

        ProbingFileSystem() {
            String content = generateUniqueContent();
            File file = null;
            try {
                file = createFile(content);
                caseSensitive = probeCaseSensitive(file, content);
                symlinkAware = probeSymlinkAware(file, content);
                canCreateSymlinks = probeCanCreateSymlinks(file, content);
                implicitLock = probeImplicitlyLocksFileOnOpen(file);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                FileUtils.deleteQuietly(file);
            }
        }

        String generateUniqueContent() {
            return UUID.randomUUID().toString();
        }

        File createFile(String secret) throws IOException {
            File file = File.createTempFile("gradle_fs_probing", null, null);
            Files.write(secret, file, Charsets.UTF_8);
            return file;
        }

        boolean probeCaseSensitive(File file, String content) {
            try {
                File upperCased = new File(file.getPath().toUpperCase());
                return !hasContent(upperCased, content);
            } catch (IOException e) {
                // not fully accurate but a sensible fallback
                // see http://stackoverflow.com/questions/1288102/how-do-i-detect-whether-the-file-system-is-case-sensitive
                boolean result = !new File("foo").equals(new File("FOO"));
                LOGGER.warn("Failed to determine if file system is case sensitive. Best guess is '{}'.", result);
                return result;
            }
        }

        boolean probeSymlinkAware(File file, String content) {
            File symlink = null;
            try {
                symlink = generateUniqueTempFileName();
                int returnCode = PosixUtil.current().symlink(file.getPath(), symlink.getPath());
                if (returnCode != 0) {
                    LOGGER.warn("Failed to determine if file system is symlink aware. Assuming it is.");
                    return true;
                }
                return hasContent(symlink, content);
            } catch (IOException e) {
                LOGGER.warn("Failed to determine if file system is symlink aware. Assuming it is.");
                return true;
            } finally {
                FileUtils.deleteQuietly(symlink);
            }
        }

        boolean probeCanCreateSymlinks(File file, String content) {
            File symlink = null;
            try {
                symlink = generateUniqueTempFileName();
                int returnCode = PosixUtil.current().symlink(file.getPath(), symlink.getPath());
                return returnCode != 0 && hasContent(symlink, content);
            } catch (IOException e) {
                LOGGER.warn("Failed to determine if file system can create symlinks. Assuming it can't.");
                return false;
            } finally {
                FileUtils.deleteQuietly(symlink);
            }
        }

        boolean probeImplicitlyLocksFileOnOpen(File file) {
            FileChannel channel = null;
            try {
                channel = new FileOutputStream(file).getChannel();
                return channel.tryLock() == null;
            } catch (IOException e) {
                LOGGER.warn("Failed to determine if file system implicitly locks file on open. Assuming it doesn't.");
                return false;
            } finally {
                Closeables.closeQuietly(channel);
            }
        }

        boolean hasContent(File file, String content) throws IOException {
            return file.exists() && Files.readFirstLine(file, Charsets.UTF_8).equals(content);
        }

        File generateUniqueTempFileName() throws IOException {
            return new File(System.getProperty("java.io.tmpdir"), "gradle_unique_file_name" + UUID.randomUUID().toString());
        }
    }
}
