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
import org.gradle.api.UncheckedIOException;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

@ThreadSafe
public abstract class MyFileSystem {
    private static final Logger logger = LoggerFactory.getLogger(MyFileSystem.class);
    private static final MyFileSystem INSTANCE = new ProbingFileSystem();

    public static MyFileSystem current() {
        return INSTANCE;
    }

    public abstract boolean isCaseSensitive();
    public abstract boolean isSymlinkAware();
    public abstract boolean getImplicitlyLocksFileOnOpen();

    private static class ProbingFileSystem extends MyFileSystem {
        final boolean caseSensitive;
        final boolean symlinkAware;
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
        public boolean getImplicitlyLocksFileOnOpen() {
            return implicitLock;
        }

        ProbingFileSystem() {
            String secret = generateSecret();
            File probe = null;
            try {
                probe = generateFile(secret);
                caseSensitive = probeCaseSensitive(probe, secret);
                symlinkAware = probeSymlinkAware(probe, secret);
                implicitLock = probeImplicitlyLocksFileOnOpen(probe);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                FileUtils.deleteQuietly(probe);
            }
        }

        String generateSecret() {
            return UUID.randomUUID().toString();
        }

        File generateFile(String secret) throws IOException {
            File file = File.createTempFile("gradle_fs_probing", null, null);
            Files.write(secret, file, Charsets.UTF_8);
            return file;
        }

        boolean probeCaseSensitive(File probe, String secret) {
            try {
                File upperCased = new File(probe.getPath().toUpperCase());
                return hasContent(upperCased, secret);
            } catch (IOException e) {
                logger.warn("Failed to determine if current file system is case sensitive. Assuming it isn't.");
                return false;
            }
        }

        boolean probeSymlinkAware(File probe, String secret) {
            File symlink = null;
            try {
                symlink = createUniqueTempFileName();
                int errorCode = PosixUtil.current().symlink(probe.getPath(), symlink.getPath());
                return errorCode == 0 && hasContent(symlink, secret);
            } catch (IOException e) {
                logger.warn("Failed to determine if current file system is symlink aware. Assuming it isn't.");
                return false;
            } finally {
                FileUtils.deleteQuietly(symlink);
            }
        }

        boolean probeImplicitlyLocksFileOnOpen(File probe) {
            FileChannel channel = null;
            try {
                channel = new FileOutputStream(probe).getChannel();
                return channel.tryLock() == null;
            } catch (IOException e) {
                logger.warn("Failed to determine if current file system implicitly locks file on open. Assuming it doesn't.");
                return false;
            } finally {
                Closeables.closeQuietly(channel);
            }
        }

        boolean hasContent(File upperCased, String secret) throws IOException {
            return Files.readFirstLine(upperCased, Charsets.UTF_8).equals(secret);
        }

        File createUniqueTempFileName() throws IOException {
            return new File(System.getProperty("java.io.tmpdir"), "gradle_unique_file_name" + UUID.randomUUID().toString());
        }
    }
}
