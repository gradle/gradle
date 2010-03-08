/*
 * Copyright 2010 the original author or authors.
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

import groovy.lang.MissingPropertyException;
import org.gradle.api.plugins.Convention;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DynamicObjectHelper extends CompositeDynamicObject {
    public enum Location {
        BeforeConvention, AfterConvention
    }

    private final AbstractDynamicObject delegateObject;
    private DynamicObject parent;
    private Convention convention;
    private DynamicObject beforeConvention;
    private DynamicObject afterConvention;
    private MapBackedDynamicObject additionalProperties;

    public DynamicObjectHelper(Object delegateObject) {
        this(new BeanDynamicObject(delegateObject), null);
    }

    public DynamicObjectHelper(Object delegateObject, Convention convention) {
        this(new BeanDynamicObject(delegateObject), convention);
    }

    public DynamicObjectHelper(AbstractDynamicObject delegateObject, Convention convention) {
        this.delegateObject = delegateObject;
        additionalProperties = new MapBackedDynamicObject(delegateObject);
        setConvention(convention);
    }

    private void updateDelegates() {
        List<DynamicObject> delegates = new ArrayList<DynamicObject>();
        delegates.add(delegateObject);
        delegates.add(additionalProperties);
        if (beforeConvention != null) {
            delegates.add(beforeConvention);
        }
        if (convention != null) {
            delegates.add(convention);
        }
        if (afterConvention != null) {
            delegates.add(afterConvention);
        }
        if (parent != null) {
            delegates.add(parent);
        }
        setObjects(delegates.toArray(new DynamicObject[delegates.size()]));

        delegates.remove(parent);
        delegates.add(additionalProperties);
        setObjectsForUpdate(delegates.toArray(new DynamicObject[delegates.size()]));
    }

    protected String getDisplayName() {
        return delegateObject.getDisplayName();
    }

    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties.getProperties();
    }

    public DynamicObject getParent() {
        return parent;
    }

    public void setParent(DynamicObject parent) {
        this.parent = parent;
        updateDelegates();
    }

    public Convention getConvention() {
        return convention;
    }

    public void setConvention(Convention convention) {
        this.convention = convention;
        updateDelegates();
    }

    public void addObject(DynamicObject object, Location location) {
        switch (location) {
            case BeforeConvention:
                beforeConvention = object;
                break;
            case AfterConvention:
                afterConvention = object;
        }
        updateDelegates();
    }

    /**
     * Returns the inheritable properties and methods of this object.
     *
     * @return an object containing the inheritable properties and methods of this object.
     */
    public DynamicObject getInheritable() {
        return new InheritedDynamicObject();
    }

    private DynamicObjectHelper snapshotInheritable() {
        AbstractDynamicObject emptyBean = new AbstractDynamicObject() {
            @Override
            protected String getDisplayName() {
                return delegateObject.getDisplayName();
            }
        };

        DynamicObjectHelper helper = new DynamicObjectHelper(emptyBean);

        helper.parent = parent;
        helper.convention = convention;
        helper.additionalProperties = additionalProperties;
        if (beforeConvention != null) {
            helper.beforeConvention = beforeConvention;
        }
        helper.updateDelegates();
        return helper;
    }

    private class InheritedDynamicObject implements DynamicObject {
        public void setProperty(String name, Object value) {
            throw new MissingPropertyException(String.format("Could not find property '%s' inherited from %s.", name,
                    delegateObject.getDisplayName()));
        }

        public boolean hasProperty(String name) {
            return snapshotInheritable().hasProperty(name);
        }

        public Object getProperty(String name) {
            return snapshotInheritable().getProperty(name);
        }

        public Map<String, Object> getProperties() {
            return snapshotInheritable().getProperties();
        }

        public boolean hasMethod(String name, Object... arguments) {
            return snapshotInheritable().hasMethod(name, arguments);
        }

        public Object invokeMethod(String name, Object... arguments) {
            return snapshotInheritable().invokeMethod(name, arguments);
        }
    }
}

