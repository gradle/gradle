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

package org.gradle.language.base.internal.model;

import com.google.common.collect.Maps;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.internal.rules.NamedDomainObjectFactoryRegistry;
import org.gradle.api.internal.rules.RuleAwareNamedDomainObjectFactoryRegistry;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.platform.base.BinarySpec;
import org.gradle.api.internal.rules.DefaultRuleAwareNamedDomainObjectFactoryRegistry;

import java.util.HashMap;
import java.util.Map;

import static org.gradle.internal.Cast.uncheckedCast;

public class BinarySpecFactoryRegistry implements RuleAwareNamedDomainObjectFactoryRegistry<BinarySpec> {

    private final CollectingNamedBinarySpecFactoryRegistry collector = new CollectingNamedBinarySpecFactoryRegistry();

    private final RuleAwareNamedDomainObjectFactoryRegistry<BinarySpec> delegate = new DefaultRuleAwareNamedDomainObjectFactoryRegistry<BinarySpec>(collector);

    @Override
    public <U extends BinarySpec> void registerFactory(Class<U> type, NamedDomainObjectFactory<? extends U> factory, ModelRuleDescriptor descriptor) {
        delegate.registerFactory(type, factory, descriptor);
    }

    @Override
    public <U extends BinarySpec> void registerFactory(Class<U> type, NamedDomainObjectFactory<? extends U> factory) {
        delegate.registerFactory(type, factory);
    }

    public void copyInto(NamedDomainObjectFactoryRegistry<BinarySpec> destination) {
        for (Map.Entry<Class<BinarySpec>, NamedDomainObjectFactory<? extends BinarySpec>> factory : collector.factories.entrySet()) {
            destination.registerFactory(factory.getKey(), factory.getValue());
        }
    }

    private static class CollectingNamedBinarySpecFactoryRegistry implements NamedDomainObjectFactoryRegistry<BinarySpec> {

        private final HashMap<Class<BinarySpec>, NamedDomainObjectFactory<? extends BinarySpec>> factories = Maps.newHashMap();

        @Override
        public <U extends BinarySpec> void registerFactory(Class<U> type, NamedDomainObjectFactory<? extends U> factory) {
            Class<BinarySpec> o = uncheckedCast(type);
            factories.put(o, factory);
        }
    }
}
