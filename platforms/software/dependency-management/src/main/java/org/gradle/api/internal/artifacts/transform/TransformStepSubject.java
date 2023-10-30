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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;

import java.io.File;

/**
 * Transform subject is either an initial artifact for the transform chain or a result of a previous transform step.
 */
public abstract class TransformStepSubject implements Describable {

    public static TransformStepSubject initial(ResolvableArtifact artifact) {
        return new Initial(artifact);
    }

    /**
     * The files which should be transformed.
     */
    public abstract ImmutableList<File> getFiles();

    /**
     * Component identifier of the initial subject.
     */
    public abstract ComponentIdentifier getInitialComponentIdentifier();

    /**
     * Creates a subsequent subject by having transformed this subject.
     */
    public TransformStepSubject createSubjectFromResult(ImmutableList<File> result) {
        return new Transformed(this, result);
    }

    private static class Initial extends TransformStepSubject {
        private final ResolvableArtifact artifact;

        public Initial(ResolvableArtifact artifact) {
            this.artifact = artifact;
        }

        @Override
        public ImmutableList<File> getFiles() {
            return ImmutableList.of(artifact.getFile());
        }

        @Override
        public String getDisplayName() {
            return artifact.getId().getDisplayName();
        }

        @Override
        public ComponentIdentifier getInitialComponentIdentifier() {
            return artifact.getId().getComponentIdentifier();
        }
    }

    private static class Transformed extends TransformStepSubject {
        private final TransformStepSubject previous;
        private final ImmutableList<File> files;

        public Transformed(TransformStepSubject previous, ImmutableList<File> files) {
            this.previous = previous;
            this.files = files;
        }

        @Override
        public ImmutableList<File> getFiles() {
            return files;
        }

        @Override
        public ComponentIdentifier getInitialComponentIdentifier() {
            return previous.getInitialComponentIdentifier();
        }

        @Override
        public String getDisplayName() {
            return previous.getDisplayName();
        }

        @Override
        public String toString() {
            return getDisplayName();
        }
    }
}
