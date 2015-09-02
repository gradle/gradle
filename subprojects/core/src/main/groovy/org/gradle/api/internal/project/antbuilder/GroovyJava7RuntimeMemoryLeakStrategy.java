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
package org.gradle.api.internal.project.antbuilder;

import org.codehaus.groovy.reflection.ClassInfo;
import org.gradle.internal.classloader.MultiParentClassLoader;
import org.gradle.internal.classpath.ClassPath;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Iterator;
import java.util.List;

public class GroovyJava7RuntimeMemoryLeakStrategy extends MemoryLeakPrevention.Strategy {

    private final static boolean HAS_CLASS_VALUE;

    static {
        boolean cv = true;
        try {
            Class.forName("java.lang.ClassValue");
        } catch (ClassNotFoundException e) {
            cv = false;
        }
        HAS_CLASS_VALUE = cv;
    }

    private Class<?> classInfoClass;
    private Method removeFromGlobalClassValue;
    private Method globalClassSetIteratorMethod;
    private Object globalClassValue;
    private Object globalClassSetItems;
    private Field clazzField;
    private ClassLoader gradleClassInfoClassLoader;

    @Override
    public boolean appliesTo(ClassPath classpath) {
        if (!HAS_CLASS_VALUE) {
            return false;
        }
        if (classpath == null) {
            return true;
        }
        for (URI uri : classpath.getAsURIs()) {
            if (uri.getPath().contains("groovy")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void prepare(ClassLoader leakingLoader, ClassLoader... affectedLoaders) throws Exception {
        // this work has to be done before classes are loaded, otherwise there are risks that
        // the PermGen space is full before we create the reflection methods

        classInfoClass = leakingLoader.loadClass("org.codehaus.groovy.reflection.ClassInfo");
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

        clazzField = classInfoClass.getDeclaredField("klazz");
        clazzField.setAccessible(true);

        gradleClassInfoClassLoader = ClassInfo.class.getClassLoader();
    }

    @Override
    public void dispose(ClassLoader classLoader, ClassLoader... affectedLoaders) throws Exception {

        Iterator it = globalClassSetIterator();

        while (it.hasNext()) {
            Object classInfo = it.next();
            if (classInfo != null) {
                Class clazz = (Class) clazzField.get(classInfo);
                if (inHierarchy(clazz, affectedLoaders)) {
                    removeFromGlobalClassValue.invoke(globalClassValue, clazz);
                }
            }
        }

    }

    private Iterator globalClassSetIterator() throws IllegalAccessException, InvocationTargetException {
        return (Iterator) globalClassSetIteratorMethod.invoke(globalClassSetItems);
    }

    private boolean inHierarchy(Class clazz, ClassLoader... loaders) {
        if (loaders == null) {
            return false;
        }
        for (ClassLoader loader : loaders) {
            if (inHierarchy(clazz, loader)) {
                return true;
            }
        }
        return false;
    }

    private boolean inHierarchy(Class clazz, ClassLoader loader) {
        ClassLoader classLoader = clazz.getClassLoader();
        if (loader == null) {
            return false;
        }
        if (classLoader == null || classLoader==gradleClassInfoClassLoader) {
            // "please don't clean any classinfo from Gradle core, for classes which have been loaded by Gradle itself"
            // system class loader, purged only if not Gradle core
            return ClassInfo.class != classInfoClass;
        }
        if (isLoadedInSameHierarchy(loader, classLoader)) {
            return true;
        }
        if (loader instanceof MultiParentClassLoader) {
            List<ClassLoader> parents = ((MultiParentClassLoader) loader).getParents();
            for (ClassLoader parent : parents) {
                if (inHierarchy(clazz, parent)) {
                    return true;
                }
            }
        }
        return inHierarchy(clazz, loader.getParent());
    }

    private boolean isLoadedInSameHierarchy(ClassLoader loader, ClassLoader ld) {
        while (ld != null) {
            if (ld == loader) {
                return true;
            }
            if (ld instanceof MultiParentClassLoader) {
                for (ClassLoader classLoader : ((MultiParentClassLoader) ld).getParents()) {
                    if (isLoadedInSameHierarchy(loader, classLoader)) {
                        return true;
                    }
                }
            }
            ld = ld.getParent();
        }
        return false;
    }

}
