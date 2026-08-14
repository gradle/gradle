/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal;

import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.jspecify.annotations.Nullable;

import java.io.File;

public class PreCompiledHeader {
    private FileCollection pchObjects;
    private File prefixHeaderFile;
    private String includeString;

    @Internal
    @Nullable
    public File getObjectFile() {
        return pchObjects == null ? null : pchObjects.getSingleFile();
    }

    public void setPchObjects(FileCollection pchObjects) {
        this.pchObjects = pchObjects;
    }

    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.ABSOLUTE)
    @InputFiles
    public FileCollection getPchObjects() {
        return pchObjects;
    }

    @Nullable
    @Optional
    @PathSensitive(PathSensitivity.ABSOLUTE)
    @InputFile
    public File getPrefixHeaderFile() {
        return prefixHeaderFile;
    }

    public void setPrefixHeaderFile(@Nullable File prefixHeaderFile) {
        this.prefixHeaderFile = prefixHeaderFile;
    }

    @Nullable
    @Optional
    @Input
    public String getIncludeString() {
        return includeString;
    }

    public void setIncludeString(@Nullable String includeString) {
        this.includeString = includeString;
    }
}
