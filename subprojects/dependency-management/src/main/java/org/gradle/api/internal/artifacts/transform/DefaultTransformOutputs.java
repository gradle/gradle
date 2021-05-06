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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.file.FileLookup;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.util.internal.GFileUtils;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class DefaultTransformOutputs implements TransformOutputsInternal {

    private final ImmutableList.Builder<File> outputsBuilder = ImmutableList.builder();
    private final Set<File> outputDirectories = new HashSet<>();
    private final Set<File> outputFiles = new HashSet<>();
    private final PathToFileResolver resolver;
    private final File inputArtifact;
    private final File outputDir;
    private final String inputArtifactPrefix;
    private final String outputDirPrefix;

    public DefaultTransformOutputs(File inputArtifact, File outputDir, FileLookup fileLookup) {
        this.resolver = fileLookup.getPathToFileResolver(outputDir);
        this.inputArtifact = inputArtifact;
        this.outputDir = outputDir;
        this.inputArtifactPrefix = inputArtifact.getPath() + File.separator;
        this.outputDirPrefix = outputDir.getPath() + File.separator;
    }

    @Override
    public ImmutableList<File> getRegisteredOutputs() {
        ImmutableList<File> outputs = outputsBuilder.build();
        for (File output : outputs) {
            TransformOutputsInternal.validateOutputExists(outputDirPrefix, output);
            if (outputFiles.contains(output) && !output.isFile()) {
                throw new InvalidUserDataException("Transform output file " + output.getPath() + " must be a file, but is not.");
            }
            if (outputDirectories.contains(output) && !output.isDirectory()) {
                throw new InvalidUserDataException("Transform output directory " + output.getPath() + " must be a directory, but is not.");
            }
        }
        return outputs;
    }

    @Override
    public File dir(Object path) {
        File outputDir = resolveAndRegister(path, GFileUtils::mkdirs);
        outputDirectories.add(outputDir);
        return outputDir;
    }

    @Override
    public File file(Object path) {
        File outputFile = resolveAndRegister(path, location -> GFileUtils.mkdirs(location.getParentFile()));
        outputFiles.add(outputFile);
        return outputFile;
    }

    private File resolveAndRegister(Object path, Consumer<File> prepareOutputLocation) {
        File output = resolver.resolve(path);
        OutputLocationType outputLocationType = TransformOutputsInternal.determineOutputLocationType(output, inputArtifact, inputArtifactPrefix, outputDir, outputDirPrefix);
        if (outputLocationType == OutputLocationType.WORKSPACE) {
            prepareOutputLocation.accept(output);
        }
        outputsBuilder.add(output);
        return output;
    }
}
