/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import java.io.File;

public class DefaultTransformationWorkspace implements TransformationWorkspaceProvider.TransformationWorkspace {

    private static final String RESULTS_FILE_SUFFIX = ".bin";

    private final File outputDirectory;
    private final File resultsFile;

    public DefaultTransformationWorkspace(File workspaceBase) {
        this.outputDirectory = workspaceBase;
        this.resultsFile = new File(workspaceBase.getParentFile(), workspaceBase.getName() + RESULTS_FILE_SUFFIX);
    }

    @Override
    public File getOutputDirectory() {
        return outputDirectory;
    }

    @Override
    public File getResultsFile() {
        return resultsFile;
    }
}
