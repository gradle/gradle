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
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.reflect.ObjectInstantiationException;
import org.gradle.internal.reflect.Instantiator;

public class InstantiatorBackedObjectFactory implements ObjectFactory {
    private final Instantiator instantiator;

    public InstantiatorBackedObjectFactory(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    @Override
    public <T extends Named> T named(Class<T> type, String name) throws ObjectInstantiationException {
        throw new UnsupportedOperationException("Instantiator does not support constructing named objects");
    }

    @Override
    public <T> Property<T> property(Class<T> valueType) {
        throw new UnsupportedOperationException("Instantiator does not support constructing property objects");
    }

    @Override
    public <T> ListProperty<T> listProperty(Class<T> elementType) {
        throw new UnsupportedOperationException("Instantiator does not support constructing property objects");
    }

    @Override
    public <T> T newInstance(Class<? extends T> type, Object... parameters) throws ObjectInstantiationException {
        return instantiator.newInstance(type, parameters);
    }
}
