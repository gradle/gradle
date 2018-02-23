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

abstract class ModelListener extends ModelPredicate {
    /**
     * Invoked once for each node when the node reaches the {@link org.gradle.model.internal.core.ModelNode.State#Discovered} state
     * if the node matches the criteria specified by this listener.
     */
    public abstract void onDiscovered(ModelNodeInternal node);
}
