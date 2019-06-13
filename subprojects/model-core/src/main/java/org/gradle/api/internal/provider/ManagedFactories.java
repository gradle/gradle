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
    public static class ProviderManagedFactory implements ManagedFactory {
        @Nullable
        @Override
        public <T> T fromState(Class<T> type, Object state) {
            if (!canCreate(type)) {
                return null;
            }
            if (state == null) {
                return type.cast(Providers.notDefined());
            } else {
                return type.cast(Providers.of(state));
            }
        }

        @Override
        public boolean canCreate(Class<?> type) {
            return type.isAssignableFrom(Provider.class);
        }
    }

    public static class PropertyManagedFactory implements ManagedFactory {
        @Nullable
        @Override
        public <S> S fromState(Class<S> type, Object state) {
            if (!canCreate(type)) {
                return null;
            }
            return type.cast(new DefaultPropertyState<>(Object.class).value(state));
        }

        @Override
        public boolean canCreate(Class<?> type) {
            return type.isAssignableFrom(Property.class);
        }
    }

    public static class ListPropertyManagedFactory implements ManagedFactory {
        @Nullable
        @Override
        public <S> S fromState(Class<S> type, Object state) {
            if (!canCreate(type)) {
                return null;
            }
            DefaultListProperty<?> property = new DefaultListProperty<>(Object.class);
            property.set((Iterable) state);
            return type.cast(property);
        }

        @Override
        public boolean canCreate(Class<?> type) {
            return type.isAssignableFrom(ListProperty.class);
        }
    }

    public static class SetPropertyManagedFactory implements ManagedFactory {
        @Nullable
        @Override
        public <T> T fromState(Class<T> type, Object state) {
            if (!type.isAssignableFrom(SetProperty.class)) {
                return null;
            }
            DefaultSetProperty<?> property = new DefaultSetProperty<>(Object.class);
            property.set((Iterable) state);
            return type.cast(property);
        }

        @Override
        public boolean canCreate(Class<?> type) {
            return false;
        }
    }

    public static class MapPropertyManagedFactory implements ManagedFactory {
        @Nullable
        @Override
        public <S> S fromState(Class<S> type, Object state) {
            if (!canCreate(type)) {
                return null;
            }
            DefaultMapProperty<?, ?> property = new DefaultMapProperty<>(Object.class, Object.class);
            property.set((Map) state);
            return type.cast(property);
        }

        @Override
        public boolean canCreate(Class<?> type) {
            return type.isAssignableFrom(MapProperty.class);
        }
    }
}
