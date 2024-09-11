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
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.instantiation.InstanceGenerator;
import org.gradle.internal.metaobject.AbstractDynamicObject;
import org.gradle.internal.metaobject.BeanDynamicObject;
import org.gradle.internal.metaobject.DynamicInvokeResult;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.util.internal.ConfigureUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static org.gradle.api.reflect.TypeOf.typeOf;

@Deprecated
public class DefaultConvention implements org.gradle.api.plugins.Convention, ExtensionContainerInternal {
    private static final TypeOf<ExtraPropertiesExtension> EXTRA_PROPERTIES_EXTENSION_TYPE = typeOf(ExtraPropertiesExtension.class);
    private final DefaultConvention.ExtensionsDynamicObject extensionsDynamicObject = new ExtensionsDynamicObject();
    private final ExtensionsStorage extensionsStorage = new ExtensionsStorage();
    private final ExtraPropertiesExtension extraProperties = new DefaultExtraPropertiesExtension();
    private final InstanceGenerator instanceGenerator;

    private Map<String, Object> plugins;
    private IdentityHashMap<Object, BeanDynamicObject> dynamicObjects;

    public DefaultConvention(InstanceGenerator instanceGenerator) {
        this.instanceGenerator = instanceGenerator;
        add(EXTRA_PROPERTIES_EXTENSION_TYPE, ExtraPropertiesExtension.EXTENSION_NAME, extraProperties);
    }

    @Deprecated
    @Override
    public Map<String, Object> getPlugins() {
        logConventionDeprecation();
        if (plugins == null) {
            plugins = new LinkedHashMap<>();
        }
        return plugins;
    }

    @Override
    public DynamicObject getExtensionsAsDynamicObject() {
        // This implementation of Convention doesn't log a deprecation warning
        // because it mixes both extensions and conventions.
        // Instead, the returned object logs a deprecation warning when
        // a convention is actually accessed.
        return extensionsDynamicObject;
    }

    @Deprecated
    @Override
    public <T> T getPlugin(Class<T> type) {
        T value = findPlugin(type);
        if (value == null) {
            throw new IllegalStateException(
                format("Could not find any convention object of type %s.", type.getSimpleName()));
        }
        return value;
    }

    @Deprecated
    @Override
    public <T> T findPlugin(Class<T> type) throws IllegalStateException {
        logConventionDeprecation();
        if (plugins == null) {
            return null;
        }
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
            if (extensionsStorage.hasExtension(name)) {
                return true;
            }
            if (plugins == null) {
                return false;
            }
            for (Object object : plugins.values()) {
                if (asDynamicObject(object).hasProperty(name)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Map<String, Object> getProperties() {
            Map<String, Object> properties = new HashMap<String, Object>();
            if (plugins != null) {
                List<Object> reverseOrder = new ArrayList<Object>(plugins.values());
                Collections.reverse(reverseOrder);
                for (Object object : reverseOrder) {
                    properties.putAll(asDynamicObject(object).getProperties());
                }
            }
            properties.putAll(extensionsStorage.getAsMap());
            return properties;
        }

        @Override
        public DynamicInvokeResult tryGetProperty(String name) {
            Object extension = extensionsStorage.findByName(name);
            if (extension != null) {
                return DynamicInvokeResult.found(extension);
            }
            if (plugins == null) {
                return DynamicInvokeResult.notFound();
            }
            for (Object object : plugins.values()) {
                DynamicObject dynamicObject = asDynamicObject(object).withNotImplementsMissing();
                DynamicInvokeResult result = dynamicObject.tryGetProperty(name);
                if (result.isFound()) {
                    return result;
                }
            }
            return DynamicInvokeResult.notFound();
        }

        @Nullable
        @SuppressWarnings("unused") // Groovy magic method
        public Object propertyMissing(String name) {
            return getProperty(name);
        }

        @Override
        public DynamicInvokeResult trySetProperty(String name, @Nullable Object value) {
            checkExtensionIsNotReassigned(name);
            if (plugins == null) {
                return DynamicInvokeResult.notFound();
            }
            for (Object object : plugins.values()) {
                BeanDynamicObject dynamicObject = asDynamicObject(object).withNotImplementsMissing();
                DynamicInvokeResult result = dynamicObject.trySetProperty(name, value);
                if (result.isFound()) {
                    return result;
                }
            }
            return DynamicInvokeResult.notFound();
        }

        @SuppressWarnings("unused")  // Groovy magic method
        public void propertyMissing(String name, Object value) {
            setProperty(name, value);
        }

        @Override
        public DynamicInvokeResult tryInvokeMethod(String name, @Nullable Object... args) {
            if (isConfigureExtensionMethod(name, args)) {
                return DynamicInvokeResult.found(configureExtension(name, args));
            }
            if (plugins == null) {
                return DynamicInvokeResult.notFound();
            }
            for (Object object : plugins.values()) {
                BeanDynamicObject dynamicObject = asDynamicObject(object).withNotImplementsMissing();
                DynamicInvokeResult result = dynamicObject.tryInvokeMethod(name, args);
                if (result.isFound()) {
                    return result;
                }
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
            if (isConfigureExtensionMethod(name, args)) {
                return true;
            }
            if (plugins == null) {
                return false;
            }
            for (Object object : plugins.values()) {
                BeanDynamicObject dynamicObject = asDynamicObject(object);
                if (dynamicObject.hasMethod(name, args)) {
                    return true;
                }
            }
            return false;
        }

        private BeanDynamicObject asDynamicObject(Object object) {
            if (dynamicObjects == null) {
                dynamicObjects = new IdentityHashMap<>();
            }
            BeanDynamicObject dynamicObject = dynamicObjects.get(object);
            if (dynamicObject == null) {
                dynamicObject = new BeanDynamicObject(object);
                dynamicObjects.put(object, dynamicObject);
            }
            return dynamicObject;
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

    private static void logConventionDeprecation() {
        DeprecationLogger.deprecateType(org.gradle.api.plugins.Convention.class)
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "deprecated_access_to_conventions")
            .nagUser();
    }
}
