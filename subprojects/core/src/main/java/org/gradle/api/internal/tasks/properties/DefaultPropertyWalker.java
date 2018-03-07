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

import com.google.common.base.Equivalence;
import com.google.common.base.Function;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.tasks.PropertySpecFactory;
import org.gradle.api.internal.tasks.properties.bean.RootRuntimeBeanNode;
import org.gradle.api.internal.tasks.properties.bean.RuntimeBeanNode;

import java.util.ArrayDeque;
import java.util.Queue;

@NonNullApi
public class DefaultPropertyWalker implements PropertyWalker {
    private static final Equivalence<RuntimeBeanNode> SAME_BEANS = Equivalence.identity().onResultOf(new Function<RuntimeBeanNode, Object>() {
        @Override
        public Object apply(RuntimeBeanNode input) {
            return input.getBean();
        }
    });

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

    private class BeanNodeContext extends AbstractNodeContext<RuntimeBeanNode> {
        private final Queue<BeanNodeContext> queue;

        public BeanNodeContext(RuntimeBeanNode currentNode, BeanNodeContext parent, Queue<BeanNodeContext> queue) {
            super(currentNode, parent);
            this.queue = queue;
            checkCycles();
        }

        public BeanNodeContext(RuntimeBeanNode currentNode, Queue<BeanNodeContext> queue) {
            super(currentNode);
            this.queue = queue;
        }

        @Override
        public void addSubProperties(RuntimeBeanNode node) {
            queue.add(new BeanNodeContext(node, this, queue));
        }

        public void visit(PropertyVisitor visitor, PropertySpecFactory specFactory) {
            getCurrentNode().visitNode(visitor, specFactory, this, propertyMetadataStore);
        }

        @Override
        protected Equivalence<RuntimeBeanNode> getNodeEquivalence() {
            return SAME_BEANS;
        }
    }
}
