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

package org.gradle.api.internal.rules;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.Map;

public class DefaultRuleAwareNamedDomainObjectFactoryRegistry<T> implements RuleAwareNamedDomainObjectFactoryRegistry<T> {

    private final Map<Class<? extends T>, Optional<ModelRuleDescriptor>> creators = Maps.newHashMap();
    private final NamedDomainObjectFactoryRegistry<T> delegate;

    public DefaultRuleAwareNamedDomainObjectFactoryRegistry(NamedDomainObjectFactoryRegistry<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public <U extends T> void registerFactory(Class<U> type, NamedDomainObjectFactory<? extends U> factory) {
        registerFactory(type, factory, null);
    }

    @Override
    public <U extends T> void registerFactory(Class<U> type, NamedDomainObjectFactory<? extends U> factory, ModelRuleDescriptor descriptor) {
        checkCanRegister(type, descriptor);
        delegate.registerFactory(type, factory);
    }

    private void checkCanRegister(Class<? extends T> type, ModelRuleDescriptor descriptor) {
        Optional<ModelRuleDescriptor> creator = creators.get(type);
        if (creator != null) {
            StringBuilder builder = new StringBuilder("Cannot register a factory for type ")
                .append(type.getSimpleName())
                .append(" because a factory for this type was already registered");

            if (creator.isPresent()) {
                builder.append(" by ");
                creator.get().describeTo(builder);
            }
            builder.append(".");
            throw new GradleException(builder.toString());
        }
        creators.put(type, Optional.fromNullable(descriptor));
    }

}
