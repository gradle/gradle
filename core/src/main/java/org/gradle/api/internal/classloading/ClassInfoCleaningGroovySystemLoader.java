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

package org.gradle.api.internal.classloading;

import org.gradle.api.GradleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;

public class ClassInfoCleaningGroovySystemLoader implements GroovySystemLoader {

    private final static Logger LOG = LoggerFactory.getLogger(ClassInfoCleaningGroovySystemLoader.class);

    private final Method removeFromGlobalClassValue;
    private final Method globalClassSetIteratorMethod;
    private final Object globalClassValue;
    private final Object globalClassSetItems;
    private Field clazzField;
    private Field classRefField;
    private final ClassLoader leakingLoader;

    public ClassInfoCleaningGroovySystemLoader(ClassLoader leakingLoader) throws Exception {
        this.leakingLoader = leakingLoader;
        // this work has to be done before classes are loaded, otherwise there are risks that
        // the PermGen space is full before we create the reflection methods

        Class<?> classInfoClass = leakingLoader.loadClass("org.codehaus.groovy.reflection.ClassInfo");
        Field globalClassValueField = classInfoClass.getDeclaredField("globalClassValue");
        globalClassValueField.setAccessible(true);
        globalClassValue = globalClassValueField.get(null);
        removeFromGlobalClassValue = globalClassValueField.getType().getDeclaredMethod("remove", Class.class);
        removeFromGlobalClassValue.setAccessible(true);

        Field globalClassSetField = classInfoClass.getDeclaredField("globalClassSet");
        globalClassSetField.setAccessible(true);
        Object globalClassSet = globalClassSetField.get(null);
        globalClassSetField = globalClassSet.getClass().getDeclaredField("items");
        globalClassSetField.setAccessible(true);
        globalClassSetItems = globalClassSetField.get(globalClassSet);
        globalClassSetIteratorMethod = globalClassSetItems.getClass().getDeclaredMethod("iterator");

        try {
            classRefField = classInfoClass.getDeclaredField("classRef");
            classRefField.setAccessible(true);
        } catch (Exception e) {
            clazzField = classInfoClass.getDeclaredField("klazz");
            clazzField.setAccessible(true);
        }
    }

    @Override
    public void shutdown() {
        if (leakingLoader == getClass().getClassLoader()) {
            throw new IllegalStateException("Cannot shut down the main Groovy loader.");
        }
        try {
            Iterator<?> it = globalClassSetIterator();
            while (it.hasNext()) {
                Object classInfo = it.next();
                if (classInfo != null) {
                    Class clazz = getClazz(classInfo);
                    removeFromGlobalClassValue.invoke(globalClassValue, clazz);
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Removed ClassInfo from {} loaded by {}", clazz.getName(), clazz.getClassLoader());
                    }
                }
            }
        } catch (Exception e) {
            throw new GradleException("Could not shut down the Groovy system for " + leakingLoader, e);
        }
    }

    @Override
    public void discardTypesFrom(ClassLoader classLoader) {
        if (classLoader == leakingLoader) {
            throw new IllegalStateException("Cannot remove own types from Groovy loader.");
        }
        try {
            Iterator<?> it = globalClassSetIterator();
            while (it.hasNext()) {
                Object classInfo = it.next();
                if (classInfo != null) {
                    Class clazz = getClazz(classInfo);
                    if (clazz != null && clazz.getClassLoader() == classLoader) {
                        removeFromGlobalClassValue.invoke(globalClassValue, clazz);
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Removed ClassInfo from {} loaded by {}", clazz.getName(), clazz.getClassLoader());
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new GradleException("Could not remove types for ClassLoader " + classLoader + " from the Groovy system " + leakingLoader, e);
        }
    }

    private Class getClazz(Object classInfo) throws IllegalAccessException {
        if (classRefField != null) {
            return (Class) ((WeakReference) classRefField.get(classInfo)).get();
        } else {
            return (Class) clazzField.get(classInfo);
        }
    }

    private Iterator<?> globalClassSetIterator() throws IllegalAccessException, InvocationTargetException {
        return (Iterator) globalClassSetIteratorMethod.invoke(globalClassSetItems);
    }
}
