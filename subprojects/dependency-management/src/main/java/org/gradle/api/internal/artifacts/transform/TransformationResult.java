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

/**
 * The result of running a transformation.
 *
 * The result of running a transformation is a list of outputs.
 * There are two kinds of outputs for a transformation:
 * - Produced outputs in the workspace. Those are absolute paths which do not change depending on the input artifact.
 * - Selected parts of the input artifact. These are relative paths of locations selected in the input artifact.
 */
public interface TransformationResult {
    /**
     * Resolves location of the outputs of this results for a given input artifact.
     *
     * Produced outputs don't need to be resolved to locations, since they are absolute paths and can be returned as is.
     * The relative paths of selected parts of the input artifact need to resolved based on the provided input artifact location.
     */
    ImmutableList<File> resolveOutputsForInputArtifact(File inputArtifact);

    static Builder builder() {
        return new Builder();
    }

    class Builder {
        private final ImmutableList.Builder<TransformationOutput> builder = ImmutableList.builder();
        private boolean onlyProducedOutputs = true;

        public void addInputArtifact(String relativePath) {
            onlyProducedOutputs = false;
            builder.add(new PartOfInputArtifact(relativePath));
        }

        public void addInputArtifact() {
            onlyProducedOutputs = false;
            builder.add(EntireInputArtifact.INSTANCE);
        }

        public void addOutput(File outputLocation) {
            builder.add(new ProducedOutput(outputLocation));
        }

        public TransformationResult build() {
            ImmutableList<TransformationOutput> transformationOutputs = builder.build();
            return onlyProducedOutputs
                ? new AlreadyResolvedTransformationResult(convertToProducedOutputLocations(transformationOutputs))
                : new ResolvingTransformationResult(transformationOutputs);
        }

        /**
         * A single output in a transformation result.
         *
         * Can be either
         * - the entire input artifact {@link EntireInputArtifact}
         * - a part of the input artifact {@link PartOfInputArtifact}
         * - a produced output in the workspace {@link ProducedOutput}
         *
         * Only outputs related to the input artifact need resolving.
         */
        private interface TransformationOutput {
            File resolveForInputArtifact(File inputArtifact);
        }

        private static ImmutableList<File> convertToProducedOutputLocations(ImmutableList<TransformationOutput> transformationOutputs) {
            ImmutableList.Builder<File> builder = new ImmutableList.Builder<>();
            transformationOutputs.forEach(output -> builder.add(((ProducedOutput) output).getOutputLocation()));
            return builder.build();
        }

        private static class AlreadyResolvedTransformationResult implements TransformationResult {
            private final ImmutableList<File> producedOutputLocations;

            public AlreadyResolvedTransformationResult(ImmutableList<File> producedOutputLocations) {
                this.producedOutputLocations = producedOutputLocations;
            }

            @Override
            public ImmutableList<File> resolveOutputsForInputArtifact(File inputArtifact) {
                return producedOutputLocations;
            }
        }

        private static class ResolvingTransformationResult implements TransformationResult {
            private final ImmutableList<TransformationOutput> transformationOutputs;

            public ResolvingTransformationResult(ImmutableList<TransformationOutput> transformationOutputs) {
                this.transformationOutputs = transformationOutputs;
            }

            @Override
            public ImmutableList<File> resolveOutputsForInputArtifact(File inputArtifact) {
                ImmutableList.Builder<File> builder = ImmutableList.builderWithExpectedSize(transformationOutputs.size());
                transformationOutputs.forEach(output -> builder.add(output.resolveForInputArtifact(inputArtifact)));
                return builder.build();
            }
        }

        private static class PartOfInputArtifact implements TransformationOutput {
            private final String relativePath;

            public PartOfInputArtifact(String relativePath) {
                this.relativePath = relativePath;
            }

            @Override
            public File resolveForInputArtifact(File inputArtifact) {
                return new File(inputArtifact, relativePath);
            }
        }

        private static class EntireInputArtifact implements TransformationOutput {
            public static final EntireInputArtifact INSTANCE = new EntireInputArtifact();

            @Override
            public File resolveForInputArtifact(File inputArtifact) {
                return inputArtifact;
            }
        }

        private static class ProducedOutput implements TransformationOutput {
            private final File outputFile;

            public ProducedOutput(File outputFile) {
                this.outputFile = outputFile;
            }

            public File getOutputLocation() {
                return outputFile;
            }

            @Override
            public File resolveForInputArtifact(File inputArtifact) {
                return outputFile;
            }
        }
    }
}
