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

import org.gradle.api.DomainObjectSet;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.tasks.InstallExecutable;

import java.io.File;

public class ExecutableVisualStudioProjectConfiguration extends VisualStudioProjectConfiguration {
    public ExecutableVisualStudioProjectConfiguration(DefaultVisualStudioProject vsProject, String configurationName, String platformName, NativeBinarySpec binary) {
        super(vsProject, configurationName, platformName, binary);
    }

    @Override
    public String getBuildTask() {
        InstallExecutable installTask = getInstallTask();
        return installTask == null ? super.getBuildTask() : installTask.getPath();
    }

    @Override
    public File getOutputFile() {
        InstallExecutable installTask = getInstallTask();
        if (installTask == null) {
            return super.getOutputFile();
        } else {
            return new File(installTask.getDestinationDir(), "lib/" + installTask.getExecutable().getName());
        }

    }

    private InstallExecutable getInstallTask() {
        final DomainObjectSet<InstallExecutable> installTasks = getBinary().getTasks().withType(InstallExecutable.class);
        return installTasks.isEmpty() ? null : installTasks.iterator().next();
    }

}
