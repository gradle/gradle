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

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.internal.instantiation.managed.ManagedObjectCreator;
import org.gradle.internal.instantiation.managed.ManagedObjectProvider;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

@ManagedObjectProvider
@ServiceScope({Scope.Global.class, Scope.Project.class})
public interface PropertyFactory {

    /**
     * If you are calling this, you are probably doing something wrong.
     */
    @Deprecated
    DefaultProperty<?> propertyWithNoType();

    /**
     * Creates a property with an arbitrary value type without applying the usual checks (like no {@code Property<Directory>}, etc.)
     *
     * @param type the type of the property value
     * @param <T> the type of the property value
     * @return the property
     * @deprecated Consider passing around the type information and using {@link #property(Class)} instead.
     */
    @Deprecated
    <T> DefaultProperty<T> propertyOfAnyType(Class<T> type);

    @ManagedObjectCreator(publicType = Property.class)
    <T> DefaultProperty<T> property(Class<T> type);

    @ManagedObjectCreator(publicType = ListProperty.class)
    <T> DefaultListProperty<T> listProperty(Class<T> elementType);

    @ManagedObjectCreator(publicType = SetProperty.class)
    <T> DefaultSetProperty<T> setProperty(Class<T> elementType);

    @ManagedObjectCreator(publicType = MapProperty.class)
    <V, K> DefaultMapProperty<K, V> mapProperty(Class<K> keyType, Class<V> valueType);

}
