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
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;

import java.io.File;
import java.util.List;
import java.util.Map;

class ArtifactTransformingVisitor implements ArtifactVisitor {
    private final ArtifactVisitor visitor;
    private final AttributeContainerInternal target;
    private final Map<ResolvableArtifact, TransformArtifactOperation> artifactResults;
    private final Map<File, TransformFileOperation> fileResults;

    ArtifactTransformingVisitor(ArtifactVisitor visitor, AttributeContainerInternal target, Map<ResolvableArtifact, TransformArtifactOperation> artifactResults, Map<File, TransformFileOperation> fileResults) {
        this.visitor = visitor;
        this.target = target;
        this.artifactResults = artifactResults;
        this.fileResults = fileResults;
    }

    @Override
    public void visitArtifact(AttributeContainer variant, ResolvableArtifact artifact) {
        TransformArtifactOperation operation = artifactResults.get(artifact);
        if (operation.getFailure() != null) {
            visitor.visitFailure(operation.getFailure());
            return;
        }

        ResolvedArtifact sourceArtifact = artifact.toPublicView();
        List<File> transformedFiles = operation.getResult();
        TaskDependency buildDependencies = ((Buildable) artifact).getBuildDependencies();

        for (File output : transformedFiles) {
            IvyArtifactName artifactName = DefaultIvyArtifactName.forFile(output, sourceArtifact.getClassifier());
            ComponentArtifactIdentifier newId = new ComponentFileArtifactIdentifier(sourceArtifact.getId().getComponentIdentifier(), artifactName);
            DefaultResolvedArtifact resolvedArtifact = new DefaultResolvedArtifact(sourceArtifact.getModuleVersion().getId(), artifactName, newId, buildDependencies, output);
            visitor.visitArtifact(target, resolvedArtifact);
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
    public void visitFile(ComponentArtifactIdentifier artifactIdentifier, AttributeContainer variant, File file) {
        TransformFileOperation operation = fileResults.get(file);
        if (operation.getFailure() != null) {
            visitor.visitFailure(operation.getFailure());
            return;
        }

        List<File> result = operation.getResult();
        for (File outputFile : result) {
            visitor.visitFile(new ComponentFileArtifactIdentifier(artifactIdentifier.getComponentIdentifier(), outputFile.getName()), target, outputFile);
        }
    }
}
