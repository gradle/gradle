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
import org.gradle.ide.xcode.XcodeWorkspace;
import org.gradle.ide.xcode.internal.DefaultXcodeProject;
import org.gradle.ide.xcode.internal.DefaultXcodeWorkspace;
import org.gradle.ide.xcode.tasks.internal.XcodeWorkspaceFile;
import org.gradle.plugins.ide.api.XmlGeneratorTask;

import java.io.File;

/**
 * Task for generating a workspace file.
 *
 * @since 4.2
 */
@Incubating
public class GenerateXcodeWorkspaceFileTask extends XmlGeneratorTask<XcodeWorkspaceFile> {
    private DefaultXcodeWorkspace xcodeWorkspace;

    @Override
    protected void configure(XcodeWorkspaceFile workspaceFile) {
        for (DefaultXcodeProject xcodeProject : xcodeWorkspace.getProjects()) {
            workspaceFile.addLocation(xcodeProject.getLocationDir().getAbsolutePath());
        }
    }

    @Override
    protected XcodeWorkspaceFile create() {
        return new XcodeWorkspaceFile(getXmlTransformer());
    }

    public void setXcodeWorkspace(XcodeWorkspace xcodeWorkspace) {
        this.xcodeWorkspace = (DefaultXcodeWorkspace) xcodeWorkspace;
    }

    @Override
    public File getInputFile() {
        return null;
    }
}
