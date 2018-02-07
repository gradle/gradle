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

import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.AbstractBuildableComponentSpec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.platform.base.internal.ComponentSpecIdentifier;

import java.io.File;

public class PreCompiledHeader extends AbstractBuildableComponentSpec {
    FileCollection pchObjects;
    File prefixHeaderFile;
    String includeString;

    public PreCompiledHeader(ComponentSpecIdentifier identifier) {
        super(identifier, PreCompiledHeader.class);
    }

    @Internal
    public File getObjectFile() {
        return pchObjects == null ? null : pchObjects.getSingleFile();
    }

    public void setPchObjects(FileCollection pchObjects) {
        this.pchObjects = pchObjects;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public FileCollection getPchObjects() {
        return pchObjects;
    }

    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public File getPrefixHeaderFile() {
        return prefixHeaderFile;
    }

    public void setPrefixHeaderFile(File prefixHeaderFile) {
        this.prefixHeaderFile = prefixHeaderFile;
    }

    @Optional @Input
    public String getIncludeString() {
        return includeString;
    }

    public void setIncludeString(String includeString) {
        this.includeString = includeString;
    }

    @Internal
    @Override
    public ComponentSpecIdentifier getIdentifier() {
        return super.getIdentifier();
    }

    @Internal
    @Override
    public String getName() {
        return super.getName();
    }

    @Internal
    @Override
    public String getProjectPath() {
        return super.getProjectPath();
    }

    @Internal
    @Override
    protected String getTypeName() {
        return super.getTypeName();
    }

    @Internal
    @Override
    public String getDisplayName() {
        return super.getDisplayName();
    }

    @Internal
    @Override
    public Task getBuildTask() {
        return super.getBuildTask();
    }

    @Internal
    @Override
    public TaskDependency getBuildDependencies() {
        return super.getBuildDependencies();
    }

    @Internal
    @Override
    public Task getCheckTask() {
        return super.getCheckTask();
    }
}
