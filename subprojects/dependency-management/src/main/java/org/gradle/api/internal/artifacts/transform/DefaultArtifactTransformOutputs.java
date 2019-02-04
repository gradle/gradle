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

package org.gradle.api.internal.artifacts.transform;

import com.google.common.collect.ImmutableList;

import java.io.File;

public class DefaultArtifactTransformOutputs implements ArtifactTransformOutputsInternal {

    private final ImmutableList.Builder<File> outputsBuilder = ImmutableList.builder();
    private final File workspace;

    public DefaultArtifactTransformOutputs(File workspace) {
        this.workspace = workspace;
    }

    @Override
    public File registerOutput(String relativePath) {
        File outputFile = new File(workspace, relativePath);
        outputsBuilder.add(outputFile);
        return outputFile;
    }

    @Override
    public void registerOutput(File output) {
        outputsBuilder.add(output);
    }

    @Override
    public File registerWorkspaceAsOutput() {
        outputsBuilder.add(workspace);
        return workspace;
    }

    @Override
    public ImmutableList<File> getRegisteredOutputs() {
        return outputsBuilder.build();
    }
}
