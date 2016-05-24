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
import org.gradle.api.internal.AbstractBuildableComponentSpec;
import org.gradle.api.tasks.InputFiles;
import org.gradle.platform.base.internal.ComponentSpecIdentifier;

import java.io.File;

public class PreCompiledHeader extends AbstractBuildableComponentSpec {
    FileCollection pchObjects;
    File prefixHeaderFile;
    String includeString;

    public PreCompiledHeader(ComponentSpecIdentifier identifier) {
        super(identifier, PreCompiledHeader.class);
    }

    public File getObjectFile() {
        return pchObjects == null ? null : pchObjects.getSingleFile();
    }

    public void setPchObjects(FileCollection pchObjects) {
        this.pchObjects = pchObjects;
    }

    @InputFiles
    public FileCollection getPchObjects() {
        return pchObjects;
    }

    public File getPrefixHeaderFile() {
        return prefixHeaderFile;
    }

    public void setPrefixHeaderFile(File prefixHeaderFile) {
        this.prefixHeaderFile = prefixHeaderFile;
    }

    public String getIncludeString() {
        return includeString;
    }

    public void setIncludeString(String includeString) {
        this.includeString = includeString;
    }
}
