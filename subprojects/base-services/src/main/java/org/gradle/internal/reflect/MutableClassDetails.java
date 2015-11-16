/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.reflect;

import java.lang.reflect.Method;
import java.util.*;

class MutableClassDetails implements ClassDetails {
    private final Class<?> type;
    private final MethodSet instanceMethods = new MethodSet();
    private final Map<String, MutablePropertyDetails> properties = new TreeMap<String, MutablePropertyDetails>();
    private final List<Method> methods = new ArrayList<Method>();
    private final Set<Class<?>> superTypes = new LinkedHashSet<Class<?>>();

    MutableClassDetails(Class<?> type) {
        this.type = type;
    }

    @Override
    public List<Method> getAllMethods() {
        return methods;
    }

    @Override
    public List<Method> getInstanceMethods() {
        return instanceMethods.getValues();
    }

    @Override
    public Set<Class<?>> getSuperTypes() {
        return superTypes;
    }

    @Override
    public Set<String> getPropertyNames() {
        return properties.keySet();
    }

    @Override
    public Collection<? extends PropertyDetails> getProperties() {
        return properties.values();
    }

    @Override
    public PropertyDetails getProperty(String name) throws NoSuchPropertyException {
        MutablePropertyDetails property = properties.get(name);
        if (property == null) {
            throw new NoSuchPropertyException(String.format("No property '%s' found on %s.", name, type));
        }
        return property;
    }

    void superType(Class<?> type) {
        superTypes.add(type);
    }

    void method(Method method) {
        methods.add(method);
    }

    void instanceMethod(Method method) {
        instanceMethods.add(method);
    }

    MutablePropertyDetails property(String propertyName) {
        MutablePropertyDetails property = properties.get(propertyName);
        if (property == null) {
            property = new MutablePropertyDetails(propertyName);
            properties.put(propertyName, property);
        }
        return property;
    }
}
