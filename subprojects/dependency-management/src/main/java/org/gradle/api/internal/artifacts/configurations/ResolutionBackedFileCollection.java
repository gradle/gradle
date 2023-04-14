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
import org.gradle.internal.logging.text.TreeFormatter;

public class ResolutionBackedFileCollection extends AbstractFileCollection {
    private final ResolutionResultProvider<SelectedArtifactSet> resultProvider;
    private final boolean lenient;
    private final ResolutionHost resolutionHost;
    private SelectedArtifactSet selectedArtifacts;

    public ResolutionBackedFileCollection(
        ResolutionResultProvider<SelectedArtifactSet> resultProvider,
        boolean lenient,
        ResolutionHost resolutionHost,
        TaskDependencyFactory taskDependencyFactory
    ) {
        super(taskDependencyFactory);
        this.resultProvider = resultProvider;
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
        SelectedArtifactSet selected = resultProvider.getTaskDependencyValue();
        FailureCollectingTaskDependencyResolveContext collectingContext = new FailureCollectingTaskDependencyResolveContext(context);
        selected.visitDependencies(collectingContext);
        if (!lenient) {
            resolutionHost.mapFailure("task dependencies", collectingContext.getFailures()).ifPresent(context::visitFailure);
        }
    }

    @Override
    public String getDisplayName() {
        return resolutionHost.displayName("files").getDisplayName();
    }

    @Override
    protected void visitContents(FileCollectionStructureVisitor visitor) {
        ResolvedFileCollectionVisitor collectingVisitor = new ResolvedFileCollectionVisitor(visitor);
        getSelectedArtifacts().visitFiles(collectingVisitor, lenient);
        if (!lenient) {
            resolutionHost.rethrowFailure("files", collectingVisitor.getFailures());
        }
    }

    @Override
    protected void appendContents(TreeFormatter formatter) {
        formatter.node("contains: " + getDisplayName());
    }

    SelectedArtifactSet getSelectedArtifacts() {
        if (selectedArtifacts == null) {
            selectedArtifacts = resultProvider.getValue();
        }
        return selectedArtifacts;
    }
}
