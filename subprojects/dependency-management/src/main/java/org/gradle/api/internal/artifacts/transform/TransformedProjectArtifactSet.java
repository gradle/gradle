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

import org.gradle.api.Action;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.EndCollection;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.Try;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.io.File;
import java.util.Collection;

/**
 * An artifact set containing transformed project artifacts.
 */
public class TransformedProjectArtifactSet implements TransformedArtifactSet, FileCollectionInternal.Source, ResolvedArtifactSet.Artifacts {

    private final ComponentVariantIdentifier targetVariant;
    private final Collection<TransformationNode> transformedArtifacts;

    public TransformedProjectArtifactSet(
        ComponentVariantIdentifier targetVariant,
        Collection<TransformationNode> transformedArtifacts
    ) {
        this.targetVariant = targetVariant;
        this.transformedArtifacts = transformedArtifacts;
    }

    public ComponentVariantIdentifier getTargetVariant() {
        return targetVariant;
    }

    public Collection<TransformationNode> getTransformedArtifacts() {
        return transformedArtifacts;
    }

    @Override
    public void visit(Visitor visitor) {
        FileCollectionStructureVisitor.VisitType visitType = visitor.prepareForVisit(this);
        if (visitType == FileCollectionStructureVisitor.VisitType.NoContents) {
            visitor.visitArtifacts(new EndCollection(this));
        } else {
            visitor.visitArtifacts(this);
        }
    }

    @Override
    public void startFinalization(BuildOperationQueue<RunnableBuildOperation> actions, boolean requireFiles) {
        // TODO - should add any nodes that have not been executed
    }

    @Override
    public void visit(ArtifactVisitor visitor) {
        DisplayName displayName = Describables.of(targetVariant.getComponentId());
        for (TransformationNode node : transformedArtifacts) {
            node.executeIfNotAlready();
            Try<TransformationSubject> transformedSubject = node.getTransformedSubject();
            if (transformedSubject.isSuccessful()) {
                for (File file : transformedSubject.get().getFiles()) {
                    visitor.visitArtifact(displayName, targetVariant.getAttributes(), targetVariant.getCapabilities(), node.getInputArtifact().transformedTo(file));
                }
            } else {
                Throwable failure = transformedSubject.getFailure().get();
                visitor.visitFailure(new TransformException(String.format("Failed to transform %s to match attributes %s.", node.getInputArtifact().getId().getDisplayName(), targetVariant.getAttributes()), failure));
            }
        }
        visitor.endVisitCollection(this);
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        if (!transformedArtifacts.isEmpty()) {
            context.add(new DefaultTransformationDependency(transformedArtifacts));
        }
    }

    @Override
    public void visitTransformSources(TransformSourceVisitor visitor) {
        for (TransformationNode transformationNode : transformedArtifacts) {
            visitor.visitTransform(transformationNode);
        }
    }

    @Override
    public void visitExternalArtifacts(Action<ResolvableArtifact> visitor) {
        throw new UnsupportedOperationException("Should not be called.");
    }
}
