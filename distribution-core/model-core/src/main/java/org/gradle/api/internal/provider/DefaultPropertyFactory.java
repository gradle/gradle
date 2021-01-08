/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.provider;

public class DefaultPropertyFactory implements PropertyFactory {
    private final PropertyHost propertyHost;

    public DefaultPropertyFactory(PropertyHost propertyHost) {
        this.propertyHost = propertyHost;
    }

    @Override
    public <T> DefaultProperty<T> property(Class<T> type) {
        return new DefaultProperty<>(propertyHost, type);
    }

    @Override
    public <T> DefaultListProperty<T> listProperty(Class<T> elementType) {
        return new DefaultListProperty<>(propertyHost, elementType);
    }

    @Override
    public <T> DefaultSetProperty<T> setProperty(Class<T> elementType) {
        return new DefaultSetProperty<>(propertyHost, elementType);
    }

    @Override
    public <V, K> DefaultMapProperty<K, V> mapProperty(Class<K> keyType, Class<V> valueType) {
        return new DefaultMapProperty<>(propertyHost, keyType, valueType);
    }
}
