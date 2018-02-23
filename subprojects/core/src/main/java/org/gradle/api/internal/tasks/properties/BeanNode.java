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
import com.google.common.collect.Iterators;
import org.gradle.api.Named;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Map;

public class BeanNode extends AbstractPropertyNode<BeanNode> {
    private final Object bean;

    public BeanNode(@Nullable String propertyName, Object bean) {
        super(propertyName, Preconditions.checkNotNull(bean, "Null is not allowed as nested property '" + propertyName + "'").getClass());
        this.bean = bean;
    }

    public Object getBean() {
        return bean;
    }

    @Override
    public Iterator<BeanNode> getIterator() {
        if (bean instanceof Map<?, ?>) {
            return Iterators.transform(((Map<?, ?>) bean).entrySet().iterator(), new Function<Map.Entry<?, ?>, BeanNode>() {
                @Override
                public BeanNode apply(Map.Entry<?, ?> input) {
                    return createBeanNode(input.getKey().toString(), input.getValue());
                }
            });
        }
        return Iterators.transform(((Iterable<?>) bean).iterator(), new Function<Object, BeanNode>() {
            private int count = 0;

            @Override
            public BeanNode apply(@Nullable Object input) {
                return createBeanNode(determinePropertyName(input), input);
            }

            private String determinePropertyName(@Nullable Object input) {
                if (input instanceof Named) {
                    return ((Named) input).getName();
                }
                return "$" + count++;
            }
        });
    }

    private BeanNode createBeanNode(String propertyName, @Nullable Object input) {
        String qualifiedPropertyName = getQualifiedPropertyName(propertyName);
        Object bean = Preconditions.checkNotNull(input, "Null is not allowed as nested property '" + qualifiedPropertyName + "'");
        return new BeanNode(qualifiedPropertyName, bean);
    }
}
