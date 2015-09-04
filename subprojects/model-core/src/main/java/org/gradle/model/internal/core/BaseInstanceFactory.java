/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.core;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.GradleException;
import org.gradle.api.Nullable;
import org.gradle.internal.Cast;
import org.gradle.internal.util.BiFunction;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class BaseInstanceFactory<T, P> implements InstanceFactory<T, P> {

    private class Registration<S extends T> {
        private final ModelRuleDescriptor source;
        private final BiFunction<? extends S, ? super P, ? super MutableModelNode> factory;

        public Registration(ModelRuleDescriptor source, BiFunction<? extends S, ? super P, ? super MutableModelNode> factory) {
            this.source = source;
            this.factory = factory;
        }
    }

    private final String displayName;
    private final Map<Class<?>, Registration<?>> factories = Maps.newIdentityHashMap();

    public BaseInstanceFactory(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public <S extends T> void register(Class<S> type, @Nullable ModelRuleDescriptor sourceRule, BiFunction<? extends S, ? super P, ? super MutableModelNode> factory) {
        Registration<S> registration = getRegistration(type);
        if (registration != null) {
            StringBuilder builder = new StringBuilder("Cannot register a factory for type ")
                .append(type.getSimpleName())
                .append(" because a factory for this type was already registered");

            if (registration.source != null) {
                builder.append(" by ");
                registration.source.describeTo(builder);
            }
            builder.append(".");
            throw new GradleException(builder.toString());
        }

        factories.put(type, new Registration<S>(sourceRule, factory));
    }

    private <S extends T> Registration<S> getRegistration(Class<S> type) {
        return Cast.uncheckedCast(factories.get(type));
    }

    @Override
    public <S extends T> S create(Class<S> type, MutableModelNode modelNode, P payload) {
        Registration<S> registration = getRegistration(type);
        if (registration == null) {
            throw new IllegalArgumentException(
                String.format("Cannot create a %s because this type is not known to %s. Known types are: %s", type.getSimpleName(), displayName, getSupportedTypeNames()));
        }
        return registration.factory.apply(payload, modelNode);
    }

    @Override
    public String getSupportedTypeNames() {
        List<String> names = Lists.newArrayList();
        for (Class<?> clazz : factories.keySet()) {
            names.add(clazz.getSimpleName());
        }
        Collections.sort(names);
        return names.isEmpty() ? "(None)" : Joiner.on(", ").join(names);
    }

    @Override
    public String toString() {
        return "[" + getSupportedTypeNames() + "]";
    }
}
