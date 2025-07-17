/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.model.internal;

import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.isolation.IsolatableFactory;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

/**
 * Default implementation of {@link org.gradle.api.model.ModelContainer}.
 */
public class DefaultModelContainer implements ModelContainerInternal {

    private final ObjectFactory objectFactory;
    private final IsolatableFactory isolatableFactory;

    // Mutable state
    private final Map<String, ModelRegistration<?>> registrations = new HashMap<>();

    @Inject
    private DefaultModelContainer(
        ObjectFactory objectFactory,
        IsolatableFactory isolatableFactory
    ) {
        this.objectFactory = objectFactory;
        this.isolatableFactory = isolatableFactory;
    }

    @Override
    public <T> void register(Class<T> type, Action<T> configureAction) {
        registrations.put(type.getName(), new ModelRegistration<>(type, configureAction));
    }

    @Override
    public @Nullable DataModel findDataModel(String name) {
        ModelRegistration<?> registration = registrations.get(name);
        if (registration == null) {
            return null;
        }
        return asDataModel(registration);
    }

    private <T> DataModel asDataModel(ModelRegistration<T> registration) {
        Isolatable<T> data = instantiate(registration);
        return new LiveDataModel<>(registration.getType(), data);
    }

    private <T> Isolatable<T> instantiate(ModelRegistration<T> registration) {
        T instance = objectFactory.newInstance(registration.getType());
        registration.getConfigureAction().execute(instance);
        return isolatableFactory.isolate(instance);
    }

    private static class ModelRegistration<T> {

        private final Class<T> type;
        private final Action<T> configureAction;

        public ModelRegistration(Class<T> type, Action<T> configureAction) {
            this.type = type;
            this.configureAction = configureAction;
        }

        public Class<T> getType() {
            return type;
        }

        public Action<T> getConfigureAction() {
            return configureAction;
        }

    }

}
