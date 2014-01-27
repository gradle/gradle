/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.ide.visualstudio.internal

import org.gradle.nativebinaries.ProjectNativeBinary
import org.gradle.nativebinaries.tasks.InstallExecutable

class ExecutableVisualStudioProjectConfiguration extends VisualStudioProjectConfiguration {
    ExecutableVisualStudioProjectConfiguration(DefaultVisualStudioProject vsProject, String configurationName, String platformName, ProjectNativeBinary binary) {
        super(vsProject, configurationName, platformName, binary)
    }

    @Override
    String getBuildTask() {
        InstallExecutable installTask = getInstallTask()
        return installTask == null ? super.getBuildTask() : installTask.path
    }

    @Override
    File getOutputFile() {
        InstallExecutable installTask = getInstallTask()
        if (installTask == null) {
            return super.getOutputFile()
        } else {
            return new File(installTask.destinationDir, "lib/" + installTask.executable.name)
        }
    }

    private InstallExecutable getInstallTask() {
        return binary.tasks.withType(InstallExecutable).iterator().next()
    }
}
