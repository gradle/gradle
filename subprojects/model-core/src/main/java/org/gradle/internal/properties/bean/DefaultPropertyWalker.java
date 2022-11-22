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

package org.gradle.internal.properties.bean;

import org.gradle.api.NonNullApi;
import org.gradle.internal.properties.PropertyVisitor;
import org.gradle.internal.properties.annotations.TypeMetadataStore;
import org.gradle.internal.reflect.validation.TypeValidationContext;

import java.util.ArrayDeque;
import java.util.Queue;

@NonNullApi
public class DefaultPropertyWalker implements PropertyWalker {
    private final RuntimeBeanNodeFactory nodeFactory;

    public DefaultPropertyWalker(TypeMetadataStore typeMetadataStore, ImplementationResolver implementationResolver) {
        this.nodeFactory = new RuntimeBeanNodeFactory(typeMetadataStore, implementationResolver);
    }

    @Override
    public void visitProperties(Object bean, TypeValidationContext validationContext, PropertyVisitor visitor) {
        Queue<RuntimeBeanNode<?>> queue = new ArrayDeque<RuntimeBeanNode<?>>();
        queue.add(nodeFactory.createRoot(bean));
        while (!queue.isEmpty()) {
            RuntimeBeanNode<?> node = queue.remove();
            node.visitNode(visitor, queue, nodeFactory, validationContext);
        }
    }
}
