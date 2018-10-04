/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.Buildable;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.transform.ArtifactTransformException;
import org.gradle.api.artifacts.transform.TransformInvocationException;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;

import java.io.File;
import java.util.List;
import java.util.Map;

class ArtifactTransformingVisitor implements ArtifactVisitor {
    private final ArtifactVisitor visitor;
    private final AttributeContainerInternal target;
    private final Map<ComponentArtifactIdentifier, TransformationOperation> artifactResults;
    private final Map<File, TransformationOperation> fileResults;

    ArtifactTransformingVisitor(ArtifactVisitor visitor, AttributeContainerInternal target, Map<ComponentArtifactIdentifier, TransformationOperation> artifactResults, Map<File, TransformationOperation> fileResults) {
        this.visitor = visitor;
        this.target = target;
        this.artifactResults = artifactResults;
        this.fileResults = fileResults;
    }

    @Override
    public void visitArtifact(DisplayName variantName, AttributeContainer variantAttributes, ResolvableArtifact artifact) {
        TransformationOperation operation = artifactResults.get(artifact.getId());
        if (operation.getFailure() != null) {
            visitFailureWithContext(operation.getFailure());
            return;
        }

        ResolvedArtifact sourceArtifact = artifact.toPublicView();
        List<File> transformedFiles = operation.getResult();
        assert transformedFiles != null;
        TaskDependency buildDependencies = ((Buildable) artifact).getBuildDependencies();

        for (File output : transformedFiles) {
            IvyArtifactName artifactName = DefaultIvyArtifactName.forFile(output, sourceArtifact.getClassifier());
            ComponentArtifactIdentifier newId = new ComponentFileArtifactIdentifier(sourceArtifact.getId().getComponentIdentifier(), artifactName);
            DefaultResolvedArtifact resolvedArtifact = new DefaultResolvedArtifact(sourceArtifact.getModuleVersion().getId(), artifactName, newId, buildDependencies, output);
            visitor.visitArtifact(variantName, target, resolvedArtifact);
        }
    }

    @Override
    public void visitFailure(Throwable failure) {
        visitor.visitFailure(failure);
    }

    @Override
    public boolean includeFiles() {
        return visitor.includeFiles();
    }

    @Override
    public boolean requireArtifactFiles() {
        return visitor.requireArtifactFiles();
    }

    @Override
    public void visitFile(ComponentArtifactIdentifier artifactIdentifier, DisplayName variantName, AttributeContainer variantAttributes, File file) {
        TransformationOperation operation = fileResults.get(file);
        if (operation.getFailure() != null) {
            visitFailureWithContext(operation.getFailure());
            return;
        }

        List<File> result = operation.getResult();
        assert result != null;
        for (File outputFile : result) {
            visitor.visitFile(new ComponentFileArtifactIdentifier(artifactIdentifier.getComponentIdentifier(), outputFile.getName()), variantName, target, outputFile);
        }
    }

    private void visitFailureWithContext(Throwable failure) {
        if (failure instanceof TransformInvocationException) {
            TransformInvocationException invocationException = (TransformInvocationException) failure;
            visitor.visitFailure(new ArtifactTransformException(invocationException.getInput(), target, invocationException.getTransformImplementation(), failure.getCause()));
        } else {
            visitor.visitFailure(failure);
        }
    }
}
