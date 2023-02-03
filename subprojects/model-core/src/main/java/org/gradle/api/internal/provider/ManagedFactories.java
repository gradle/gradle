/*
 * Copyright 2019 the original author or authors.
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

import com.google.common.base.Objects;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.internal.Cast;
import org.gradle.internal.state.ManagedFactory;

import javax.annotation.Nullable;
import java.util.Map;

public class ManagedFactories {
    public static class ProviderManagedFactory implements ManagedFactory {
        private static final Class<?> PUBLIC_TYPE = Provider.class;
        private static final Class<?> IMPL_TYPE = Providers.FixedValueProvider.class;
        public static final int FACTORY_ID = Objects.hashCode(IMPL_TYPE.getName());

        @Nullable
        @Override
        public <T> T fromState(Class<T> type, Object state) {
            if (!type.isAssignableFrom(PUBLIC_TYPE)) {
                return null;
            }
            return type.cast(Providers.ofNullable(state));
        }

        @Override
        public int getId() {
            return FACTORY_ID;
        }
    }

    public static class PropertyManagedFactory implements ManagedFactory {
        private static final Class<?> PUBLIC_TYPE = Property.class;
        private static final Class<?> IMPL_TYPE = DefaultProperty.class;
        public static final int FACTORY_ID = Objects.hashCode(IMPL_TYPE.getName());
        private final PropertyFactory propertyFactory;

        public PropertyManagedFactory(PropertyFactory propertyFactory) {
            this.propertyFactory = propertyFactory;
        }

        @Nullable
        @Override
        public <S> S fromState(Class<S> type, Object state) {
            if (!type.isAssignableFrom(PUBLIC_TYPE)) {
                return null;
            }
            // TODO - should preserve the property type (which may be different to the provider type)
            ProviderInternal<S> provider = Cast.uncheckedNonnullCast(state);
            return type.cast(propertyOf(provider.getType(), provider));
        }

        <V> Property<V> propertyOf(Class<V> type, Provider<V> value) {
            return propertyFactory.property(type).value(value);
        }

        @Override
        public int getId() {
            return FACTORY_ID;
        }
    }

    public static class ListPropertyManagedFactory implements ManagedFactory {
        private static final Class<?> PUBLIC_TYPE = ListProperty.class;
        private static final Class<?> IMPL_TYPE = DefaultListProperty.class;
        public static final int FACTORY_ID = Objects.hashCode(IMPL_TYPE.getName());
        private final PropertyFactory propertyFactory;

        public ListPropertyManagedFactory(PropertyFactory propertyFactory) {
            this.propertyFactory = propertyFactory;
        }

        @Nullable
        @Override
        public <S> S fromState(Class<S> type, Object state) {
            if (!type.isAssignableFrom(PUBLIC_TYPE)) {
                return null;
            }
            // TODO - should preserve the element type
            DefaultListProperty<Object> property = propertyFactory.listProperty(Object.class);
            property.set(Cast.<Iterable<Object>>uncheckedNonnullCast(state));
            return type.cast(property);
        }

        @Override
        public int getId() {
            return FACTORY_ID;
        }
    }

    public static class SetPropertyManagedFactory implements ManagedFactory {
        private static final Class<?> PUBLIC_TYPE = SetProperty.class;
        private static final Class<?> IMPL_TYPE = DefaultSetProperty.class;
        public static final int FACTORY_ID = Objects.hashCode(IMPL_TYPE.getName());

        private final PropertyFactory propertyFactory;

        public SetPropertyManagedFactory(PropertyFactory propertyFactory) {
            this.propertyFactory = propertyFactory;
        }

        @Nullable
        @Override
        public <T> T fromState(Class<T> type, Object state) {
            if (!type.isAssignableFrom(PUBLIC_TYPE)) {
                return null;
            }
            // TODO - should preserve the element type
            DefaultSetProperty<Object> property = propertyFactory.setProperty(Object.class);
            property.set(Cast.<Iterable<Object>>uncheckedNonnullCast(state));
            return type.cast(property);
        }

        @Override
        public int getId() {
            return FACTORY_ID;
        }
    }

    public static class MapPropertyManagedFactory implements ManagedFactory {
        private static final Class<?> PUBLIC_TYPE = MapProperty.class;
        private static final Class<?> IMPL_TYPE = MapProperty.class;
        public static final int FACTORY_ID = Objects.hashCode(IMPL_TYPE.getName());

        private final PropertyFactory propertyFactory;

        public MapPropertyManagedFactory(PropertyFactory propertyFactory) {
            this.propertyFactory = propertyFactory;
        }

        @Nullable
        @Override
        public <S> S fromState(Class<S> type, Object state) {
            if (!type.isAssignableFrom(PUBLIC_TYPE)) {
                return null;
            }
            // TODO - should preserve the key and value types
            DefaultMapProperty<Object, Object> property = propertyFactory.mapProperty(Object.class, Object.class);
            property.set(Cast.<Map<Object, Object>>uncheckedNonnullCast(state));
            return type.cast(property);
        }

        @Override
        public int getId() {
            return FACTORY_ID;
        }
    }
}
