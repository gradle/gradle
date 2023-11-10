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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.RelativePath;

import java.io.File;
import java.util.function.Consumer;

/**
 * The result of running a single transform action on a single input artifact.
 * This result is not bound to a workspace, and all outputs are stored relatively.
 *
 * The result of running a transform is a list of outputs.
 * There are two kinds of outputs for a transform:
 * - Produced outputs in the workspace. Those are absolute paths which do not change depending on the input artifact.
 * - Selected parts of the input artifact. These are relative paths of locations selected in the input artifact.
 */
public interface TransformExecutionResult {

    default TransformWorkspaceResult bindToOutputDir(File outputDir) {
        return inputArtifact -> resolveLocations(inputArtifact, outputDir);
    }

    ImmutableList<File> resolveLocations(File inputArtifact, File outputDir);

    void visitOutputs(OutputVisitor visitor);

    int size();

    static OutputTypeInferringBuilder builderFor(File inputArtifact, File outputDir) {
        return new OutputTypeInferringBuilder(inputArtifact, outputDir);
    }

    static Builder builder() {
        return new Builder();
    }

    class Builder {
        private final ImmutableList.Builder<TransformOutput> builder = ImmutableList.builder();
        private boolean onlyProducedOutputs = true;

        public void addEntireInputArtifact() {
            onlyProducedOutputs = false;
            builder.add(EntireInputArtifact.INSTANCE);
        }

        public void addPartOfInputArtifact(String relativePath) {
            onlyProducedOutputs = false;
            builder.add(new PartOfInputArtifact(relativePath));
        }

        public void addProducedOutput(String relativePath) {
            builder.add(new ProducedOutput(relativePath));
        }

        public TransformExecutionResult build() {
            ImmutableList<TransformOutput> transformOutputs = builder.build();
            return onlyProducedOutputs
                ? new ProducedOutputOnlyResult(convertToProducedOutputLocations(transformOutputs))
                : new FilteredResult(transformOutputs);
        }

        private static ImmutableList<String> convertToProducedOutputLocations(ImmutableList<TransformOutput> transformOutputs) {
            ImmutableList.Builder<String> builder = new ImmutableList.Builder<>();
            transformOutputs.forEach(output -> builder.add(((ProducedOutput) output).getOutputLocation()));
            return builder.build();
        }

        /**
         * Optimized variant for a transform whose results are all produced by the transform,
         * and don't include any of its input artifact.
         */
        private static class ProducedOutputOnlyResult implements TransformExecutionResult {
            private final ImmutableList<String> producedOutputRelativePaths;

            public ProducedOutputOnlyResult(ImmutableList<String> producedOutputRelativePaths) {
                this.producedOutputRelativePaths = producedOutputRelativePaths;
            }

            @Override
            public ImmutableList<File> resolveLocations(File inputArtifact, File outputDir) {
                ImmutableList.Builder<File> builder = ImmutableList.builderWithExpectedSize(size());
                producedOutputRelativePaths.stream()
                    .map(relativePath -> new File(outputDir, relativePath))
                    .forEach(builder::add);
                return builder.build();
            }

            @Override
            public void visitOutputs(OutputVisitor visitor) {
                producedOutputRelativePaths.forEach(visitor::visitProducedOutput);
            }

            @Override
            public int size() {
                return producedOutputRelativePaths.size();
            }
        }

        /**
         * Results of a transform that includes parts or the whole of its input artifact.
         * It might also include outputs produced by the transform.
         */
        private static class FilteredResult implements TransformExecutionResult {
            private final ImmutableList<TransformOutput> transformOutputs;

            public FilteredResult(ImmutableList<TransformOutput> transformOutputs) {
                this.transformOutputs = transformOutputs;
            }

            @Override
            public ImmutableList<File> resolveLocations(File inputArtifact, File outputDir) {
                ImmutableList.Builder<File> builder = ImmutableList.builderWithExpectedSize(size());
                transformOutputs.stream()
                    .map(output -> output.resolveLocation(inputArtifact, outputDir))
                    .forEach(builder::add);
                return builder.build();
            }

            @Override
            public void visitOutputs(OutputVisitor visitor) {
                transformOutputs.forEach(output -> output.visitOutput(visitor));
            }

