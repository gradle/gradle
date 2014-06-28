/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.java.archives;

import groovy.lang.Closure;
import org.gradle.api.Action;

/**
 * Specifies how the entries of multiple manifests should be merged together.
 */
public interface ManifestMergeSpec {
    /**
     * Adds a merge path to a manifest that should be merged into the base manifest. A merge path can be either another
     * {@link org.gradle.api.java.archives.Manifest} or a path that is evaluated as per
     * {@link org.gradle.api.Project#files(Object...)} . If multiple merge paths are specified, the manifest are merged
     * in the order in which they are added.
     * 
     * @param mergePaths The paths of manifests to be merged
     * @return this
     */
    ManifestMergeSpec from(Object... mergePaths);

    /**
     * Adds an action to be applied to each key-value tuple in a merge operation. If multiple merge paths are specified,
     * the action is called for each key-value tuple of each merge operation. The given action is called with a
     * {@link org.gradle.api.java.archives.ManifestMergeDetails} as its parameter. Actions are executed
     * in the order added.
     *
     * @param mergeAction A merge action to be executed.
     * @return this
     */
    ManifestMergeSpec eachEntry(Action<? super ManifestMergeDetails> mergeAction);

    /**
     * Adds an action to be applied to each key-value tuple in a merge operation. If multiple merge paths are specified,
     * the action is called for each key-value tuple of each merge operation. The given closure is called with a
     * {@link org.gradle.api.java.archives.ManifestMergeDetails} as its parameter. Actions are executed
     * in the order added.
     *
     * @param mergeAction The action to execute.
     * @return this
     */
    ManifestMergeSpec eachEntry(Closure mergeAction);
}