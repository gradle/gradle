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

package org.gradle.plugins.ide.internal.tooling;

import org.gradle.api.Project;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.process.internal.CurrentProcess;
import org.gradle.tooling.internal.build.DefaultBuildEnvironment;
import org.gradle.tooling.internal.gradle.DefaultBuildIdentifier;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

import java.io.File;
import java.util.List;

/**
 * Builds the GradleProject that contains the project hierarchy and task information
 */
public class BuildEnvironmentBuilder implements ToolingModelBuilder {
    private final FileCollectionFactory fileCollectionFactory;

    public BuildEnvironmentBuilder(FileCollectionFactory fileCollectionFactory) {
        this.fileCollectionFactory = fileCollectionFactory;
    }

    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals("org.gradle.tooling.model.build.BuildEnvironment");
    }

    @Override
    public Object buildAll(String modelName, Project target) {
        File gradleUserHomeDir = target.getGradle().getGradleUserHomeDir();
        String gradleVersion = target.getGradle().getGradleVersion();

        CurrentProcess currentProcess = new CurrentProcess(fileCollectionFactory);
        File javaHome = currentProcess.getJvm().getJavaHome();
        List<String> jvmArgs = currentProcess.getJvmOptions().getAllImmutableJvmArgs();

        DefaultBuildIdentifier buildIdentifier = new DefaultBuildIdentifier(target.getRootDir());

        return new DefaultBuildEnvironment(buildIdentifier, gradleUserHomeDir, gradleVersion, javaHome, jvmArgs);
    }
}
