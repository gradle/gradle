/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.file;

import net.jcip.annotations.ThreadSafe;
import org.gradle.internal.Factory;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.util.GFileUtils;

import java.io.File;

@ThreadSafe
public class JarCache {
    private final FileHasher fileHasher;

    public JarCache(FileHasher fileHasher) {
        this.fileHasher = fileHasher;
    }

    /**
     * Returns a cached copy of the given file. The cached copy is guaranteed to not be modified or removed.
     *
     * @param original The source file.
     * @param baseDirFactory A factory that can provide a base directory for the file cache.
     * @return The cached file.
     */
    public File getCachedJar(File original, Factory<File> baseDirFactory) {
        HashCode hashValue = fileHasher.hash(original);
        File baseDir = baseDirFactory.create();
        File cachedFile = new File(baseDir, hashValue.toString() + '/' + original.getName());
        if (!cachedFile.isFile()) {
            GFileUtils.copyFile(original, cachedFile);
        }
        return cachedFile;
    }
}
