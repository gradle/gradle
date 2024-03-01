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

import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.internal.PolymorphicNamedEntityInstantiator;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.Set;

public class DefaultRuleAwarePolymorphicNamedEntityInstantiator<T> implements RuleAwarePolymorphicNamedEntityInstantiator<T> {

    private final PolymorphicNamedEntityInstantiator<T> instantiator;
    private final RuleAwareNamedDomainObjectFactoryRegistry<T> registry;

    public DefaultRuleAwarePolymorphicNamedEntityInstantiator(PolymorphicNamedEntityInstantiator<T> instantiator) {
        this(instantiator, new DefaultRuleAwareNamedDomainObjectFactoryRegistry<T>(instantiator));
    }

    public DefaultRuleAwarePolymorphicNamedEntityInstantiator(PolymorphicNamedEntityInstantiator<T> instantiator, RuleAwareNamedDomainObjectFactoryRegistry<T> registry) {
        this.instantiator = instantiator;
        this.registry = registry;
    }

    @Override
    public <S extends T> S create(String name, Class<S> type) {
        return instantiator.create(name, type);
    }

    @Override
    public <U extends T> void registerFactory(Class<U> type, NamedDomainObjectFactory<? extends U> factory, ModelRuleDescriptor descriptor) {
        registry.registerFactory(type, factory, descriptor);
    }

    @Override
    public Set<? extends Class<? extends T>> getCreatableTypes() {
        return instantiator.getCreatableTypes();
    }

    @Override
    public <U extends T> void registerFactory(Class<U> type, NamedDomainObjectFactory<? extends U> factory) {
        registry.registerFactory(type, factory);
    }
}
