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

import org.gradle.internal.classpath.ClassPath;

import java.beans.Introspector;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

class JavaBeanIntrospectorMemoryLeakStrategy implements MemoryLeakPrevention.Strategy {
    private final static Field DECLARED_METHOD_CACHE_FIELD;
    private final static Method GET_CONTEXT_METHOD;
    private final static Method CLEAR_BEANINFO_METHOD;

    static {
        Field declaredMethodCache = null;
        Method getContextMethod = null;
        Method clearBeanInfoCacheMethod = null;
        try {
            declaredMethodCache = Introspector.class.getDeclaredField("declaredMethodCache");
            declaredMethodCache.setAccessible(true);
            Class<?> threadGroupContextClass = Class.forName("java.beans.ThreadGroupContext");
            getContextMethod = threadGroupContextClass.getDeclaredMethod("getContext");
            getContextMethod.setAccessible(true);
            clearBeanInfoCacheMethod = threadGroupContextClass.getDeclaredMethod("clearBeanInfoCache");
            clearBeanInfoCacheMethod.setAccessible(true);
        } catch (NoSuchFieldException e) {
            declaredMethodCache = null;
        } catch (ClassNotFoundException e) {
            getContextMethod = null;
            clearBeanInfoCacheMethod = null;
        } catch (NoSuchMethodException e) {
            getContextMethod = null;
            clearBeanInfoCacheMethod = null;
        }
        DECLARED_METHOD_CACHE_FIELD = declaredMethodCache;
        GET_CONTEXT_METHOD = getContextMethod;
        CLEAR_BEANINFO_METHOD = clearBeanInfoCacheMethod;
    }
    @Override
    public boolean appliesTo(ClassPath classpath) {
        return DECLARED_METHOD_CACHE_FIELD!=null && GET_CONTEXT_METHOD!=null;
    }

    @Override
    public void cleanup(ClassLoader classLoader) throws Exception {
        Object cache = DECLARED_METHOD_CACHE_FIELD.get(null);
        cache.getClass().getDeclaredMethod("clear").invoke(cache);
        CLEAR_BEANINFO_METHOD.invoke(GET_CONTEXT_METHOD.invoke(null));
    }
}