            @Override
            public int size() {
                return transformOutputs.size();
            }
        }

        /**
         * A single output in a transform result.
         *
         * Can be either
         * - the entire input artifact {@link EntireInputArtifact}
         * - a part of the input artifact {@link PartOfInputArtifact}
         * - a produced output in the workspace {@link ProducedOutput}
         *
         * Only outputs related to the input artifact need resolving.
         */
        private interface TransformOutput {
            File resolveLocation(File inputArtifact, File outputDir);

            void visitOutput(OutputVisitor visitor);
        }

        private static class PartOfInputArtifact implements TransformOutput {
            private final String relativePath;

            public PartOfInputArtifact(String relativePath) {
                this.relativePath = relativePath;
            }

            @Override
            public File resolveLocation(File inputArtifact, File outputDir) {
                return new File(inputArtifact, relativePath);
            }

            @Override
            public void visitOutput(OutputVisitor visitor) {
                visitor.visitPartOfInputArtifact(relativePath);
            }
        }

        private static class EntireInputArtifact implements TransformOutput {
            public static final EntireInputArtifact INSTANCE = new EntireInputArtifact();

            @Override
            public File resolveLocation(File inputArtifact, File outputDir) {
                return inputArtifact;
            }

            @Override
            public void visitOutput(OutputVisitor visitor) {
                visitor.visitEntireInputArtifact();
            }
        }

        private static class ProducedOutput implements TransformOutput {
            private final String relativePath;

            public ProducedOutput(String relativePath) {
                this.relativePath = relativePath;
            }

            public String getOutputLocation() {
                return relativePath;
            }

            @Override
            public File resolveLocation(File inputArtifact, File outputDir) {
                return new File(outputDir, relativePath);
            }

            @Override
            public void visitOutput(OutputVisitor visitor) {
                visitor.visitProducedOutput(relativePath);
            }
        }
    }

    interface OutputVisitor {
        /**
         * Called when the result is the full input artifact.
         */
        void visitEntireInputArtifact();

        /**
         * Called when the result is inside the input artifact.
         *
         * @param relativePath the relative path from the input artifact to the selected location in the input artifact.
         */
        void visitPartOfInputArtifact(String relativePath);

        /**
         * Called when the result is a produced output in the workspace.
         *
         * @param relativePath the relative path of the output in the workspace.
         */
        void visitProducedOutput(String relativePath);
    }

    /**
     * A {@link TransformExecutionResult} builder which accepts absolute locations of results.
     * <p>
     * The builder then infers if the result is (in) the input artifact or a produced output in the workspace.
     */
    class OutputTypeInferringBuilder {
        private final File inputArtifact;
        private final File outputDir;
        private final String inputArtifactPrefix;
        private final String outputDirPrefix;
        private final Builder delegate = TransformExecutionResult.builder();

        public OutputTypeInferringBuilder(File inputArtifact, File outputDir) {
            this.inputArtifact = inputArtifact;
            this.outputDir = outputDir;
            this.inputArtifactPrefix = inputArtifact.getPath() + File.separator;
            this.outputDirPrefix = outputDir.getPath() + File.separator;
        }

        /**
         * Adds an output location to the result.
         *
         * @param workspaceAction an action to run when the output is a produced output in the workspace.
         */
        public void addOutput(File output, Consumer<File> workspaceAction) {
            if (output.equals(inputArtifact)) {
                delegate.addEntireInputArtifact();
            } else if (output.equals(outputDir)) {
                delegate.addProducedOutput("");
                workspaceAction.accept(output);
            } else if (output.getPath().startsWith(outputDirPrefix)) {
                String relativePath = RelativePath.parse(true, output.getPath().substring(outputDirPrefix.length())).getPathString();
                delegate.addProducedOutput(relativePath);
                workspaceAction.accept(output);
            } else if (output.getPath().startsWith(inputArtifactPrefix)) {
                String relativePath = RelativePath.parse(true, output.getPath().substring(inputArtifactPrefix.length())).getPathString();
                delegate.addPartOfInputArtifact(relativePath);
            } else {
                throw new InvalidUserDataException("Transform output " + output.getPath() + " must be a part of the input artifact or refer to a relative path.");
            }
        }

        public TransformExecutionResult build() {
            return delegate.build();
        }
    }
}
