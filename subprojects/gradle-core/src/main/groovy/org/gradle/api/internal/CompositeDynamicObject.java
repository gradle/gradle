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
package org.gradle.api.internal;

import groovy.lang.*;
import groovy.lang.MissingMethodException;

import java.util.HashMap;
import java.util.Map;

public abstract class CompositeDynamicObject extends AbstractDynamicObject {
    private DynamicObject[] objects = new DynamicObject[0];
    private DynamicObject[] updateObjects = new DynamicObject[0];

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
        return super.hasProperty(name);
    }

    @Override
    public Object getProperty(String name) throws MissingPropertyException {
        for (DynamicObject object : objects) {
            if (object.hasProperty(name)) {
                return object.getProperty(name);
            }
        }
        return super.getProperty(name);
    }

    @Override
    public void setProperty(String name, Object value) throws MissingPropertyException {
        for (DynamicObject object : updateObjects) {
            if (object.hasProperty(name)) {
                object.setProperty(name, value);
                return;
            }
        }
        updateObjects[updateObjects.length - 1].setProperty(name, value);
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
    public boolean hasMethod(String name, Object... arguments) {
        for (DynamicObject object : objects) {
            if (object.hasMethod(name, arguments)) {
                return true;
            }
        }
        return super.hasMethod(name, arguments);
    }

    @Override
    public Object invokeMethod(String name, Object... arguments) throws MissingMethodException {
        for (DynamicObject object : objects) {
            if (object.hasMethod(name, arguments)) {
                return object.invokeMethod(name, arguments);
            }
        }

        if (hasProperty(name)) {
            Object property = getProperty(name);
            if (property instanceof Closure) {
                Closure closure = (Closure) property;
                closure.setResolveStrategy(Closure.DELEGATE_FIRST);
                return closure.call(arguments);
            }
        }

        return super.invokeMethod(name, arguments);
    }
}
