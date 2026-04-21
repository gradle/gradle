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
import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;
import org.codehaus.groovy.runtime.GeneratedClosure;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.internal.instantiation.InstanceGenerator;
import org.gradle.internal.metaobject.AbstractDynamicObject;
import org.gradle.internal.metaobject.BeanDynamicObject;
import org.gradle.internal.metaobject.CompositeDynamicObject;
import org.gradle.internal.metaobject.DynamicInvokeResult;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.internal.metaobject.DynamicObjectUtil;
import org.gradle.internal.metaobject.HierarchicalDynamicObject;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link DynamicObject} implementation that provides extensibility.
 *
 * This is the dynamic object implementation that "enhanced" objects expose.
 *
 * @see org.gradle.internal.instantiation.generator.MixInExtensibleDynamicObject
 */
public class ExtensibleDynamicObject extends AbstractDynamicObject implements HierarchicalDynamicObject {

    public enum Location {
        BeforeConventionNotInherited, BeforeConvention, AfterConvention
    }

    private final AbstractDynamicObject dynamicDelegate;
    private final DefaultExtensionContainer extensionContainer;
    private final DynamicObject extraPropertiesDynamicObject;

    @Nullable
    private DynamicObject beforeConventionNotInherited;
    @Nullable
    private DynamicObject beforeConvention;
    @Nullable
    private DynamicObject afterConvention;

    // Combines the bean, extra properties, conventions, and extensions into a single lookup.
    // Does not include the parent hierarchy — parent is walked separately.
    private CompositeDynamicObject delegateObjects;
    @Nullable
    private HierarchicalDynamicObject parent;

    public ExtensibleDynamicObject(Object delegate, Class<?> publicType, InstanceGenerator instanceGenerator) {
        this(delegate, new BeanDynamicObject(delegate, publicType), new DefaultExtensionContainer(instanceGenerator));
    }

    public ExtensibleDynamicObject(Object delegate, AbstractDynamicObject dynamicDelegate, InstanceGenerator instanceGenerator) {
        this(delegate, dynamicDelegate, new DefaultExtensionContainer(instanceGenerator));
    }

    public ExtensibleDynamicObject(Object delegate, AbstractDynamicObject dynamicDelegate, DefaultExtensionContainer convention) {
        this.dynamicDelegate = dynamicDelegate;
        this.extensionContainer = convention;
        this.extraPropertiesDynamicObject = new ExtraPropertiesDynamicObjectAdapter(delegate.getClass(), convention.getExtraProperties());

        updateDelegates();
    }

    private void updateDelegates() {
        List<DynamicObject> delegates = new ArrayList<>(5);
        delegates.add(dynamicDelegate);
        delegates.add(extraPropertiesDynamicObject);
        if (beforeConventionNotInherited != null) {
            delegates.add(beforeConventionNotInherited);
        }
        if (beforeConvention != null) {
            delegates.add(beforeConvention);
        }
        delegates.add(extensionContainer.getExtensionsAsDynamicObject());
        if (afterConvention != null) {
            delegates.add(afterConvention);
        }
        this.delegateObjects = new CompositeDynamicObject(delegates, this::getDisplayName);
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
        return extensionContainer.getExtraProperties();
    }

    @Override
    @Nullable
    public HierarchicalDynamicObject getParent() {
        return parent;
    }

    public void setParent(@Nullable HierarchicalDynamicObject parent) {
        this.parent = parent;
    }

    public ExtensionContainer getExtensions() {
        return extensionContainer;
    }

