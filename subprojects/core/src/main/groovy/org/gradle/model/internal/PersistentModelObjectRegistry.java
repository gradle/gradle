/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.model.internal;

import com.google.common.collect.MapMaker;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.GradleException;
import org.gradle.api.Nullable;
import org.gradle.cache.internal.btree.BTreePersistentIndexedCache;
import org.gradle.internal.CompositeStoppable;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.messaging.serialize.DefaultSerializer;
import org.gradle.model.ModelType;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeMap;

public class PersistentModelObjectRegistry {
    private final BTreePersistentIndexedCache<Object, FlattenedObject> store;
    private final Map<Object, Object> idToInstance;
    private final Map<Object, Object> instanceToId;

    public PersistentModelObjectRegistry(File outputFile) {
        store = new BTreePersistentIndexedCache<Object, FlattenedObject>(outputFile, new DefaultSerializer<Object>(), new DefaultSerializer<FlattenedObject>());
        idToInstance = new MapMaker().weakValues().makeMap();
        instanceToId = new MapMaker().weakKeys().makeMap();
    }

    public void put(Object identifier, Object modelObject) {
        if (modelObject.getClass().getAnnotation(ModelType.class) == null) {
            throw new IllegalArgumentException(String.format("Cannot persist object of class %s, as this class is not marked @%s", modelObject.getClass().getSimpleName(), ModelType.class.getSimpleName()));
        }

        FlattenedObject flattened = new FlattenedObject();
        Class<?> type = modelObject.getClass();
        for (Class<?> current = type; current != Object.class; current = current.getSuperclass()) {
            for (Method method : current.getMethods()) {
                if (method.getName().startsWith("get") && method.getParameterTypes().length == 0 && !Modifier.isStatic(method.getModifiers())) {
                    String prop = StringUtils.uncapitalize(method.getName().substring(3));
                    if (prop.equals("metaClass") || prop.equals("class")) {
                        continue;
                    }
                    Object value;
                    try {
                        value = method.invoke(modelObject);
                    } catch (Exception e) {
                        throw new GradleException(String.format("Could not get property %s for model %s (%s)", prop, identifier, modelObject.getClass().getSimpleName()), e);
                    }
                    if (value != null && value.getClass().getAnnotation(ModelType.class) != null) {
                        Object valueId = instanceToId.get(value);
                        if (valueId == null) {
                            throw new IllegalStateException(String.format("Model %s (%s) references an unknown model object of type %s.", identifier, modelObject.getClass().getSimpleName(), value.getClass().getSimpleName()));
                        }
                        value = new Reference(value.getClass().getName(), valueId);
                    }
                    flattened.properties.put(prop, value);
                }
            }
        }

        idToInstance.put(identifier, modelObject);
        instanceToId.put(modelObject, identifier);
        store.put(identifier, flattened);
    }

    @Nullable
    public <T> T get(Object identifier, Class<T> type) {
        Object modelObject = idToInstance.get(identifier);
        if (modelObject != null) {
            return type.cast(modelObject);
        }

        FlattenedObject flattened = store.get(identifier);
        if (flattened == null) {
            return null;
        }
        try {
            modelObject = type.newInstance();
        } catch (Exception e) {
            throw new GradleException(String.format("Could not create an instance of %s.", type.getSimpleName()), e);
        }
        for (Map.Entry<String, Object> entry : flattened.properties.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Reference) {
                Reference reference = (Reference) value;
                Class<?> referenceType;
                try {
                    referenceType = type.getClassLoader().loadClass(reference.type);
                } catch (ClassNotFoundException e) {
                    throw new GradleException(String.format("Could not locate type %s referenced by model %s (%s)", reference.type, identifier, type.getSimpleName()));
                }
                value = get(reference.identifier, referenceType);
            }
            JavaReflectionUtil.writeProperty(modelObject, entry.getKey(), value);
        }

        idToInstance.put(identifier, modelObject);
        instanceToId.put(modelObject, identifier);
        return type.cast(modelObject);
    }

    public void close() {
        CompositeStoppable.stoppable(store).stop();
    }

    private static class Reference implements Serializable {
        final String type;
        final Object identifier;

        private Reference(String type, Object identifier) {
            this.type = type;
            this.identifier = identifier;
        }
    }

    private static class FlattenedObject implements Serializable {
        Map<String, Object> properties = new TreeMap<String, Object>();
    }

}

