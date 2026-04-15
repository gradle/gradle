/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.artifacts.result.artifact;

import org.gradle.api.artifacts.ArtifactCollection;

public interface ArtifactGraphInternal extends ArtifactGraph {

    /**
     * Get the node with the given index. Nodes are cached, so repeated
     * calls to this method with the same index will return the same instance.
     */
    ArtifactNode getNode(int nodeIndex);

    /**
     * Get the artifacts for the node with the given index. Returned artifact
     * collections are not cached.
     */
    ArtifactCollection artifactsFor(int nodeIndex);

}