    public void addObject(DynamicObject object, Location location) {
        switch (location) {
            case BeforeConventionNotInherited:
                beforeConventionNotInherited = object;
                break;
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
    public HierarchicalDynamicObject getInheritable() {
        return new InheritedDynamicObject();
    }

    private DynamicObject snapshotInheritable() {
        List<DynamicObject> delegates = new ArrayList<>(4);
        delegates.add(extraPropertiesDynamicObject);
        if (beforeConvention != null) {
            delegates.add(beforeConvention);
        }
        delegates.add(extensionContainer.getExtensionsAsDynamicObject());
        if (parent != null) {
            delegates.add(parent);
        }
        return new CompositeDynamicObject(delegates, dynamicDelegate::getDisplayName);
    }

    @Override
    public boolean hasProperty(String name) {
        if (delegateObjects.hasProperty(name)) {
            return true;
        }
        HierarchicalDynamicObject parent = this.parent;
        while (parent != null) {
            if (parent.hasProperty(name)) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    @Override
    public DynamicInvokeResult tryGetProperty(String name) {
        DynamicInvokeResult result = delegateObjects.tryGetProperty(name);
        if (result.isFound()) {
            return result;
        }
        HierarchicalDynamicObject parent = this.parent;
        while (parent != null) {
            result = parent.tryGetProperty(name);
            if (result.isFound()) {
                return result;
            }
            parent = parent.getParent();
        }
        return DynamicInvokeResult.notFound();
    }

    @Override
    public DynamicInvokeResult trySetProperty(String name, @Nullable Object value) {
        return delegateObjects.trySetProperty(name, value);
    }

    @Override
    public DynamicInvokeResult trySetPropertyWithoutInstrumentation(String name, @Nullable Object value) {
        return delegateObjects.trySetPropertyWithoutInstrumentation(name, value);
    }

    @Override
    @Deprecated
    public Map<String, @Nullable Object> getProperties() {
        Map<String, @Nullable Object> properties = new HashMap<>();

        // Push parent properties in reverse order, giving priority
        // to child properties.
        Deque<HierarchicalDynamicObject> parents = new ArrayDeque<>(5);
        HierarchicalDynamicObject parent = this.parent;
        while (parent != null) {
            parents.push(parent);
            parent = parent.getParent();
        }
        while ((parent = parents.poll()) != null) {
            properties.putAll(parent.getProperties());
        }

        properties.putAll(delegateObjects.getProperties());
        properties.put("properties", properties);
        return properties;
    }

    @Override
    public boolean hasMethod(String name, @Nullable Object... arguments) {
        if (delegateObjects.hasMethod(name, arguments)) {
            return true;
        }
        HierarchicalDynamicObject parent = this.parent;
        while (parent != null) {
            if (parent.hasMethod(name, arguments)) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    private DynamicInvokeResult doTryInvokeMethod(String name, @Nullable Object... arguments) {
        DynamicInvokeResult result = delegateObjects.tryInvokeMethod(name, arguments);
        if (result.isFound()) {
            return result;
        }
        HierarchicalDynamicObject parent = this.parent;
        while (parent != null) {
            result = parent.tryInvokeMethod(name, arguments);
            if (result.isFound()) {
                return result;
            }
            parent = parent.getParent();
        }
        return DynamicInvokeResult.notFound();
    }

    @Override
    public DynamicInvokeResult tryInvokeMethod(String name, @Nullable Object... arguments) {
        // First, try to find an actual method on delegates or parents
        DynamicInvokeResult result = doTryInvokeMethod(name, arguments);
        if (result.isFound()) {
            return result;
        }

        // No method found — fall back to looking for a property with the method name
        // whose value can be invoked (Closure, NamedDomainObjectContainer, or callable)
        DynamicInvokeResult propertyResult = tryGetProperty(name);
        if (!propertyResult.isFound()) {
            return DynamicInvokeResult.notFound();
        }

        Object property = propertyResult.getValue();

        // Closure property: invoke it as a method via doCall
        if (property instanceof Closure) {
            Closure<?> closure = (Closure<?>) property;
            closure.setResolveStrategy(Closure.DELEGATE_FIRST);
            BeanDynamicObject dynamicObject = new BeanDynamicObject(closure);
            result = dynamicObject.tryInvokeMethod("doCall", arguments);
            if (!result.isFound() && !(closure instanceof GeneratedClosure)) {
                return DynamicInvokeResult.found(closure.call(arguments));
            }
            return result;
        }

        // NamedDomainObjectContainer property: configure it with the closure argument
        if (property instanceof NamedDomainObjectContainer && arguments.length == 1 && arguments[0] instanceof Closure) {
            ((NamedDomainObjectContainer<?>) property).configure((Closure<?>) arguments[0]);
            return DynamicInvokeResult.found();
        }

        // Any other callable property: try invoking call() on it
        DynamicObject dynamicObject = DynamicObjectUtil.asDynamicObject(property);
        if (dynamicObject.hasMethod("call", arguments)) {
            return dynamicObject.tryInvokeMethod("call", arguments);
        }
        return DynamicInvokeResult.notFound();
    }

    private class InheritedDynamicObject implements HierarchicalDynamicObject {

        @Override
        public @Nullable HierarchicalDynamicObject getParent() {
            return parent;
        }

        @Override
        public String getDisplayName() {
            return dynamicDelegate.getDisplayName();
        }

        @Override
        public void setProperty(String name, @Nullable Object value) {
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
        public MissingMethodException methodMissingException(String name, @Nullable Object... params) {
            return dynamicDelegate.methodMissingException(name, params);
        }

        @Override
        public DynamicInvokeResult trySetProperty(String name, @Nullable Object value) {
            setProperty(name, value);
            return DynamicInvokeResult.found();
        }

        @Override
        public DynamicInvokeResult trySetPropertyWithoutInstrumentation(String name, @Nullable Object value) {
            setProperty(name, value);
            return DynamicInvokeResult.found();
        }

        @Override
        public boolean hasProperty(String name) {
            return snapshotInheritable().hasProperty(name);
        }

        @Override
        @Nullable
        public Object getProperty(String name) {
            return snapshotInheritable().getProperty(name);
        }

        @Override
        public DynamicInvokeResult tryGetProperty(String name) {
            return snapshotInheritable().tryGetProperty(name);
        }

        @Override
        public Map<String, ? extends @Nullable Object> getProperties() {
            return snapshotInheritable().getProperties();
        }

        @Override
        public boolean hasMethod(String name, @Nullable Object... arguments) {
            return snapshotInheritable().hasMethod(name, arguments);
        }

        @Override
        public DynamicInvokeResult tryInvokeMethod(String name, @Nullable Object... arguments) {
            return snapshotInheritable().tryInvokeMethod(name, arguments);
        }

        @Override
        @Nullable
        public Object invokeMethod(String name, @Nullable Object... arguments) {
            return snapshotInheritable().invokeMethod(name, arguments);
        }

    }
}
