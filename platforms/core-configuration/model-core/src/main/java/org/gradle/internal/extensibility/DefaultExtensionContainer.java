/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.extensibility;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.plugins.ExtensionContainerInternal;
import org.gradle.api.plugins.ExtensionsSchema;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.reflect.TypeOf;
import org.gradle.internal.Cast;
import org.gradle.internal.Describables;
import org.gradle.internal.instantiation.InstanceGenerator;
import org.gradle.internal.metaobject.AbstractDynamicObject;
import org.gradle.internal.metaobject.DynamicInvokeResult;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.util.internal.ConfigureUtil;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

import static java.lang.String.format;
import static org.gradle.api.reflect.TypeOf.typeOf;

public class DefaultExtensionContainer implements ExtensionContainerInternal {
    private static final TypeOf<ExtraPropertiesExtension> EXTRA_PROPERTIES_EXTENSION_TYPE = typeOf(ExtraPropertiesExtension.class);
    private final DefaultExtensionContainer.ExtensionsDynamicObject extensionsDynamicObject = new ExtensionsDynamicObject();
    private final ExtensionsStorage extensionsStorage = new ExtensionsStorage();
    private final ExtraPropertiesExtension extraProperties = new DefaultExtraPropertiesExtension();
    private final InstanceGenerator instanceGenerator;

    public DefaultExtensionContainer(InstanceGenerator instanceGenerator) {
        this.instanceGenerator = instanceGenerator;
        add(EXTRA_PROPERTIES_EXTENSION_TYPE, ExtraPropertiesExtension.EXTENSION_NAME, extraProperties);
    }


    public DynamicObject getExtensionsAsDynamicObject() {
        // This implementation of Convention doesn't log a deprecation warning
        // because it mixes both extensions and conventions.
        // Instead, the returned object logs a deprecation warning when
        // a convention is actually accessed.
        return extensionsDynamicObject;
    }

    @Override
    public void add(String name, Object extension) {
        if (extension instanceof Class) {
            create(name, (Class<?>) extension);
        } else {
            addWithDefaultPublicType(name, extension);
        }
    }

    @Override
    public <T> void add(Class<T> publicType, String name, T extension) {
        add(typeOf(publicType), name, extension);
    }

    @Override
    public <T> void add(TypeOf<T> publicType, String name, T extension) {
        extensionsStorage.add(publicType, name, extension);
    }

    @Override
    public <T> T create(String name, Class<T> instanceType, Object... constructionArguments) {
        T instance = instantiate(instanceType, name, constructionArguments);
        addWithDefaultPublicType(name, instance);
        return instance;
    }

    @Override
    public <T> T create(Class<T> publicType, String name, Class<? extends T> instanceType, Object... constructionArguments) {
        return create(typeOf(publicType), name, instanceType, constructionArguments);
    }

    @Override
    public <T> T create(TypeOf<T> publicType, String name, Class<? extends T> instanceType, Object... constructionArguments) {
        T instance = instantiate(instanceType, name, constructionArguments);
        add(publicType, name, instance);
        return instance;
    }

    @Override
    public ExtraPropertiesExtension getExtraProperties() {
        return extraProperties;
    }

    @Override
    public ExtensionsSchema getExtensionsSchema() {
        return extensionsStorage.getSchema();
    }

    @Override
    public <T> T getByType(Class<T> type) {
        return getByType(typeOf(type));
    }

    @Override
    public <T> T getByType(TypeOf<T> type) {
        return extensionsStorage.getByType(type);
    }

    @Override
    public <T> T findByType(Class<T> type) {
        return findByType(typeOf(type));
    }

    @Override
    public <T> T findByType(TypeOf<T> type) {
        return extensionsStorage.findByType(type);
    }

    @Override
    public Object getByName(String name) {
        return extensionsStorage.getByName(name);
    }

    @Override
    public Object findByName(String name) {
        return extensionsStorage.findByName(name);
    }

