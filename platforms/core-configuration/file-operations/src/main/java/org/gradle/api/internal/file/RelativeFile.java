/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.file;

import org.gradle.api.file.RelativePath;

import java.io.File;
import java.io.Serializable;

public class RelativeFile implements Serializable {

    private final File file;
    private final RelativePath relativePath;

    public RelativeFile(File file, RelativePath relativePath) {
        this.file = file;
        this.relativePath = relativePath;
    }

    public File getFile() {
        return file;
    }

    public RelativePath getRelativePath() {
        return relativePath;
    }

    public File getBaseDir() {
        if (file == null || relativePath == null) {
            return null;
        }
        int relativeSegments = relativePath.getSegments().length;
        File parentFile = file;
        for (int i=0; i<relativeSegments; i++) {
            parentFile = parentFile.getParentFile();
        }
        return parentFile;
    }

}
