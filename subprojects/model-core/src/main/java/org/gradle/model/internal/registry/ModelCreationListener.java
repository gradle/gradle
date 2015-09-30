/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.model.internal.registry;

import org.gradle.model.internal.core.ModelPredicate;

abstract class ModelCreationListener extends ModelPredicate {
    /**
     * Invoked for each node that matches the criteria specified by {@link #getPath()}, {@link #getParent()}, {@link #getAncestor()} <em>and</em> {@link #getType()},
     * or every node if no criteria specified. Stops notifying listener with further nodes when this method returns true.
     *
     * @return true if this listener should no longer receive any notifications of additional nodes.
     */
    public abstract boolean onCreate(ModelNodeInternal node);
}
