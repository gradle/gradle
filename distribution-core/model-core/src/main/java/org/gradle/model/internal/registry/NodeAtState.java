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

package org.gradle.model.internal.registry;

import org.gradle.model.internal.core.ModelNode;
import org.gradle.model.internal.core.ModelPath;

class NodeAtState implements Comparable<NodeAtState> {
    public final ModelPath path;
    public final ModelNode.State state;

    public NodeAtState(ModelPath path, ModelNode.State state) {
        this.path = path;
        this.state = state;
    }

    @Override
    public String toString() {
        return "node " + path + " at state " + state;
    }

    @Override
    public int compareTo(NodeAtState other) {
        int diff = path.compareTo(other.path);
        if (diff != 0) {
            return diff;
        }
        return state.compareTo(other.state);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        NodeAtState other = (NodeAtState) obj;
        return path.equals(other.path) && state.equals(other.state);
    }

    @Override
    public int hashCode() {
        return path.hashCode() ^ state.hashCode();
    }
}
