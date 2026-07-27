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

import org.gradle.api.Describable;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Presents a {@link DynamicObject} view of multiple objects at once.
 *
 * Can be used to provide a dynamic view of an object with enhancements.
 */
@NullMarked
public class CompositeDynamicObject extends AbstractDynamicObject {

    private final List<DynamicObject> objects;
    private final Describable displayName;

    public CompositeDynamicObject(List<DynamicObject> objects, Describable displayName) {
        this.objects = objects;
        this.displayName = displayName;
    }

    @Override
    public String getDisplayName() {
        return displayName.getDisplayName();
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
        for (DynamicObject object : objects) {
            DynamicInvokeResult result = object.trySetProperty(name, value);
            if (result.isFound()) {
                return result;
            }
        }
        return DynamicInvokeResult.notFound();
    }

    @Override
    public DynamicInvokeResult trySetPropertyWithoutInstrumentation(String name, @Nullable Object value) {
        for (DynamicObject object : objects) {
            DynamicInvokeResult result = object.trySetPropertyWithoutInstrumentation(name, value);
            if (result.isFound()) {
                return result;
            }
        }
        return DynamicInvokeResult.notFound();
    }

    @Override
    public Map<String, @Nullable Object> getProperties() {
        Map<String, @Nullable Object> properties = new HashMap<>();
        for (int i = objects.size() - 1; i >= 0; i--) {
            properties.putAll(objects.get(i).getProperties());
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
