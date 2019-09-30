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

import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;
import org.gradle.api.internal.HasConvention;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.internal.instantiation.InstanceGenerator;
import org.gradle.internal.metaobject.AbstractDynamicObject;
import org.gradle.internal.metaobject.BeanDynamicObject;
import org.gradle.internal.metaobject.CompositeDynamicObject;
import org.gradle.internal.metaobject.DynamicInvokeResult;
import org.gradle.internal.metaobject.DynamicObject;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A {@link DynamicObject} implementation that provides extensibility.
 *
 * This is the dynamic object implementation that “enhanced” objects expose.
 *
 * @see org.gradle.api.internal.AsmBackedClassGenerator.MixInExtensibleDynamicObject
 */
public class ExtensibleDynamicObject extends MixInClosurePropertiesAsMethodsDynamicObject implements HasConvention {

    public enum Location {
        BeforeConvention, AfterConvention
    }

    private final AbstractDynamicObject dynamicDelegate;
    private DynamicObject parent;
    private Convention convention;
    private DynamicObject beforeConvention;
    private DynamicObject afterConvention;
    private DynamicObject extraPropertiesDynamicObject;

    public ExtensibleDynamicObject(Object delegate, Class<?> publicType, InstanceGenerator instanceGenerator) {
        this(delegate, createDynamicObject(delegate, publicType), new DefaultConvention(instanceGenerator));
    }

    public ExtensibleDynamicObject(Object delegate, AbstractDynamicObject dynamicDelegate, InstanceGenerator instanceGenerator) {
        this(delegate, dynamicDelegate, new DefaultConvention(instanceGenerator));
    }

    public ExtensibleDynamicObject(Object delegate, AbstractDynamicObject dynamicDelegate, Convention convention) {
        this.dynamicDelegate = dynamicDelegate;
        this.convention = convention;
        this.extraPropertiesDynamicObject = new ExtraPropertiesDynamicObjectAdapter(delegate.getClass(), convention.getExtraProperties());

        updateDelegates();
    }

    private static BeanDynamicObject createDynamicObject(Object delegate, Class<?> publicType) {
        return new BeanDynamicObject(delegate, publicType);
    }

    private void updateDelegates() {
        DynamicObject[] delegates = new DynamicObject[6];
        delegates[0] = dynamicDelegate;
        delegates[1] = extraPropertiesDynamicObject;
        int idx = 2;
        if (beforeConvention != null) {
            delegates[idx++] = beforeConvention;
        }
        if (convention != null) {
            delegates[idx++] = convention.getExtensionsAsDynamicObject();
        }
        if (afterConvention != null) {
            delegates[idx++] = afterConvention;
        }
        boolean addedParent = false;
        if (parent != null) {
            addedParent = true;
            delegates[idx++] = parent;
        }
        DynamicObject[] objects = new DynamicObject[idx];
        System.arraycopy(delegates, 0, objects, 0, idx);
        setObjects(objects);

        if (addedParent) {
            idx--;
            objects = new DynamicObject[idx];
            System.arraycopy(delegates, 0, objects, 0, idx);
            setObjectsForUpdate(objects);
        }
    }

    @Override
    public String getDisplayName() {
        return dynamicDelegate.getDisplayName();
    }

    @Nullable
    @Override
    public Class<?> getPublicType() {
        return dynamicDelegate.getPublicType();
    }

    @Override
    public boolean hasUsefulDisplayName() {
        return dynamicDelegate.hasUsefulDisplayName();
    }

    public ExtraPropertiesExtension getDynamicProperties() {
        return convention.getExtraProperties();
    }

    public void addProperties(Map<String, ?> properties) {
        for (Map.Entry<String, ?> entry : properties.entrySet()) {
            getDynamicProperties().set(entry.getKey(), entry.getValue());
        }
    }

    public DynamicObject getParent() {
        return parent;
    }

    public void setParent(DynamicObject parent) {
        this.parent = parent;
        updateDelegates();
    }

    @Override
    public Convention getConvention() {
        return convention;
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

    private DynamicObject snapshotInheritable() {
        final List<DynamicObject> delegates = new ArrayList<DynamicObject>(4);
        delegates.add(extraPropertiesDynamicObject);
        if (beforeConvention != null) {
            delegates.add(beforeConvention);
        }
        delegates.add(convention.getExtensionsAsDynamicObject());
        if (parent != null) {
            delegates.add(parent);
        }
        return new CompositeDynamicObject() {
            {
                setObjects(delegates.toArray(new DynamicObject[0]));
            }

            @Override
            public String getDisplayName() {
                return dynamicDelegate.getDisplayName();
            }
        };
    }

    private class InheritedDynamicObject implements DynamicObject {
        @Override
        public void setProperty(String name, Object value) {
            throw new MissingPropertyException(String.format("Could not find property '%s' inherited from %s.", name,
                    dynamicDelegate.getDisplayName()));
        }

        @Override
        public MissingPropertyException getMissingProperty(String name) {
            return dynamicDelegate.getMissingProperty(name);
        }

        @Override
        public MissingPropertyException setMissingProperty(String name) {
            return dynamicDelegate.setMissingProperty(name);
        }

        @Override
        public MissingMethodException methodMissingException(String name, Object... params) {
            return dynamicDelegate.methodMissingException(name, params);
        }

        @Override
        public DynamicInvokeResult trySetProperty(String name, Object value) {
            setProperty(name, value);
            return DynamicInvokeResult.found();
        }

        @Override
        public boolean hasProperty(String name) {
            return snapshotInheritable().hasProperty(name);
        }

        @Override
        public Object getProperty(String name) {
            return snapshotInheritable().getProperty(name);
        }

        @Override
        public DynamicInvokeResult tryGetProperty(String name) {
            return snapshotInheritable().tryGetProperty(name);
        }

        @Override
        public Map<String, ?> getProperties() {
            return snapshotInheritable().getProperties();
        }

        @Override
        public boolean hasMethod(String name, Object... arguments) {
            return snapshotInheritable().hasMethod(name, arguments);
        }

        @Override
        public DynamicInvokeResult tryInvokeMethod(String name, Object... arguments) {
            return snapshotInheritable().tryInvokeMethod(name, arguments);
        }

        @Override
        public Object invokeMethod(String name, Object... arguments) {
            return snapshotInheritable().invokeMethod(name, arguments);
        }

    }
}

