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

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;

import javax.annotation.Nullable;

public class PropertyNode extends AbstractBeanNode<PropertyNode> {
    private final Object bean;

    public PropertyNode(@Nullable String parentPropertyName, Object bean) {
        super(parentPropertyName, Preconditions.checkNotNull(bean, "Null is not allowed as nested property '" + parentPropertyName + "'").getClass());
        this.bean = bean;
    }

    public Object getBean() {
        return bean;
    }

    public Iterable<PropertyNode> asIterable(final NestedBeanContext<PropertyNode> context) {
        return Iterables.transform((Iterable<?>) bean, new Function<Object, PropertyNode>() {
            private int count = 0;

            @Override
            public PropertyNode apply(@Nullable Object input) {
                String nestedPropertyName = getQualifiedPropertyName("$" + count++);
                return context.createNode(nestedPropertyName, Preconditions.checkNotNull(input, "Null is not allowed as nested property '" + nestedPropertyName + "'"));
            }
        });
    }
}