    @Override
    public <T> void configure(Class<T> type, Action<? super T> action) {
        configure(typeOf(type), action);
    }

    @Override
    public <T> void configure(TypeOf<T> type, Action<? super T> action) {
        extensionsStorage.configureExtension(type, action);
    }

    @Override
    public <T> void configure(String name, Action<? super T> action) {
        extensionsStorage.configureExtension(name, action);
    }

    @Override
    public Map<String, Object> getAsMap() {
        return extensionsStorage.getAsMap();
    }

    public Object propertyMissing(String name) {
        return getByName(name);
    }

    public void propertyMissing(String name, Object value) {
        checkExtensionIsNotReassigned(name);
        add(name, value);
    }

    private void addWithDefaultPublicType(String name, Object extension) {
        add(new DslObject(extension).getPublicType(), name, extension);
    }

    private <T> T instantiate(Class<? extends T> instanceType, String name, Object[] constructionArguments) {
        return instanceGenerator.newInstanceWithDisplayName(instanceType, Describables.withTypeAndName("extension", name), constructionArguments);
    }

    private class ExtensionsDynamicObject extends AbstractDynamicObject {
        @Override
        public String getDisplayName() {
            return "extensions";
        }

        @Override
        public boolean hasProperty(String name) {
            return extensionsStorage.hasExtension(name);
        }

        @Override
        public Map<String, @Nullable Object> getProperties() {
            Map<String, Object> properties = new HashMap<String, Object>();
            properties.putAll(extensionsStorage.getAsMap());
            return properties;
        }

        @Override
        public DynamicInvokeResult tryGetProperty(String name) {
            Object extension = extensionsStorage.findByName(name);
            if (extension != null) {
                return DynamicInvokeResult.found(extension);
            }
            return DynamicInvokeResult.notFound();
        }

        @Nullable
        @SuppressWarnings("unused") // Groovy magic method
        public Object propertyMissing(String name) {
            return getProperty(name);
        }

        @SuppressWarnings("unused")  // Groovy magic method
        public void propertyMissing(String name, Object value) {
            setProperty(name, value);
        }

        @Override
        public DynamicInvokeResult trySetProperty(String name, @Nullable Object value) {
            return trySetProperty(name);
        }

        @Override
        public DynamicInvokeResult trySetPropertyWithoutInstrumentation(String name, @Nullable Object value) {
            return trySetProperty(name);
        }

        private DynamicInvokeResult trySetProperty(String name) {
            checkExtensionIsNotReassigned(name);
            return DynamicInvokeResult.notFound();
        }

        @Override
        public DynamicInvokeResult tryInvokeMethod(String name, @Nullable Object... args) {
            if (isConfigureExtensionMethod(name, args)) {
                return DynamicInvokeResult.found(configureExtension(name, args));
            }
            return DynamicInvokeResult.notFound();
        }

        @Nullable
        @SuppressWarnings("unused") // Groovy magic method
        public Object methodMissing(String name, Object args) {
            return invokeMethod(name, (Object[]) args);
        }

        @Override
        public boolean hasMethod(String name, @Nullable Object... args) {
            return isConfigureExtensionMethod(name, args);
        }
    }

    private void checkExtensionIsNotReassigned(String name) {
        if (extensionsStorage.hasExtension(name)) {
            throw new IllegalArgumentException(
                format("There's an extension registered with name '%s'. You should not reassign it via a property setter.", name));
        }
    }

    private boolean isConfigureExtensionMethod(String name, @Nullable Object[] args) {
        return args.length == 1 &&
            (args[0] instanceof Closure || args[0] instanceof Action) &&
            extensionsStorage.hasExtension(name);
    }

    private Object configureExtension(String name, Object[] args) {
        Action<Object> action;
        if (args[0] instanceof Closure) {
            action = ConfigureUtil.configureUsing(Cast.uncheckedCast(args[0]));
        } else {
            action = Cast.uncheckedCast(args[0]);
        }
        return extensionsStorage.configureExtension(name, action);
    }
}
