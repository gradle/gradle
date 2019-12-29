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

import com.google.common.collect.ImmutableList;
import org.gradle.api.Describable;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;

import java.io.File;
import java.util.Optional;

/**
 * Subject which is transformed or the result of a transformation.
 */
public abstract class TransformationSubject implements Describable {

    public static TransformationSubject initial(ResolvableArtifact artifact) {
        return new InitialArtifactTransformationSubject(artifact);
    }

    /**
     * The files which should be transformed.
     */
    public abstract ImmutableList<File> getFiles();

    /**
     * The artifacts which make up this subject.
     */
    public abstract ImmutableList<ResolvableArtifact> getArtifacts();

    /**
     * Component producing this subject.
     *
     * {@link Optional#empty()} if the subject is not produced by a project.
     */
    public abstract Optional<ProjectComponentIdentifier> getProducer();

    /**
     * Creates a subsequent subject by having transformed this subject.
     */
    public abstract TransformationSubject createSubjectFromResult(ImmutableList<File> result);

    @Override
    public String toString() {
        return getDisplayName();
    }

    private static class InitialArtifactTransformationSubject extends TransformationSubject {
        private final ResolvableArtifact artifact;

        public InitialArtifactTransformationSubject(ResolvableArtifact artifact) {
            this.artifact = artifact;
        }

        @Override
        public String getDisplayName() {
            return "artifact " + artifact.getId().getDisplayName();
        }

        @Override
        public ImmutableList<File> getFiles() {
            return ImmutableList.of(artifact.getFile());
        }

        @Override
        public ImmutableList<ResolvableArtifact> getArtifacts() {
            return ImmutableList.of(artifact);
        }

        @Override
        public Optional<ProjectComponentIdentifier> getProducer() {
            ComponentIdentifier componentIdentifier = artifact.getId().getComponentIdentifier();
            if (componentIdentifier instanceof ProjectComponentIdentifier) {
                return Optional.of((ProjectComponentIdentifier) componentIdentifier);
            }
            return Optional.empty();
        }

        @Override
        public TransformationSubject createSubjectFromResult(ImmutableList<File> result) {
            return new SubsequentTransformationSubject(this, artifact, result);
        }
    }

    private static class SubsequentTransformationSubject extends TransformationSubject {
        private final TransformationSubject previous;
        private final ResolvableArtifact inputArtifact;
        private final ImmutableList<File> files;

        public SubsequentTransformationSubject(TransformationSubject previous, ResolvableArtifact inputArtifact, ImmutableList<File> files) {
            this.previous = previous;
            this.inputArtifact = inputArtifact;
            this.files = files;
        }

        @Override
        public ImmutableList<File> getFiles() {
            return files;
        }

        @Override
        public ImmutableList<ResolvableArtifact> getArtifacts() {
            ImmutableList.Builder<ResolvableArtifact> builder = ImmutableList.builderWithExpectedSize(files.size());
            for (File output : files) {
                builder.add(inputArtifact.transformedTo(output));
            }
            return builder.build();
        }

        @Override
        public Optional<ProjectComponentIdentifier> getProducer() {
            return previous.getProducer();
        }

        @Override
        public String getDisplayName() {
            return previous.getDisplayName();
        }

        @Override
        public TransformationSubject createSubjectFromResult(ImmutableList<File> result) {
            return new SubsequentTransformationSubject(this, inputArtifact, result);
        }
    }
}
