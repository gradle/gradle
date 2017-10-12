/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.model;

import org.gradle.api.Named;
import org.gradle.api.internal.provider.DefaultListProperty;
import org.gradle.api.internal.provider.DefaultProviderFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.reflect.ObjectInstantiationException;
import org.gradle.internal.reflect.Instantiator;

public class DefaultObjectFactory implements ObjectFactory {
    private final Instantiator instantiator;
    private final NamedObjectInstantiator namedObjectInstantiator;
    private final DefaultProviderFactory providerFactory;

    public DefaultObjectFactory(Instantiator instantiator, NamedObjectInstantiator namedObjectInstantiator) {
        this.instantiator = instantiator;
        this.namedObjectInstantiator = namedObjectInstantiator;
        providerFactory = new DefaultProviderFactory();
    }

    @Override
    public <T extends Named> T named(final Class<T> type, final String name) {
        return namedObjectInstantiator.named(type, name);
    }

    @Override
    public <T> T newInstance(Class<? extends T> type, Object... parameters) throws ObjectInstantiationException {
        return instantiator.newInstance(type, parameters);
    }

    @Override
    public <T> Property<T> property(Class<T> valueType) {
        return providerFactory.propertyNoNag(valueType);
    }

    @Override
    public <T> ListProperty<T> listProperty(Class<T> elementType) {
        return new DefaultListProperty<T>(elementType);
    }
}
