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

import org.gradle.api.Named;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.internal.tasks.properties.TypeMetadata;
import org.gradle.internal.reflect.TypeValidationContext;

import javax.annotation.Nullable;
import java.util.Queue;

class IterableRuntimeBeanNode extends RuntimeBeanNode<Iterable<?>> {
    public IterableRuntimeBeanNode(RuntimeBeanNode<?> parentNode, String propertyName, Iterable<?> iterable, TypeMetadata typeMetadata) {
        super(parentNode, propertyName, iterable, typeMetadata);
    }

    private static String determinePropertyName(@Nullable Object input, int count) {
        String prefix = input instanceof Named ? ((Named) input).getName() : "";
        return prefix + "$" + count;
    }

    @Override
    public void visitNode(PropertyVisitor visitor, Queue<RuntimeBeanNode<?>> queue, RuntimeBeanNodeFactory nodeFactory, TypeValidationContext validationContext) {
        int count = 0;
        for (Object input : getBean()) {
            String propertyName = determinePropertyName(input, count);
            count++;
            queue.add(createChildNode(propertyName, input, nodeFactory));
        }
    }
}
