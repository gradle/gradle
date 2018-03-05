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

package org.gradle.api.internal.tasks.properties;

import com.google.common.base.Preconditions;
import org.gradle.internal.util.BiFunction;

import javax.annotation.Nullable;

public abstract class AbstractNodeContext<T extends PropertyNode> implements NodeContext<T> {
    private final T currentNode;
    private final ParentBeanNodeList<T> parentNodes;

    public AbstractNodeContext(T currentNode) {
        this(currentNode, (ParentBeanNodeList<T>) null);
    }

    public AbstractNodeContext(T currentNode, AbstractNodeContext<T> parent) {
        this(currentNode, new ParentBeanNodeList<T>(parent.parentNodes, parent.currentNode));
    }

    private AbstractNodeContext(T currentNode, @Nullable ParentBeanNodeList<T> parentNodes) {
        this.currentNode = currentNode;
        this.parentNodes = parentNodes;
    }

    public T getCurrentNode() {
        return currentNode;
    }

    public boolean currentNodeCreatesCycle() {
        return parentNodes != null && parentNodes.findNodeCreatingCycle(currentNode, getNodeEquals()) != null;
    }

    public void checkCycles() {
        T nodeCreatingCycle = parentNodes != null ? parentNodes.findNodeCreatingCycle(currentNode, getNodeEquals()) : null;
        Preconditions.checkState(
            nodeCreatingCycle == null,
            "Cycles between nested beans are not allowed. Cycle detected between: '%s' and '%s'.",
            nodeCreatingCycle, currentNode);
    }

    protected abstract BiFunction<Boolean, T, T> getNodeEquals();

    private static class ParentBeanNodeList<T extends PropertyNode> {
        private final ParentBeanNodeList<T> parent;
        private final T node;

        public ParentBeanNodeList(@Nullable ParentBeanNodeList<T> parent, T node) {
            this.parent = parent;
            this.node = node;
        }

        @Nullable
        public T findNodeCreatingCycle(T childNode, BiFunction<Boolean, T, T> nodeEquals) {
            if (nodeEquals.apply(node, childNode)) {
                return node;
            }
            if (parent == null) {
                return null;
            }
            return parent.findNodeCreatingCycle(childNode, nodeEquals);
        }
    }
}
