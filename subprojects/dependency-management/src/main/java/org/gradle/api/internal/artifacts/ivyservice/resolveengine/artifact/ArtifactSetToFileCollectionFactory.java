/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.internal.artifacts.ivyservice.ResolvedArtifactCollectingVisitor;
import org.gradle.api.internal.artifacts.ivyservice.ResolvedFileCollectionVisitor;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.Set;

@ServiceScope(Scopes.BuildSession.class)
public class ArtifactSetToFileCollectionFactory {
    private final BuildOperationExecutor buildOperationExecutor;

    public ArtifactSetToFileCollectionFactory(BuildOperationExecutor buildOperationExecutor) {
        this.buildOperationExecutor = buildOperationExecutor;
    }

    /**
     * Presents the contents of the given artifacts as a partial {@link FileCollectionInternal} implementation.
     *
     * <p>This produces only a minimal implementation to use for artifact sets loaded from the configuration cache
     * Over time, this should be merged with the FileCollection implementation in DefaultConfiguration
     */
    public FileCollectionInternal asFileCollection(ResolvedArtifactSet artifacts) {
        return new AbstractFileCollection() {
            @Override
            public String getDisplayName() {
                return "files";
            }

            @Override
            protected void visitContents(FileCollectionStructureVisitor visitor) {
                ResolvedFileCollectionVisitor collectingVisitor = new ResolvedFileCollectionVisitor(visitor);
                ParallelResolveArtifactSet.wrap(artifacts, buildOperationExecutor).visit(collectingVisitor);
                if (!collectingVisitor.getFailures().isEmpty()) {
                    throw UncheckedException.throwAsUncheckedException(collectingVisitor.getFailures().iterator().next());
                }
            }
        };
    }

    /**
     * Presents the contents of the given artifacts as a supplier of {@link ResolvedArtifactResult} instances.
     *
     * <p>Over time, this should be merged with the ArtifactCollection implementation in DefaultConfiguration
     */
    public Set<ResolvedArtifactResult> asResolvedArtifacts(ResolvedArtifactSet artifacts) {
        ResolvedArtifactCollectingVisitor collectingVisitor = new ResolvedArtifactCollectingVisitor();
        ParallelResolveArtifactSet.wrap(artifacts, buildOperationExecutor).visit(collectingVisitor);
        if (!collectingVisitor.getFailures().isEmpty()) {
            throw UncheckedException.throwAsUncheckedException(collectingVisitor.getFailures().iterator().next());
        }
        return collectingVisitor.getArtifacts();
    }
}
