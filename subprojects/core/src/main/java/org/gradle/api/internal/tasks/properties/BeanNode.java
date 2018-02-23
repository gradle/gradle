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

public abstract class BeanNode extends AbstractPropertyNode<BeanNode> {

    public static BeanNode create(@Nullable String propertyName, Object bean) {
        if (bean instanceof Map<?, ?>) {
            return new MapBeanNode(propertyName, (Map<?, ?>) bean);
        }
        if (bean instanceof Iterable<?>) {
            return new IterableBeanNode(propertyName, (Iterable<?>) bean);
        }
        return new SimpleBeanNode(propertyName, bean);
    }

    protected BeanNode(@Nullable String propertyName, Object bean) {
        super(propertyName, Preconditions.checkNotNull(bean, "Null is not allowed as nested property '" + propertyName + "'").getClass());
    }

    public abstract Object getBean();

    protected BeanNode createChildNode(String propertyName, @Nullable Object input) {
        String qualifiedPropertyName = getQualifiedPropertyName(propertyName);
        Object bean = Preconditions.checkNotNull(input, "Null is not allowed as nested property '" + qualifiedPropertyName + "'");
        return BeanNode.create(qualifiedPropertyName, bean);
    }

    private static class SimpleBeanNode extends BeanNode {

        private final Object bean;

        public SimpleBeanNode(@Nullable String propertyName, Object bean) {
            super(propertyName, bean);
            this.bean = bean;
        }

        @Override
        public Object getBean() {
            return bean;
        }

        @Override
        public boolean isIterable() {
            return false;
        }

        @Override
        public Iterator<BeanNode> getIterator() {
            throw new UnsupportedOperationException();
        }
    }

    private static class IterableBeanNode extends BeanNode {
        private final Iterable<?> iterable;

        public IterableBeanNode(@Nullable String propertyName, Iterable<?> iterable) {
            super(propertyName, iterable);
            this.iterable = iterable;
        }

        @Override
        public boolean isIterable() {
            return true;
        }

        @Override
        public Iterator<BeanNode> getIterator() {
            return Iterators.transform(iterable.iterator(), new Function<Object, BeanNode>() {
                private int count = 0;

                @Override
                public BeanNode apply(@Nullable Object input) {
                    return createChildNode(determinePropertyName(input), input);
                }

                private String determinePropertyName(@Nullable Object input) {
                    if (input instanceof Named) {
                        return ((Named) input).getName();
                    }
                    return "$" + count++;
                }
            });
        }

        @Override
        public Object getBean() {
            return iterable;
        }
    }

    private static class MapBeanNode extends BeanNode {
        private final Map<?, ?> map;

        public MapBeanNode(@Nullable String propertyName, Map<?, ?> map) {
            super(propertyName, map);
            this.map = map;
        }

        @Override
        public boolean isIterable() {
            return true;
        }

        @Override
        public Iterator<BeanNode> getIterator() {
            return Iterators.transform(map.entrySet().iterator(), new Function<Map.Entry<?, ?>, BeanNode>() {
                @Override
                public BeanNode apply(Map.Entry<?, ?> input) {
                    return createChildNode(input.getKey().toString(), input.getValue());
                }
            });
        }

        @Override
        public Object getBean() {
            return map;
        }
    }
}
