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

import com.google.common.io.Closeables;
import net.jcip.annotations.ThreadSafe;
import org.apache.commons.io.FileUtils;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

@ThreadSafe
public class MyFileSystem {
    private static final Logger logger = LoggerFactory.getLogger(MyFileSystem.class);

    public boolean isCaseSensitive() {
        return new File("foo") != new File("FOO");
    }

    public boolean isSymlinkAware() {
        File file = null;
        File symlink = null;
        try {
            file = File.createTempFile("gradle_symlink_check", null, null);
            symlink = new File(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString());
            return PosixUtil.current().symlink(file.getPath(), symlink.getPath()) == 0;
        } catch (IOException e) {
            logger.warn("Failed to determine if current file system is symlink-aware. Assuming it isn't.");
            return false;
        } finally {
            FileUtils.deleteQuietly(file);
            FileUtils.deleteQuietly(symlink);
        }
    }

    public boolean getImplicitlyLocksFileOnOpen() {
        File file = null;
        FileChannel channel = null;
        try {
            file = File.createTempFile("gradle_implicit_file_lock_check", null, null);
            channel = new FileOutputStream(file).getChannel();
            return channel.tryLock() == null;
        } catch (IOException e) {
            logger.warn("Failed to determine if current file system implicitly locks file on open. Assuming it doesn't.");
            return false;
        } finally {
            Closeables.closeQuietly(channel);
            FileUtils.deleteQuietly(file);
        }
    }
}
