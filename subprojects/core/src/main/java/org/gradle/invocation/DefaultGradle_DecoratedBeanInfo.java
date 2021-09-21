/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.invocation;

import groovy.lang.Closure;
import groovy.lang.GroovyRuntimeException;
import groovy.lang.MetaClass;
import org.gradle.api.Action;
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.metaobject.BeanDynamicObject;

import java.awt.Image;
import java.beans.BeanDescriptor;
import java.beans.BeanInfo;
import java.beans.EventSetDescriptor;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.MethodDescriptor;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

import static org.gradle.internal.UncheckedException.throwAsUncheckedException;

/**
 * This {@link BeanInfo} class provides a corrected subset of PropertyDescriptors for the {@link Introspector}
 * to fix its miscategorization of: 1) the generated {@code Gradle.settings} property as well as 2) the
 * {@link Gradle#settingsEvaluated(Action)} and {@link Gradle#settingsEvaluated(Closure)} methods.
 *
 * These were being miscategorised into PropertyDescriptors by the {@link Introspector} since they look like
 * JavaBeans setter methods. As a consequence the two {@code Gradle.settingsEvaluated()} functions were being
 * reported as a write-only property named {@code tingsEvaluated} by the {@link MetaClass#getProperties()}
 * function. This in turn was triggering a {@link GroovyRuntimeException} to be thrown when the
 * {@link BeanDynamicObject#getProperties()} function was evaluated on {@link Gradle} objects, making it
 * impossible use the {@link Gradle} object's properties values directly in init scripts like we do for
 * settings and project scripts.
 */
@SuppressWarnings("typeName")
public class DefaultGradle_DecoratedBeanInfo implements BeanInfo {
    private final Method getBaseNameMethod;
    private final BeanInfo delegate;

    public DefaultGradle_DecoratedBeanInfo() throws IntrospectionException, NoSuchMethodException, ClassNotFoundException {
        // work around: PropertyDescriptor.getBaseName() is package private
        Method getBaseNameMethod = PropertyDescriptor.class.getDeclaredMethod("getBaseName");
        getBaseNameMethod.setAccessible(true);
        this.getBaseNameMethod = getBaseNameMethod;

        // work around: the "DefaultGradle_Decorated" class is not available at compile time
        Class<? extends DefaultGradle> defaultGradleClass =
            Class.forName("org.gradle.invocation.DefaultGradle_Decorated").asSubclass(DefaultGradle.class);
        this.delegate = Introspector.getBeanInfo(defaultGradleClass, Introspector.IGNORE_IMMEDIATE_BEANINFO);
    }

    @Override
    public PropertyDescriptor[] getPropertyDescriptors() {
        // filter out the miscategorised Bean Properties
        PropertyDescriptor[] propertyDescriptors = delegate.getPropertyDescriptors();
        if (null == propertyDescriptors) {
            return null;
        }

        int idx = 0;

        for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
            // Filter out any descriptors where the baseName == name. In other words: keep only the
            // propertyDescriptors where camel casing indicates that a Bean Property was intended. For example:
            // camel casing in getXxxx()/setXxxx(), indicates an intentional Bean Property with baseName = "Xxxx"
            // and name = "xxxx", while the lack of camel casing in settingsEvaluated() indicates that it is not
            // intended to be Bean Property with baseName = "tingsEvaluated" and name = "tingsEvaluated".
            try {
                if (!Objects.equals(getBaseNameMethod.invoke(propertyDescriptor), propertyDescriptor.getName())) {
                    propertyDescriptors[idx++] = propertyDescriptor;
                }
            } catch (IllegalAccessException | InvocationTargetException ex) {
                // this should never happen
                throwAsUncheckedException(ex);
            }
        }

        PropertyDescriptor[] result = new PropertyDescriptor[idx];
        System.arraycopy(propertyDescriptors, 0, result, 0, idx);

        return result;
    }

    @Override
    public BeanDescriptor getBeanDescriptor() {
        return delegate.getBeanDescriptor();
    }

    @Override
    public EventSetDescriptor[] getEventSetDescriptors() {
        return delegate.getEventSetDescriptors();
    }

    @Override
    public int getDefaultEventIndex() {
        return delegate.getDefaultEventIndex();
    }

    @Override
    public int getDefaultPropertyIndex() {
        return delegate.getDefaultPropertyIndex();
    }

    @Override
    public MethodDescriptor[] getMethodDescriptors() {
        return delegate.getMethodDescriptors();
    }

    @Override
    public BeanInfo[] getAdditionalBeanInfo() {
        return delegate.getAdditionalBeanInfo();
    }

    @Override
    public Image getIcon(int iconKind) {
        return delegate.getIcon(iconKind);
    }
}
