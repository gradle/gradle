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

import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;

import java.io.File;

public interface ResolvedFileVisitor {
    /**
     * Called prior to scheduling resolution of a set of artifacts. Should be called in result order.
     */
    default FileCollectionStructureVisitor.VisitType prepareForVisit(FileCollectionInternal.Source source) {
        return FileCollectionStructureVisitor.VisitType.Visit;
    }

    /**
     * Visits an artifact file.
     *
     * <p>Note that a given artifact may be visited multiple times. The implementation is required to filter out duplicates.</p>
     */
    void visitFile(File file);

    /**
     * Called when some problem occurs visiting some element of the set. Visiting may continue.
     */
    void visitFailure(Throwable failure);

    /**
     * Called after a set of artifacts has been visited.
     */
    default void endVisitCollection(FileCollectionInternal.Source source) {
    }
}
