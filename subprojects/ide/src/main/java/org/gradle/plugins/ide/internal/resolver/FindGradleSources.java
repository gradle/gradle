/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.plugins.ide.internal.resolver;

import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.Arrays;

public abstract class FindGradleSources implements TransformAction<TransformParameters.None> {
    @PathSensitive(PathSensitivity.NONE)
    @InputArtifact
    protected abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public void transform(TransformOutputs outputs) {
        registerSourceDirectories(outputs);
    }


    private void registerSourceDirectories(TransformOutputs outputs) {
        File unzippedDistroDir = unzippedDistroDir();
        if (unzippedDistroDir == null) {
            return;
        }

        File srcDir = outputs.dir("gradle-src");

        File subprojects = new File(unzippedDistroDir, "subprojects");
        Arrays.stream(subprojects.listFiles(File::isDirectory)).forEach(subproject -> {
            File subprojectDestination = new File(srcDir, subproject.getName());
            GFileUtils.mkdirs(subprojectDestination);
            File subprojectSourceRoot = new File(subproject, "src/main");
            if (subprojectSourceRoot.exists()) {
                Arrays.stream(subprojectSourceRoot.listFiles(File::isDirectory))
                    .forEach(subprojectSource -> GFileUtils.copyDirectory(subprojectSource, subprojectDestination));
            }
        });
    }

    private File unzippedDistroDir() {
        File[] unzippedDirs = getInputArtifact().get().getAsFile().listFiles();
        return unzippedDirs == null || unzippedDirs.length == 0 ? null : unzippedDirs[0];
    }

}
