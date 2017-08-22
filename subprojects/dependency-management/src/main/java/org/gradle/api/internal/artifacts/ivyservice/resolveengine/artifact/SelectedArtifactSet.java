/*
 * Copyright 2016 the original author or authors.
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

/**
 * A container of artifacts that match some criteria. Not every query method is available, depending on which details are available.
 */
public interface SelectedArtifactSet {
    /**
     * Collects the build dependencies required to build the artifacts in this result. Failures to calculate the build dependencies are supplied to the visitor
     */
    void collectBuildDependencies(BuildDependenciesVisitor visitor);

    /**
     * Visits the files and artifacts of this set. Does not include any files or artifacts which could not be selected. Failures to select or resolve artifacts are supplied to the visitor.
     */
    void visitArtifacts(ArtifactVisitor visitor, boolean continueOnSelectionFailure);

}
