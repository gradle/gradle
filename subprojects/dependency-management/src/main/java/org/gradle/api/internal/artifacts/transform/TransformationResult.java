/*
 * Copyright 2021 the original author or authors.
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
import java.util.stream.Stream;

/**
 * The result of running a transformation.
 */
public interface TransformationResult {
    /**
     * The outputs of the transformation for a given input artifact.
     *
     * A transform can have two kinds of outputs:
     * - Produced outputs in the workspace. Those are absolute paths which do not change depending on the input artifact.
     * - Selected parts of the input artifact. These outputs are considered relative to the given input artifact.
     *   If two input artifacts have the same normalization, and therefore we re-use the result, we still need
     *   to resolve the relative paths of the outputs to the currently transformed input artifact.
     */
    ImmutableList<File> resolveOutputsForInputArtifact(File inputArtifact);

    /**
     * The result of the artifact transform in the transform workspace.
     *
     * May be empty, when the transform only selects elements of the input artifact.
     */
    ImmutableList<File> getResultsInWorkspace();

    static Builder builder() {
        return new Builder();
    }

    class Builder {
        private final ImmutableList.Builder<TransformationResultFile> builder = ImmutableList.builder();

        public void addInputArtifact(String relativePath) {
            builder.add(new InInputArtifact(relativePath));
        }

        public void addInputArtifact() {
            builder.add(new InputArtifact());
        }

        public void addOutput(File outputLocation) {
            builder.add(new WorkspaceLocation(outputLocation));
        }

        public TransformationResult build() {
            ImmutableList<TransformationResultFile> transformationResults = builder.build();
            ImmutableList.Builder<File> workspaceFileBuilder = ImmutableList.builder();
            transformationResults.stream()
                .flatMap(TransformationResultFile::getWorkspaceFile)
                .forEach(workspaceFileBuilder::add);
            ImmutableList<File> workspaceFiles = workspaceFileBuilder.build();
            return transformationResults.size() == workspaceFiles.size()
                ? new WorkspaceOnlyTransformationResult(workspaceFiles)
                : new DefaultTransformationResult(workspaceFiles, transformationResults);
        }

        private interface TransformationResultFile {
            Stream<File> getWorkspaceFile();
            File resultRelativeTo(File inputArtifact);
        }

        private static class WorkspaceOnlyTransformationResult implements TransformationResult {
            private final ImmutableList<File> result;

            public WorkspaceOnlyTransformationResult(ImmutableList<File> result) {
                this.result = result;
            }

            @Override
            public ImmutableList<File> resolveOutputsForInputArtifact(File inputArtifact) {
                return result;
            }

            @Override
            public ImmutableList<File> getResultsInWorkspace() {
                return result;
            }
        }

        private static class DefaultTransformationResult implements TransformationResult {
            private final ImmutableList<File> workspaceFiles;
            private final ImmutableList<TransformationResultFile> transformationResults;

            public DefaultTransformationResult(ImmutableList<File> workspaceFiles, ImmutableList<TransformationResultFile> transformationResults) {
                this.workspaceFiles = workspaceFiles;
                this.transformationResults = transformationResults;
            }

            @Override
            public ImmutableList<File> resolveOutputsForInputArtifact(File inputArtifact) {
                ImmutableList.Builder<File> builder = ImmutableList.builderWithExpectedSize(transformationResults.size());
                transformationResults.forEach(resultFile -> builder.add(resultFile.resultRelativeTo(inputArtifact)));
                return builder.build();
            }

            @Override
            public ImmutableList<File> getResultsInWorkspace() {
                return workspaceFiles;
            }
        }

        private static class InInputArtifact implements TransformationResultFile {
            private final String relativePath;

            public InInputArtifact(String relativePath) {
                this.relativePath = relativePath;
            }

            @Override
            public Stream<File> getWorkspaceFile() {
                return Stream.empty();
            }

            @Override
            public File resultRelativeTo(File inputArtifact) {
                return new File(inputArtifact, relativePath);
            }
        }

        private static class InputArtifact implements TransformationResultFile {
            @Override
            public Stream<File> getWorkspaceFile() {
                return Stream.empty();
            }

            @Override
            public File resultRelativeTo(File inputArtifact) {
                return inputArtifact;
            }
        }

        private static class WorkspaceLocation implements TransformationResultFile {
            private final File outputFile;

            public WorkspaceLocation(File outputFile) {
                this.outputFile = outputFile;
            }

            @Override
            public Stream<File> getWorkspaceFile() {
                return Stream.of(outputFile);
            }

            @Override
            public File resultRelativeTo(File inputArtifact) {
                return outputFile;
            }
        }
    }
}
