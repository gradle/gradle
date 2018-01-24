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

package org.gradle.ide.visualstudio.internal;

import java.io.File;
import java.util.List;

//TODO: collapse this and VisualStudioTargetBinary - there doesn't seem to be a meaningful distinction between these two classes
public class VisualStudioProjectConfiguration {
    private final DefaultVisualStudioProject vsProject;
    private final String configurationName;
    private final String platformName;
    private final VisualStudioTargetBinary binary;

    public VisualStudioProjectConfiguration(DefaultVisualStudioProject vsProject, String configurationName, String platformName, VisualStudioTargetBinary binary) {
        this.vsProject = vsProject;
        this.configurationName = configurationName;
        this.platformName = platformName;
        this.binary = binary;
    }

    public String getName() {
        return configurationName + "|" + platformName;
    }

    public String getConfigurationName() {
        return configurationName;
    }

    public String getPlatformName() {
        return platformName;
    }

    public String getBuildTask() {
        return binary.getBuildTaskPath();
    }

    public String getCleanTask() {
        return binary.getCleanTaskPath();
    }

    public File getOutputFile() {
        return binary.getOutputFile();
    }

    public boolean isDebug() {
        return binary.isDebuggable();
    }

    public List<String> getCompilerDefines() {
        return binary.getCompilerDefines();
    }

    public List<File> getIncludePaths() {
        return binary.getIncludePaths();
    }

    public final String getType() {
        return "Makefile";
    }

    public DefaultVisualStudioProject getProject() {
        return vsProject;
    }

    public Iterable<VisualStudioTargetBinary> getDependencyBinaries() {
        return binary.getDependencies();
    }
}
