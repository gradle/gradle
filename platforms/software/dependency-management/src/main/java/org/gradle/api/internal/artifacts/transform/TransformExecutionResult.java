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
 *
 * The result of running a transform is a list of outputs.
 * There are two kinds of outputs for a transform:
 * - Produced outputs in the workspace. Those are relative paths depending on the workspace root, independent of the input artifact.
 * - Selected parts of the input artifact. These are relative paths of locations selected in the input artifact, independent of the workspace directory.
 *
 * The workspace can be relocated for immutable transform executions, and the input artifact can change.
 * Therefore, to get the absolute path of the output files they need to be resolved against both a workspace root and an input artifact.
 */
public abstract class TransformExecutionResult {

    protected final ImmutableList<Builder.TransformExecutionOutput> executionOutputs;

    protected TransformExecutionResult(ImmutableList<Builder.TransformExecutionOutput> executionOutputs) {
        this.executionOutputs = executionOutputs;
    }

    /**
     * Transform results bound to a workspace.
     */
    public interface TransformWorkspaceResult {
        /**
         * Resolves location of the outputs of this result for a given input artifact.
         *
         * Produced outputs don't need to be resolved to locations, since they are already resolved to absolute paths in the workspace.
         * The relative paths of selected parts of the input artifact need to resolved based on the provided input artifact location.
         */
        ImmutableList<File> resolveForInputArtifact(File inputArtifact);
    }

    public abstract TransformWorkspaceResult resolveForWorkspace(File workspaceDir);

    public void visitOutputs(OutputVisitor visitor) {
        executionOutputs.forEach(output -> output.visitOutput(visitor));
    }

    public int size() {
        return executionOutputs.size();
    }

    public static OutputTypeInferringBuilder builderFor(File inputArtifact, File outputDir) {
        return new OutputTypeInferringBuilder(inputArtifact, outputDir);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final ImmutableList.Builder<TransformExecutionOutput> builder = ImmutableList.builder();
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
            builder.add(new ProducedExecutionOutput(relativePath));
        }

        public TransformExecutionResult build() {
            ImmutableList<TransformExecutionOutput> transformOutputs = builder.build();
            return onlyProducedOutputs
                ? new ProducedOutputOnlyResult(transformOutputs)
                : new MixedInputAndProducedOutputResult(transformOutputs);
        }

        /**
         * Optimized variant for a transform whose results are all produced by the transform,
         * and don't include any of its input artifact.
         */
        private static class ProducedOutputOnlyResult extends TransformExecutionResult {
            public ProducedOutputOnlyResult(ImmutableList<TransformExecutionOutput> executionOutputs) {
                super(executionOutputs);
            }

            @Override
            public TransformWorkspaceResult resolveForWorkspace(File workspaceDir) {
                ImmutableList<File> resolvedOutputs = executionOutputs.stream()
                    .map(ProducedExecutionOutput.class::cast)
                    .map(output -> output.resolveForWorkspaceDirectly(workspaceDir))
                    .collect(ImmutableList.toImmutableList());
                return inputArtifact -> resolvedOutputs;
            }
        }

        /**
         * Results of a transform that includes parts or the whole of its input artifact.
         * It might also include outputs produced by the transform.
         */
        private static class MixedInputAndProducedOutputResult extends TransformExecutionResult {
            public MixedInputAndProducedOutputResult(ImmutableList<TransformExecutionOutput> executionOutputs) {
                super(executionOutputs);
            }

            @Override
            public TransformWorkspaceResult resolveForWorkspace(File workspaceDir) {
                ImmutableList<TransformWorkspaceOutput> resolvedOutputs = executionOutputs.stream()
                    .map(output -> output.resolveForWorkspace(workspaceDir))
                    .collect(ImmutableList.toImmutableList());
                return inputArtifact -> resolvedOutputs.stream()
                    .map(output -> output.resolveForInputArtifact(inputArtifact))
                    .collect(ImmutableList.toImmutableList());
            }
        }

        /**
         * A single output in a transform result.
         *
         * Can be either
         * - the entire input artifact {@link EntireInputArtifact}
         * - a part of the input artifact {@link PartOfInputArtifact}
         * - a produced output in the workspace {@link ProducedExecutionOutput}
         *
         * Only outputs related to the input artifact need resolving.
         */
        protected interface TransformExecutionOutput {
            TransformWorkspaceOutput resolveForWorkspace(File workspaceDir);

            void visitOutput(OutputVisitor visitor);
        }

        protected interface TransformWorkspaceOutput {
            File resolveForInputArtifact(File inputArtifact);
        }

        private static class PartOfInputArtifact implements TransformExecutionOutput, TransformWorkspaceOutput {
            private final String relativePath;

            public PartOfInputArtifact(String relativePath) {
                this.relativePath = relativePath;
            }

            @Override
            public TransformWorkspaceOutput resolveForWorkspace(File workspaceDir) {
                return this;
            }

            @Override
            public File resolveForInputArtifact(File inputArtifact) {
                return new File(inputArtifact, relativePath);
            }

            @Override
            public void visitOutput(OutputVisitor visitor) {
                visitor.visitPartOfInputArtifact(relativePath);
            }
        }

        private static class EntireInputArtifact implements TransformExecutionOutput, TransformWorkspaceOutput {
            public static final EntireInputArtifact INSTANCE = new EntireInputArtifact();

            @Override
            public TransformWorkspaceOutput resolveForWorkspace(File workspaceDir) {
                return this;
            }

            @Override
            public File resolveForInputArtifact(File inputArtifact) {
                return inputArtifact;
            }

            @Override
            public void visitOutput(OutputVisitor visitor) {
                visitor.visitEntireInputArtifact();
            }
        }

        private static class ProducedExecutionOutput implements TransformExecutionOutput {
            private final String relativePath;

            public ProducedExecutionOutput(String relativePath) {
                this.relativePath = relativePath;
            }

            public String getOutputLocation() {
                return relativePath;
            }

            @Override
            public TransformWorkspaceOutput resolveForWorkspace(File workspaceDir) {
                File workspacePath = resolveForWorkspaceDirectly(workspaceDir);
                return inputArtifact -> workspacePath;
            }

            public File resolveForWorkspaceDirectly(File workspaceDir) {
                return new File(workspaceDir, relativePath);
            }

            @Override
            public void visitOutput(OutputVisitor visitor) {
                visitor.visitProducedOutput(relativePath);
            }
        }
    }

    public interface OutputVisitor {
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
    public static class OutputTypeInferringBuilder {
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
