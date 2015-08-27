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
package org.gradle.api.internal.project;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.classpath.ClassPath;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Iterator;
import java.util.Vector;

class GroovyRuntimeMemoryLeakStrategy implements MemoryLeakPrevention.Strategy {

    private final static Logger LOG = Logging.getLogger(GroovyRuntimeMemoryLeakStrategy.class);
    private final static Field CLASSES_FIELD;

    static {
        Field classesField;
        try {
            classesField = ClassLoader.class.getDeclaredField("classes");
            classesField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            classesField = null;
        }
        CLASSES_FIELD = classesField;
    }

    @Override
    public boolean appliesTo(ClassPath classpath) {
        for (URI uri : classpath.getAsURIs()) {
            if (uri.getPath().contains("groovy")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void cleanup(ClassLoader classLoader) throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Applying Groovy runtime memory leak prevention strategy");
        }
        Class classInfoClass = classLoader.loadClass("org.codehaus.groovy.reflection.ClassInfo");
        Field globalClassValueField = classInfoClass.getDeclaredField("globalClassValue");
        globalClassValueField.setAccessible(true);
        final Object globalClassValue = globalClassValueField.get(null);
        final Method removeFromGlobalClassValue = globalClassValueField.getType().getDeclaredMethod("remove", Class.class);
        removeFromGlobalClassValue.setAccessible(true);

        Field globalClassSetField = classInfoClass.getDeclaredField("globalClassSet");
        globalClassSetField.setAccessible(true);
        Object globalClassSet = globalClassSetField.get(null);
        globalClassSetField = globalClassSet.getClass().getDeclaredField("items");
        globalClassSetField.setAccessible(true);
        Object globalClassSetItems = globalClassSetField.get(globalClassSet);

        Field clazzField = classInfoClass.getDeclaredField("klazz");
        clazzField.setAccessible(true);


        Iterator it = (Iterator) globalClassSetItems.getClass().getDeclaredMethod("iterator").invoke(globalClassSetItems);

        while (it.hasNext()) {
            Object classInfo = it.next();
            Object clazz = clazzField.get(classInfo);
            removeFromGlobalClassValue.invoke(globalClassValue, clazz);
        }

        removeClassInfoFromClassValue(classLoader, globalClassValue, removeFromGlobalClassValue);
    }

    @SuppressWarnings("unchecked")
    private void removeClassInfoFromClassValue(ClassLoader classLoader, Object globalClassValue, Method removeFromGlobalClassValue) throws IllegalAccessException, InvocationTargetException {
        Vector<Class> classes = (Vector<Class>) CLASSES_FIELD.get(classLoader);
        if (classes != null) {
            for (Class clazz : classes) {
                removeFromGlobalClassValue.invoke(globalClassValue, clazz);
            }
        }
        ClassLoader parent = classLoader.getParent();
        if (parent != null) {
            removeClassInfoFromClassValue(parent, globalClassValue, removeFromGlobalClassValue);
        }
    }
}
