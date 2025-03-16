/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.metaobject;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Presents a {@link DynamicObject} view of multiple objects at once.
 *
 * Can be used to provide a dynamic view of an object with enhancements.
 */
public abstract class CompositeDynamicObject extends AbstractDynamicObject {

    private static final DynamicObject[] NONE = new DynamicObject[0];

    private DynamicObject[] objects = NONE;
    private DynamicObject[] updateObjects = NONE;

    protected void setObjects(DynamicObject... objects) {
        this.objects = objects;
        updateObjects = objects;
    }

    protected void setObjectsForUpdate(DynamicObject... objects) {
        this.updateObjects = objects;
    }

    @Override
    public boolean hasProperty(String name) {
        for (DynamicObject object : objects) {
            if (object.hasProperty(name)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public DynamicInvokeResult tryGetProperty(String name) {
        for (DynamicObject object : objects) {
            DynamicInvokeResult result = object.tryGetProperty(name);
            if (result.isFound()) {
                return result;
            }
        }
        return DynamicInvokeResult.notFound();
    }

    @Override
    public DynamicInvokeResult trySetProperty(String name, @Nullable Object value) {
        for (DynamicObject object : updateObjects) {
            DynamicInvokeResult result = object.trySetProperty(name, value);
            if (result.isFound()) {
                return result;
            }
        }
        return DynamicInvokeResult.notFound();
    }

    @Override
    public Map<String, Object> getProperties() {
        Map<String, Object> properties = new HashMap<String, Object>();
        for (int i = objects.length - 1; i >= 0; i--) {
            DynamicObject object = objects[i];
            properties.putAll(object.getProperties());
        }
        properties.put("properties", properties);
        return properties;
    }

    @Override
    public boolean hasMethod(String name, @Nullable Object... arguments) {
        for (DynamicObject object : objects) {
            if (object.hasMethod(name, arguments)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public DynamicInvokeResult tryInvokeMethod(String name, @Nullable Object... arguments) {
        for (DynamicObject object : objects) {
            DynamicInvokeResult result = object.tryInvokeMethod(name, arguments);
            if (result.isFound()) {
                return result;
            }
        }
        return DynamicInvokeResult.notFound();
    }
}
