/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.internal.plugins;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.plugins.DeferredConfigurable;
import org.gradle.internal.UncheckedException;
import org.gradle.listener.ActionBroadcast;
import org.gradle.util.ConfigureUtil;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ExtensionsStorage {
    private final Map<String, ExtensionHolder> extensions = new LinkedHashMap<String, ExtensionHolder>();

    public <T> void add(Class<T> publicType, String name, T extension) {
        if (extensions.containsKey(name)) {
            throw new IllegalArgumentException(String.format("Cannot add extension with name '%s', as there is an extension already registered with that name.", name));
        }
        extensions.put(name, wrap(name, publicType, extension));
    }

    public boolean hasExtension(String name) {
        return extensions.containsKey(name);
    }

    public Map<String, Object> getAsMap() {
        Map<String, Object> rawExtensions = new LinkedHashMap<String, Object>(extensions.size());
        for (Map.Entry<String, ExtensionHolder> entry : extensions.entrySet()) {
            rawExtensions.put(entry.getKey(), entry.getValue().get());
        }
        return rawExtensions;
    }

    public Map<String, Class<?>> getSchema() {
        Map<String, Class<?>> schema = new LinkedHashMap<String, Class<?>>(extensions.size());
        for (Map.Entry<String, ExtensionHolder> entry : extensions.entrySet()) {
            schema.put(entry.getKey(), entry.getValue().getType());
        }
        return schema;
    }

    public void checkExtensionIsNotReassigned(String name) {
        if (hasExtension(name)) {
            throw new IllegalArgumentException(String.format("There's an extension registered with name '%s'. You should not reassign it via a property setter.", name));
        }
    }

    public boolean isConfigureExtensionMethod(String methodName, Object... arguments) {
        return extensions.containsKey(methodName) && arguments.length == 1 && arguments[0] instanceof Closure;
    }

    public <T> T configureExtension(String methodName, Object... arguments) {
        Closure closure = (Closure) arguments[0];
        Action<T> action = ConfigureUtil.configureUsing(closure);
        ExtensionHolder<T> extensionHolder = extensions.get(methodName);
        return extensionHolder.configure(action);
    }

    public <T> void configureExtension(Class<T> type, Action<? super T> action) {
        getHolderByType(type).configure(action);
    }

    public <T> T getByType(Class<T> type) {
        return getHolderByType(type).get();
    }

    public <T> T findByType(Class<T> type) {
        ExtensionHolder<T> holder;
        try {
            holder = getHolderByType(type);
        } catch (UnknownDomainObjectException e) {
            return null;
        }
        return holder.get();
    }

    private <T> ExtensionHolder<T> getHolderByType(Class<T> type) {
        List<String> types = new LinkedList<String>();
        for (ExtensionHolder extensionHolder : extensions.values()) {
            Class<?> clazz = extensionHolder.getType();
            types.add(clazz.getSimpleName());
            if (type.isAssignableFrom(clazz)) {
                return extensionHolder;
            }
        }
        throw new UnknownDomainObjectException("Extension of type '" + type.getSimpleName() + "' does not exist. Currently registered extension types: " + types);
    }

    public Object getByName(String name) {
        Object extension = findByName(name);
        if (extension == null) {
            throw new UnknownDomainObjectException("Extension with name '" + name + "' does not exist. Currently registered extension names: " + extensions.keySet());
        }
        return extension;
    }

    public Object findByName(String name) {
        ExtensionHolder extensionHolder = extensions.get(name);
        return extensionHolder == null ? null : extensionHolder.get();
    }

    private <T> ExtensionHolder<T> wrap(String name, Class<T> publicType, T extension) {
        if (isDeferredConfigurable(extension)) {
            return new DeferredConfigurableExtensionHolder<T>(name, publicType, extension);
        }
        return new ExtensionHolder<T>(publicType, extension);
    }

    private <T> boolean isDeferredConfigurable(T extension) {
        return extension.getClass().isAnnotationPresent(DeferredConfigurable.class);
    }

    private static class ExtensionHolder<T> {
        protected final Class<T> publicType;
        protected final T extension;

        private ExtensionHolder(Class<T> publicType, T extension) {
            this.publicType = publicType;
            this.extension = extension;
        }

        public Class<T> getType() {
            return publicType;
        }

        public T get() {
            return extension;
        }

        public T configure(Closure configuration) {
            return configure(ConfigureUtil.configureUsing(configuration));
        }

        public T configure(Action<? super T> action) {
            action.execute(extension);
            return extension;
        }
    }

    private static class DeferredConfigurableExtensionHolder<T> extends ExtensionHolder<T> {
        private final String name;
        private ActionBroadcast<T> actions = new ActionBroadcast<T>();
        private boolean configured;
        private Throwable configureFailure;

        public DeferredConfigurableExtensionHolder(String name, Class<T> publicType, T extension) {
            super(publicType, extension);
            this.name = name;
        }

        public T get() {
            configureNow();
            return extension;
        }

        @Override
        public T configure(Action<? super T> action) {
            configureLater(action);
            return null;
        }

        private void configureLater(Action<? super T> action) {
            if (configured) {
                throw new InvalidUserDataException(String.format("Cannot configure the '%s' extension after it has been accessed.", name));
            }
            actions.add(action);
        }

        private void configureNow() {
            if (!configured) {
                configured = true;
                try {
                    actions.execute(extension);
                } catch (Throwable t) {
                    configureFailure = t;
                }
                actions = null;
            }

            if (configureFailure != null) {
                throw UncheckedException.throwAsUncheckedException(configureFailure);
            }
        }

    }
}
