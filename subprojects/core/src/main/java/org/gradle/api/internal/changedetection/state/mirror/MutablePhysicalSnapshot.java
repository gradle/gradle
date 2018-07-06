/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.changedetection.state.mirror;

/**
 * A physical snapshot which allows adding children.
 */
public interface MutablePhysicalSnapshot extends PhysicalSnapshot {

    /**
     * Adds the provided snapshot relative to the current snapshot.
     *
     * <p>
     * The relative path is {@code segments[offset..]}.
     * If {@code offset == segments.length}, then the current snapshot is returned
     * and it will be checked if the current snapshot has the same type as the provided snapshot.
     * </p>
     *
     * @param segments relative path segments
     * @param offset where the relative path starts in segments
     * @param snapshot the snapshot to add to the tree
     * @return the {@link PhysicalSnapshot} at the queried position, either snapshot or the pre-existing snapshot.
     */
    MutablePhysicalSnapshot add(String[] segments, int offset, MutablePhysicalSnapshot snapshot);
}
