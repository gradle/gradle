/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.ide.xcode.tasks;

import org.gradle.api.Incubating;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.ide.xcode.tasks.internal.XcodeWorkspaceFile;
import org.gradle.plugins.ide.api.XmlGeneratorTask;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;

/**
 * Task for generating a Xcode workspace file (e.g. {@code Foo.xcworkspace/contents.xcworkspacedata}). A workspace can contain any number of Xcode projects.
 *
 * @see org.gradle.ide.xcode.XcodeWorkspace
 * @since 4.2
 */
@Incubating
@DisableCachingByDefault(because = "Not made cacheable, yet")
public class GenerateXcodeWorkspaceFileTask extends XmlGeneratorTask<XcodeWorkspaceFile> {
    private FileCollection xcodeProjectLocations;

    public GenerateXcodeWorkspaceFileTask() {
        this.xcodeProjectLocations = getProject().files();
    }

    @Override
    protected void configure(XcodeWorkspaceFile workspaceFile) {
        for (File xcodeProjectDir : xcodeProjectLocations) {
            workspaceFile.addLocation(xcodeProjectDir.getAbsolutePath());
        }
    }

    @Override
    protected XcodeWorkspaceFile create() {
        return new XcodeWorkspaceFile(getXmlTransformer());
    }

    @PathSensitive(PathSensitivity.ABSOLUTE)
    @InputFiles
    public FileCollection getXcodeProjectLocations() {
        return xcodeProjectLocations;
    }

    public void setXcodeProjectLocations(FileCollection xcodeProjectLocations) {
        this.xcodeProjectLocations = xcodeProjectLocations;
    }

    @Override
    public File getInputFile() {
        return null;
    }
}
