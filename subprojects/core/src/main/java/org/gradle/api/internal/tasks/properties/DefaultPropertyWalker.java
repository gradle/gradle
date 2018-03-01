/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.tasks.PropertySpecFactory;
import org.gradle.api.internal.tasks.properties.bean.RootRuntimeBeanNode;
import org.gradle.api.internal.tasks.properties.bean.RuntimeBeanNode;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Queue;

@NonNullApi
public class DefaultPropertyWalker implements PropertyWalker {

    private final PropertyMetadataStore propertyMetadataStore;

    public DefaultPropertyWalker(PropertyMetadataStore propertyMetadataStore) {
        this.propertyMetadataStore = propertyMetadataStore;
    }

    @Override
    public void visitProperties(PropertySpecFactory specFactory, PropertyVisitor visitor, Object bean) {
        Queue<BeanNodeContext> queue = new ArrayDeque<BeanNodeContext>();
        queue.add(new BeanNodeContext(new RootRuntimeBeanNode(bean), queue));
        while (!queue.isEmpty()) {
            BeanNodeContext context = queue.remove();
            context.visit(visitor, specFactory);
        }
    }

    private class BeanNodeContext implements NodeContext {
        private final RuntimeBeanNode currentNode;
        private final Queue<BeanNodeContext> queue;
        private final ParentBeanNodeList parentNodes;

        public BeanNodeContext(RuntimeBeanNode currentNode, Queue<BeanNodeContext> queue, @Nullable ParentBeanNodeList parentNodes) {
            this.currentNode = currentNode;
            this.queue = queue;
            this.parentNodes = parentNodes;
            if (parentNodes != null) {
                parentNodes.checkCycles(currentNode);
            }
        }

        public BeanNodeContext(RuntimeBeanNode currentNode, Queue<BeanNodeContext> queue) {
            this(currentNode, queue, null);
        }

        @Override
        public void addSubProperties(RuntimeBeanNode node) {
            queue.add(new BeanNodeContext(node, queue, new ParentBeanNodeList(parentNodes, currentNode)));
        }

        public void visit(PropertyVisitor visitor, PropertySpecFactory specFactory) {
            currentNode.visitNode(visitor, specFactory, this, propertyMetadataStore);
        }
    }

    private static class ParentBeanNodeList {
        private final ParentBeanNodeList parent;
        private final RuntimeBeanNode node;

        public ParentBeanNodeList(@Nullable ParentBeanNodeList parent, RuntimeBeanNode node) {
            this.parent = parent;
            this.node = node;
        }

        public void checkCycles(RuntimeBeanNode childNode) {
            Preconditions.checkState(
                node.getBean() != childNode.getBean(),
                "Cycles between nested beans are not allowed. Cycle detected between: '%s' and '%s'.",
                node, childNode);
            if (parent != null) {
                parent.checkCycles(childNode);
            }
        }
    }
}
