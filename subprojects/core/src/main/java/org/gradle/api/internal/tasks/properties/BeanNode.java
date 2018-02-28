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
import org.gradle.api.Named;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Queue;

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

    protected BeanNode(@Nullable String propertyName, Class<?> beanClass) {
        super(propertyName, beanClass);
    }

    public abstract Object getBean();
}

abstract class BaseBeanNode<T> extends BeanNode {

    private final T bean;

    protected BaseBeanNode(@Nullable String propertyName, T bean) {
        super(propertyName, Preconditions.checkNotNull(bean, "Null is not allowed as nested property '%s'", propertyName).getClass());
        this.bean = bean;
    }

    @Override
    public T getBean() {
        return bean;
    }

    protected BeanNode createChildNode(String propertyName, @Nullable Object input) {
        String qualifiedPropertyName = getQualifiedPropertyName(propertyName);
        Object bean = Preconditions.checkNotNull(input, "Null is not allowed as nested property '%s'", qualifiedPropertyName);
        return BeanNode.create(qualifiedPropertyName, bean);
    }
}

class SimpleBeanNode extends BaseBeanNode<Object> {
    public SimpleBeanNode(@Nullable String propertyName, Object bean) {
        super(propertyName, bean);
    }

    @Override
    public boolean unpackToQueue(Queue<BeanNode> queue) {
        return false;
    }
}

class IterableBeanNode extends BaseBeanNode<Iterable<?>> {
    public IterableBeanNode(@Nullable String propertyName, Iterable<?> iterable) {
        super(propertyName, iterable);
    }

    @Override
    public boolean unpackToQueue(Queue<BeanNode> queue) {
        int count = 0;
        for (Object input : getBean()) {
            String propertyName = determinePropertyName(input, count);
            count++;
            queue.add(createChildNode(propertyName, input));
        }
        return true;
    }

    private static String determinePropertyName(@Nullable Object input, int count) {
        String prefix = input instanceof Named ? ((Named) input).getName() : "";
        return prefix + "$" + count;
    }
}

class MapBeanNode extends BaseBeanNode<Map<?, ?>> {
    public MapBeanNode(@Nullable String propertyName, Map<?, ?> map) {
        super(propertyName, map);
    }

    @Override
    public boolean unpackToQueue(Queue<BeanNode> queue) {
        for (Map.Entry<?, ?> entry : getBean().entrySet()) {
            BeanNode childNode = createChildNode(
                Preconditions.checkNotNull(entry.getKey(), "Null keys in nested map '%s' are not allowed.", getPropertyName()).toString(),
                entry.getValue()
            );
            queue.add(childNode);
        }
        return true;
    }
}
