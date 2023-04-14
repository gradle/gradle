/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Utility class for dealing with cache directories.
 */
class CacheDirUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheDirUtil.class);

    /**
     * Mark a directory as a cache directory. This typically involves setting attributes or creating files in it.
     */
    public static void tryMarkCacheDirectoryByTag(Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        Path cacheDirTag = dir.resolve("CACHEDIR.TAG");
        OutputStream stream;
        try {
            stream = Files.newOutputStream(cacheDirTag, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            // if we failed to write it, either the file already exists, or there was some other error.
            if (Files.exists(cacheDirTag)) {
                // if the file already exists, we assume another process has already written it correctly.
                return;
            }
            // otherwise, log the error and return
            LOGGER.debug("Failed to write cache directory tag file: " + cacheDirTag, e);
            return;
        }
        try {
            try {
                stream.write(
                    (
                        "Signature: 8a477f597d28d172789f06886806bc55\n" +
                            "# This file is a cache directory tag created by Gradle.\n" +
                            "# For information about cache directory tags, see:\n" +
                            "#\thttps://bford.info/cachedir/"
                    ).getBytes("UTF-8")
                );
            } finally {
                // stream close is done here so if it throws, we can re-use the outer catch block
                stream.close();
            }
        } catch (IOException e) {
            LOGGER.debug("Failed to write cache directory tag file: " + cacheDirTag, e);
            // don't leave a partial file
            try {
                Files.deleteIfExists(cacheDirTag);
            } catch (IOException e1) {
                // logging at warn here so it's more visible to the end-user, who might see the file and assume
                // that it is written properly.
                LOGGER.warn("Failed to delete partial cache directory tag file: " + cacheDirTag, e1);
            }
        }
    }
}
