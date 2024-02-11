/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact;

import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.ImmutableCapabilities;

import java.io.File;

public class ArtifactVisitorToResolvedFileVisitorAdapter implements ArtifactVisitor {
    private final ResolvedFileVisitor visitor;

    public ArtifactVisitorToResolvedFileVisitorAdapter(ResolvedFileVisitor visitor) {
        this.visitor = visitor;
    }

    @Override
    public FileCollectionStructureVisitor.VisitType prepareForVisit(FileCollectionInternal.Source source) {
        return visitor.prepareForVisit(source);
    }

    public void visitFile(File file) {
        visitor.visitFile(file);
    }

    @Override
    public void visitArtifact(DisplayName variantName, AttributeContainer variantAttributes, ImmutableCapabilities capabilities, ResolvableArtifact artifact) {
        visitor.visitFile(artifact.getFile());
    }

    @Override
    public void visitFailure(Throwable failure) {
        visitor.visitFailure(failure);
    }

    @Override
    public void endVisitCollection(FileCollectionInternal.Source source) {
        visitor.endVisitCollection(source);
    }

    @Override
    public boolean requireArtifactFiles() {
        return true;
    }
}
