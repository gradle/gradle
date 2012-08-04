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

import org.gradle.api.Nullable;
import org.gradle.internal.Factory;
import org.gradle.internal.SystemProperties;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TmpDirTemporaryFileProvider extends DefaultTemporaryFileProvider {
    private final List<File> createdFiles = new ArrayList<File>();

    public TmpDirTemporaryFileProvider() {
        super(new Factory<File>() {
            public File create() {
                return GFileUtils.canonicalise(new File(SystemProperties.getJavaIoTmpDir()));
            }
        });
    }

    public File createTemporaryFile(@Nullable String prefix, @Nullable String suffix, @Nullable String... path) {
        return deleteLater(super.createTemporaryFile(prefix, suffix, path));
    }

    public File createTemporaryDirectory(@Nullable String prefix, @Nullable String suffix, @Nullable String... path) {
        return deleteLater(super.createTemporaryDirectory(prefix, suffix, path));
    }

    public void deleteAllCreated() {
        for (File createdFile : createdFiles) {
            GFileUtils.deleteQuietly(createdFile);
        }
    }

    private File deleteLater(File tmpFile) {
        createdFiles.add(tmpFile);
        return tmpFile;
    }
}
