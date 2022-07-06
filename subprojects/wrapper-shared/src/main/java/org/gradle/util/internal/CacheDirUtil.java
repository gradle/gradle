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

package org.gradle.util.internal;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Utility class for dealing with cache directories.
 */
public class CacheDirUtil {
    // NB if we drop Java 6 support for workers, this should probably be moved to :base-services, in GFileUtils.
    // For safety reasons, I wrote this with Java 8 and placed it here to be able to do so.

    /**
     * Mark a directory as a cache directory. This typically involves setting attributes or creating files in it.
     */
    public static void markAsCacheDirectory(File dir) {
        if (!dir.isDirectory()) {
            throw new RuntimeException(String.format("Cannot mark directory '%s' as a cache directory as it is not a directory", dir));
        }
        try {
            createCacheDirTag(dir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void createCacheDirTag(File dir) throws IOException {
        File cacheDirTag = new File(dir, "CACHEDIR.TAG");
        if (!cacheDirTag.createNewFile()) {
            // No need to write it again.
            return;
        }
        // Note: there is a race condition here where someone could delete the file, another Gradle process could create it
        // and we would open two streams to the same file. But nothing should delete the file this quickly, or at all.
        // This could be fixed by using the Path API, but that's not available in Java 6.
        try {
            OutputStream stream = new FileOutputStream(cacheDirTag);
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
                stream.close();
            }
        } catch (IOException e) {
            // We don't want a partially-written file to be left behind, so delete it if we can.
            cacheDirTag.delete();
            throw e;
        }
    }
}
