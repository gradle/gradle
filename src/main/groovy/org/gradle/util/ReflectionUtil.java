/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.util;

import org.gradle.api.GradleException;

import java.beans.PropertyDescriptor;
import java.beans.Introspector;
import java.beans.IntrospectionException;
import java.lang.reflect.InvocationTargetException;

/**
 * @author Hans Dockter
 */
public class ReflectionUtil {
    public static PropertyDescriptor getPropertyDescriptor(Class clazz, String key) {
        try {
            for (PropertyDescriptor prop : Introspector.getBeanInfo(clazz, java.lang.Object.class).getPropertyDescriptors())
                if (key.equals(prop.getName()))
                    return prop;
        } catch (IntrospectionException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public static boolean hasProperty(Class clazz, String prop) {
        return null != getPropertyDescriptor(clazz, prop);
    }

    public static boolean hasProperty(Object obj, String prop) {
        return null != getPropertyDescriptor(obj.getClass(), prop);
    }

    public static PropertyDescriptor getPropertyDescriptor(Object obj, String key) {
        return getPropertyDescriptor(obj.getClass(), key);
    }

    public static Object getProperty(Object obj, String key) {
        try {
            return getPropertyDescriptor(obj, key).getReadMethod().invoke(obj);
        } catch (IllegalAccessException e) {
            throw new GradleException(e);
        } catch (InvocationTargetException e) {
            throw new GradleException(e);
        }
    }

    public static void setProperty(Object obj, String prop, Object arg) {
        try {
            obj.getClass().getMethod("set" + prop.substring(0, 1).toUpperCase() + prop.substring(1), arg.getClass()).invoke(obj, arg);
        } catch (NoSuchMethodException e) {
            throw new GradleException(e);
        } catch (IllegalAccessException e) {
            throw new GradleException(e);
        } catch (InvocationTargetException e) {
            throw new GradleException(e);
        }
    }
}
