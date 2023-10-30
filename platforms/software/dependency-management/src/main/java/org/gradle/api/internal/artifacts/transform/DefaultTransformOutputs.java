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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.file.FileLookup;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.util.internal.GFileUtils;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class DefaultTransformOutputs implements TransformOutputsInternal {

    private final TransformExecutionResult.OutputTypeInferringBuilder resultBuilder;
    private final Set<File> outputDirectories = new HashSet<>();
    private final Set<File> outputFiles = new HashSet<>();
    private final PathToFileResolver resolver;
    private final File inputArtifact;
    private final File outputDir;

    public DefaultTransformOutputs(File inputArtifact, File outputDir, FileLookup fileLookup) {
        this.resolver = fileLookup.getPathToFileResolver(outputDir);
        this.inputArtifact = inputArtifact;
        this.outputDir = outputDir;
        this.resultBuilder = TransformExecutionResult.builderFor(inputArtifact, outputDir);
    }

    @Override
    public TransformExecutionResult getRegisteredOutputs() {
        TransformExecutionResult result = resultBuilder.build();
        result.visitOutputs(new TransformExecutionResult.OutputVisitor() {
            @Override
            public void visitEntireInputArtifact() {
                validate(inputArtifact);
            }

            @Override
            public void visitPartOfInputArtifact(String relativePath) {
                validate(new File(inputArtifact, relativePath));
            }

            @Override
            public void visitProducedOutput(File outputLocation) {
                validate(outputLocation);
            }

            private void validate(File output) {
                validateOutputExists(outputDir, output);
                if (outputFiles.contains(output) && !output.isFile()) {
                    throw new InvalidUserDataException("Transform output file " + output.getPath() + " must be a file, but is not.");
                }
                if (outputDirectories.contains(output) && !output.isDirectory()) {
                    throw new InvalidUserDataException("Transform output directory " + output.getPath() + " must be a directory, but is not.");
                }
            }
        });

        return result;
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
        resultBuilder.addOutput(output, prepareOutputLocation);
        return output;
    }

    private static void validateOutputExists(File outputDir, File output) {
        if (!output.exists()) {
            String outputAbsolutePath = output.getAbsolutePath();
            String outputDirPrefix = outputDir.getAbsolutePath() + File.separator;
            String reportedPath = outputAbsolutePath.startsWith(outputDirPrefix)
                ? outputAbsolutePath.substring(outputDirPrefix.length())
                : outputAbsolutePath;
            throw new InvalidUserDataException("Transform output " + reportedPath + " must exist.");
        }
    }}
