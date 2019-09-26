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

package org.gradle.api.internal.tasks.properties.bean;

import com.google.common.base.Preconditions;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.internal.tasks.properties.TypeMetadata;
import org.gradle.internal.reflect.TypeValidationContext;

import java.util.Map;
import java.util.Queue;

class MapRuntimeBeanNode extends RuntimeBeanNode<Map<?, ?>> {
    public MapRuntimeBeanNode(RuntimeBeanNode<?> parentNode, String propertyName, Map<?, ?> map, TypeMetadata typeMetadata) {
        super(parentNode, propertyName, map, typeMetadata);
    }

    @Override
    public void visitNode(PropertyVisitor visitor, Queue<RuntimeBeanNode<?>> queue, RuntimeBeanNodeFactory nodeFactory, TypeValidationContext validationContext) {
        for (Map.Entry<?, ?> entry : getBean().entrySet()) {
            RuntimeBeanNode<?> childNode = createChildNode(
                Preconditions.checkNotNull(entry.getKey(), "Null keys in nested map '%s' are not allowed.", getPropertyName()).toString(),
                entry.getValue(),
                nodeFactory);
            queue.add(childNode);
        }
    }
}
