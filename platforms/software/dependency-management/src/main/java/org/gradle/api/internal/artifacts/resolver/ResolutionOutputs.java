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

package org.gradle.api.internal.artifacts.resolver;

import org.gradle.api.Action;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.file.FileCollection;
import org.gradle.internal.HasInternalProtocol;

/**
 * The outputs of a graph resolution. All results on this type are lazy. Resolution is only performed
 * when the results are accessed.
 *
 * TODO: This type is intended to be made public in future Gradle versions, in some form.
 */
@HasInternalProtocol
public interface ResolutionOutputs {

    /**
     * A {@link FileCollection} containing all resolved files. The returned collection
     * carries all task dependencies required to build the resolved files, and when used
     * as a task input the files will be built before the task executes.
     */
    FileCollection getFiles();

    /**
     * A set of resolved artifacts corresponding to the resolved files.
     */
    ArtifactCollection getArtifacts();

    /**
     * Creates a view of the resolved graph that can be used to filter the resolved artifacts,
     * perform transformations on the resolved artifacts, or reselect the variants of the resolved
     * graph to adjacent artifacts.
     */
    ArtifactView artifactView(Action<? super ArtifactView.ViewConfiguration> action);

}
