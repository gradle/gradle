/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.internal.artifacts.ResolveArtifactsBuildOperationType;
import org.gradle.api.internal.artifacts.configurations.ResolutionHost;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.DependencyVerificationOverride;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.work.WorkerLeaseService;

import javax.inject.Inject;

/**
 * Resolves a {@link ResolvedArtifactSet} in a build operation, visiting the results.
 */
public class ResolvedArtifactSetResolver {

    private final WorkerLeaseService workerLeaseService;
    private final BuildOperationExecutor buildOperationExecutor;
    private final DependencyVerificationOverride dependencyVerificationOverride;

    @Inject
    public ResolvedArtifactSetResolver(
        WorkerLeaseService workerLeaseService,
        BuildOperationExecutor buildOperationExecutor,
        DependencyVerificationOverride dependencyVerificationOverride
    ) {
        this.workerLeaseService = workerLeaseService;
        this.buildOperationExecutor = buildOperationExecutor;
        this.dependencyVerificationOverride = dependencyVerificationOverride;
    }

    public void visitInUnmanagedWorkerThread(ResolvedArtifactSet artifacts, ArtifactVisitor visitor, ResolutionHost resolutionHost) {
        // This may be called from an unmanaged thread, so temporarily enlist the current thread
        // as a worker if it is not already so that it can visit the results. It would be better
        // to instead to memoize the results on the first visit so that this is not required.
        workerLeaseService.runAsUnmanagedWorkerThread(() -> visitArtifacts(artifacts, visitor, resolutionHost));
    }

    public void visitArtifacts(ResolvedArtifactSet artifacts, ArtifactVisitor visitor, ResolutionHost resolutionHost) {
        buildOperationExecutor.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                ParallelResolveArtifactSet.wrap(artifacts, buildOperationExecutor).visit(visitor);
                dependencyVerificationOverride.artifactsAccessed(resolutionHost.getDisplayName());
                context.setResult(new ResolveArtifactsBuildOperationType.Result() {});
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                String displayName = "Resolve files of " + resolutionHost.getDisplayName();
                return BuildOperationDescriptor
                    .displayName(displayName)
                    .progressDisplayName(displayName)
                    .details(new ResolveArtifactsBuildOperationType.Details() {
                        @Override
                        @Deprecated
                        public String getConfigurationPath() {
                            return "";
                        }
                    });
            }
        });
    }
}
