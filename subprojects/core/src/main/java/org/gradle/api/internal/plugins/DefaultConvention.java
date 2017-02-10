/*
 * Copyright 2009 the original author or authors.
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
import org.gradle.api.GradleException;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.gradle.internal.metaobject.AbstractDynamicObject;
import org.gradle.internal.metaobject.BeanDynamicObject;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.internal.metaobject.GetPropertyResult;
import org.gradle.internal.metaobject.InvokeMethodResult;
import org.gradle.internal.metaobject.SetPropertyResult;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.ConfigureUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static org.gradle.api.reflect.TypeOf.typeOf;
import static org.gradle.internal.Cast.uncheckedCast;

public class DefaultConvention implements Convention, ExtensionContainerInternal {

    private final Map<String, Object> plugins = new LinkedHashMap<String, Object>();
    private final DefaultConvention.ExtensionsDynamicObject extensionsDynamicObject = new ExtensionsDynamicObject();
    private final ExtensionsStorage extensionsStorage = new ExtensionsStorage();
    private final ExtraPropertiesExtension extraProperties = new DefaultExtraPropertiesExtension();
    private final Instantiator instantiator;

    /**
     * This method should not be used in runtime code proper as means that the convention cannot create
     * dynamic extensions.
     *
     * It's here for backwards compatibility with our tests and for convenience.
     *
     * @see #DefaultConvention(org.gradle.internal.reflect.Instantiator)
     */
    public DefaultConvention() {
        this(null);
    }

    public DefaultConvention(Instantiator instantiator) {
        this.instantiator = instantiator;
        add(ExtraPropertiesExtension.class, ExtraPropertiesExtension.EXTENSION_NAME, extraProperties);
    }

    @Override
    public Map<String, Object> getPlugins() {
        return plugins;
    }

    @Override
    public DynamicObject getExtensionsAsDynamicObject() {
        return extensionsDynamicObject;
    }

    private Instantiator getInstantiator() {
        if (instantiator == null) {
            throw new GradleException("request for DefaultConvention.instantiator when the object was constructed without a convention");
        }
        return instantiator;
    }

    @Override
    public <T> T getPlugin(Class<T> type) {
        T value = findPlugin(type);
        if (value == null) {
            throw new IllegalStateException(
                format("Could not find any convention object of type %s.", type.getSimpleName()));
        }
        return value;
    }

    @Override
    public <T> T findPlugin(Class<T> type) throws IllegalStateException {
        List<T> values = new ArrayList<T>();
        for (Object object : plugins.values()) {
            if (type.isInstance(object)) {
                values.add(type.cast(object));
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        if (values.size() > 1) {
            throw new IllegalStateException(
                format("Found multiple convention objects of type %s.", type.getSimpleName()));
        }
        return values.get(0);
    }

    @Override
    public void add(String name, Object extension) {
        if (extension instanceof Class) {
            create(name, (Class<?>) extension);
        } else {
            addWithDefaultPublicType(extension.getClass(), name, extension);
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
        T instance = instantiate(instanceType, constructionArguments);
        addWithDefaultPublicType(instanceType, name, instance);
        return instance;
    }

    @Override
    public <T> T create(Class<T> publicType, String name, Class<? extends T> instanceType, Object... constructionArguments) {
        return create(typeOf(publicType), name, instanceType, constructionArguments);
    }

    @Override
    public <T> T create(TypeOf<T> publicType, String name, Class<? extends T> instanceType, Object... constructionArguments) {
        T instance = instantiate(instanceType, constructionArguments);
        add(publicType, name, instance);
        return instance;
    }

    @Override
    public ExtraPropertiesExtension getExtraProperties() {
        return extraProperties;
    }

    @Override
    public Map<String, TypeOf<?>> getSchema() {
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

    private void addWithDefaultPublicType(Class<?> defaultType, String name, Object extension) {
        add(preferredPublicTypeOf(extension, defaultType), name, extension);
    }

    private TypeOf<Object> preferredPublicTypeOf(Object extension, Class<?> defaultType) {
        if (extension instanceof HasPublicType) {
            return uncheckedCast(((HasPublicType) extension).getPublicType());
        }
        return TypeOf.<Object>typeOf(defaultType);
    }

    private <T> T instantiate(Class<? extends T> instanceType, Object[] constructionArguments) {
        return getInstantiator().newInstance(instanceType, constructionArguments);
    }

    private class ExtensionsDynamicObject extends AbstractDynamicObject {
        @Override
        public String getDisplayName() {
            return "extensions";
        }

        @Override
        public boolean hasProperty(String name) {
            if (extensionsStorage.hasExtension(name)) {
                return true;
            }
            for (Object object : plugins.values()) {
                if (new BeanDynamicObject(object).hasProperty(name)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Map<String, Object> getProperties() {
            Map<String, Object> properties = new HashMap<String, Object>();
            List<Object> reverseOrder = new ArrayList<Object>(plugins.values());
            Collections.reverse(reverseOrder);
            for (Object object : reverseOrder) {
                properties.putAll(new BeanDynamicObject(object).getProperties());
            }
            properties.putAll(extensionsStorage.getAsMap());
            return properties;
        }

        @Override
        public void getProperty(String name, GetPropertyResult result) {
            Object extension = extensionsStorage.findByName(name);
            if (extension != null) {
                result.result(extension);
                return;
            }
            for (Object object : plugins.values()) {
                DynamicObject dynamicObject = new BeanDynamicObject(object).withNotImplementsMissing();
                dynamicObject.getProperty(name, result);
                if (result.isFound()) {
                    return;
                }
            }
        }

        public Object propertyMissing(String name) {
            return getProperty(name);
        }

        @Override
        public void setProperty(String name, Object value, SetPropertyResult result) {
            checkExtensionIsNotReassigned(name);
            for (Object object : plugins.values()) {
                BeanDynamicObject dynamicObject = new BeanDynamicObject(object).withNotImplementsMissing();
                dynamicObject.setProperty(name, value, result);
                if (result.isFound()) {
                    return;
                }
            }
        }

        public void propertyMissing(String name, Object value) {
            setProperty(name, value);
        }

        @Override
        public void invokeMethod(String name, InvokeMethodResult result, Object... args) {
            if (isConfigureExtensionMethod(name, args)) {
                result.result(configureExtension(name, args));
                return;
            }
            for (Object object : plugins.values()) {
                BeanDynamicObject dynamicObject = new BeanDynamicObject(object).withNotImplementsMissing();
                dynamicObject.invokeMethod(name, result, args);
                if (result.isFound()) {
                    return;
                }
            }
        }

        public Object methodMissing(String name, Object args) {
            return invokeMethod(name, (Object[]) args);
        }

        @Override
        public boolean hasMethod(String name, Object... args) {
            if (isConfigureExtensionMethod(name, args)) {
                return true;
            }
            for (Object object : plugins.values()) {
                BeanDynamicObject dynamicObject = new BeanDynamicObject(object);
                if (dynamicObject.hasMethod(name, args)) {
                    return true;
                }
            }
            return false;
        }
    }

    private void checkExtensionIsNotReassigned(String name) {
        if (extensionsStorage.hasExtension(name)) {
            throw new IllegalArgumentException(
                format("There's an extension registered with name '%s'. You should not reassign it via a property setter.", name));
        }
    }

    private boolean isConfigureExtensionMethod(String name, Object[] args) {
        return args.length == 1 && args[0] instanceof Closure && extensionsStorage.hasExtension(name);
    }

    private Object configureExtension(String name, Object[] args) {
        Closure closure = (Closure) args[0];
        Action<Object> action = ConfigureUtil.configureUsing(closure);
        return extensionsStorage.configureExtension(name, action);
    }
}
