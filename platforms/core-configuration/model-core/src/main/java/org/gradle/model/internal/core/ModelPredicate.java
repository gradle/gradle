/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.core;

import javax.annotation.Nullable;

/**
 * A predicate that selects model nodes.
 *
 * <p>Defines a fixed set of criteria that a model node must match. A node is only selected when it matches <em>all</em> non-null criteria.</p>
 */
public abstract class ModelPredicate {
    /**
     * Returns the path of the node to select, or null if path is not relevant.
     *
     * <p>A node will be selected if its path equals the specified path.
     */
    @Nullable
    public ModelPath getPath() {
        return null;
    }

    /**
     * Returns the parent path of the nodes to select, or null if parent is not relevant.
     *
     * <p>A node will be selected if its parent's path equals the specified path.
     */
    @Nullable
    public ModelPath getParent() {
        return null;
    }

    /**
     * Return the path of the scope of the nodes to select, or null if ancestor is not relevant.
     *
     * <p>A node will be selected if its path or one of its ancestor's path equals the specified path.</p>
     */
    @Nullable
    public ModelPath getAncestor() {
        return null;
    }
}
