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

package org.gradle.api.internal.artifacts.configurations;

import org.gradle.api.internal.artifacts.ivyservice.ResolvedFileCollectionVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactSet;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.tasks.FailureCollectingTaskDependencyResolveContext;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.internal.component.resolution.failure.exception.ArtifactSelectionException;
import org.gradle.internal.logging.text.TreeFormatter;

public class ResolutionBackedFileCollection extends AbstractFileCollection {

    private final SelectedArtifactSet artifacts;
    private final boolean lenient;
    private final ResolutionHost resolutionHost;

    public ResolutionBackedFileCollection(
        SelectedArtifactSet artifacts,
        boolean lenient,
        ResolutionHost resolutionHost,
        TaskDependencyFactory taskDependencyFactory
    ) {
        super(taskDependencyFactory);
        this.artifacts = artifacts;
        this.lenient = lenient;
        this.resolutionHost = resolutionHost;
    }

    public ResolutionHost getResolutionHost() {
        return resolutionHost;
    }

    public boolean isLenient() {
        return lenient;
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        FailureCollectingTaskDependencyResolveContext collectingContext = new FailureCollectingTaskDependencyResolveContext(context);
        artifacts.visitDependencies(collectingContext);
        if (!lenient) {
            resolutionHost.consolidateFailures("dependencies", collectingContext.getFailures()).ifPresent(consolidatedFailure -> {
                resolutionHost.reportProblems(consolidatedFailure);
                context.visitFailure(consolidatedFailure);
            });
        }
    }

    @Override
    public String getDisplayName() {
        return resolutionHost.displayName("files").getDisplayName();
    }

    @Override
    protected void visitContents(FileCollectionStructureVisitor visitor) {
        ResolvedFileCollectionVisitor collectingVisitor = new ResolvedFileCollectionVisitor(visitor);
        artifacts.visitFiles(collectingVisitor, lenient);
        maybeThrowResolutionFailures(collectingVisitor);
    }

    /**
     * If the file collection is not lenient, rethrow any failures that occurred during the visit.
     *
     * @throws ArtifactSelectionException subtypes
     */
    private void maybeThrowResolutionFailures(ResolvedFileCollectionVisitor collectingVisitor) {
        if (!lenient) {
            resolutionHost.rethrowFailuresAndReportProblems("files", collectingVisitor.getFailures());
        }
    }

    @Override
    protected void appendContents(TreeFormatter formatter) {
        formatter.node("contains: " + getDisplayName());
    }

    SelectedArtifactSet getArtifacts() {
        return artifacts;
    }
}
