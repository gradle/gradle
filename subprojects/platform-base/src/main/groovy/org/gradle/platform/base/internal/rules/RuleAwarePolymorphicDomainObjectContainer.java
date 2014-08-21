/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.platform.base.internal.rules;

import com.google.common.collect.Maps;
import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.Namer;
import org.gradle.api.internal.DefaultPolymorphicDomainObjectContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.Map;

public abstract class RuleAwarePolymorphicDomainObjectContainer<T> extends DefaultPolymorphicDomainObjectContainer<T> {
    private final Map<Class<? extends T>, ModelRuleDescriptor> creators = Maps.newHashMap();

    public RuleAwarePolymorphicDomainObjectContainer(Class<T> type, Instantiator instantiator, Namer<? super T> namer) {
        super(type, instantiator, namer);
    }

    public RuleAwarePolymorphicDomainObjectContainer(Class<T> type, Instantiator instantiator) {
        super(type, instantiator);
    }

    @Override
    public <U extends T> void registerFactory(Class<U> type, NamedDomainObjectFactory<? extends U> factory) {
        checkCanRegister(type);
        super.registerFactory(type, factory);
    }

    private void checkCanRegister(Class<? extends T> type) {
        ModelRuleDescriptor creator = creators.get(type);
        if (creator != null) {
            StringBuilder builder = new StringBuilder("Cannot register a factory for type ")
                    .append(type.getSimpleName())
                    .append(" because a factory for this type was already registered by ");
            creator.describeTo(builder);
            builder.append(".");
            throw new GradleException(builder.toString());
        }
        creators.put(type, RuleContext.get());
    }
}
