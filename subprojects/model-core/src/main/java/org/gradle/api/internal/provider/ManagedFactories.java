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
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.internal.state.ManagedFactory;

import javax.annotation.Nullable;
import java.util.Map;

public class ManagedFactories {
    public static class ProviderManagedFactory extends ManagedFactory.TypedManagedFactory {
        public ProviderManagedFactory() {
            super(Provider.class);
        }

        @Nullable
        @Override
        public <T> T fromState(Class<T> type, Object state) {
            if (!type.isAssignableFrom(publicType)) {
                return null;
            }
            if (state == null) {
                return type.cast(Providers.notDefined());
            } else {
                return type.cast(Providers.of(state));
            }
        }
    }

    public static class PropertyManagedFactory extends ManagedFactory.TypedManagedFactory {
        public PropertyManagedFactory() {
            super(Property.class);
        }

        @Nullable
        @Override
        public <S> S fromState(Class<S> type, Object state) {
            if (!type.isAssignableFrom(publicType)) {
                return null;
            }
            return type.cast(new DefaultPropertyState<>(Object.class).value(state));
        }
    }

    public static class ListPropertyManagedFactory extends ManagedFactory.TypedManagedFactory {
        public ListPropertyManagedFactory() {
            super(ListProperty.class);
        }

        @Nullable
        @Override
        public <S> S fromState(Class<S> type, Object state) {
            if (!type.isAssignableFrom(publicType)) {
                return null;
            }
            DefaultListProperty<?> property = new DefaultListProperty<>(Object.class);
            property.set((Iterable) state);
            return type.cast(property);
        }
    }

    public static class SetPropertyManagedFactory extends ManagedFactory.TypedManagedFactory {
        public SetPropertyManagedFactory() {
            super(SetProperty.class);
        }

        @Nullable
        @Override
        public <T> T fromState(Class<T> type, Object state) {
            if (!type.isAssignableFrom(publicType)) {
                return null;
            }
            DefaultSetProperty<?> property = new DefaultSetProperty<>(Object.class);
            property.set((Iterable) state);
            return type.cast(property);
        }
    }

    public static class MapPropertyManagedFactory extends ManagedFactory.TypedManagedFactory {
        public MapPropertyManagedFactory() {
            super(MapProperty.class);
        }

        @Nullable
        @Override
        public <S> S fromState(Class<S> type, Object state) {
            if (!type.isAssignableFrom(publicType)) {
                return null;
            }
            DefaultMapProperty<?, ?> property = new DefaultMapProperty<>(Object.class, Object.class);
            property.set((Map) state);
            return type.cast(property);
        }
    }
}
