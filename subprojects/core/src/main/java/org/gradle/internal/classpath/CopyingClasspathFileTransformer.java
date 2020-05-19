/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.classpath;

import org.gradle.internal.file.FileType;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.util.GFileUtils;

import java.io.File;

public class CopyingClasspathFileTransformer implements ClasspathFileTransformer {
    @Override
    public File transform(File source, CompleteFileSystemLocationSnapshot sourceSnapshot, File cacheDir) {
        if (sourceSnapshot.getType() != FileType.RegularFile) {
            return source;
        }
        File cachedFile = new File(cacheDir, "o_" + sourceSnapshot.getHash().toString() + '/' + source.getName());
        if (!cachedFile.isFile()) {
            // Just copy the jar
            GFileUtils.copyFile(source, cachedFile);
        }
        return cachedFile;
    }
}
