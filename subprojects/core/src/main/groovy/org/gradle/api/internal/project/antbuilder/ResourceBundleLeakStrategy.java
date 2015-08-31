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

import org.gradle.internal.classpath.ClassPath;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.ResourceBundle;

class ResourceBundleLeakStrategy extends MemoryLeakPrevention.Strategy {
    private final static Field RESOURCE_BUNDLE_CACHE_FIELD;
    private final static ResourceBundle NONEXISTENT_RB;
    private final static Field RESOURCE_BUNDLE_NONEXISTENT_CACHEKEY;
    private final static Method CLEAR_METHOD;

    static {
        Field f = null;
        Field f2 = null;
        Method m = null;
        ResourceBundle bundle = null;
        try {
            f = ResourceBundle.class.getDeclaredField("cacheList");
            f.setAccessible(true);
            f2 = ResourceBundle.class.getDeclaredField("NONEXISTENT_BUNDLE");
            f2.setAccessible(true);
            bundle = (ResourceBundle) f2.get(null);
            f2 = ResourceBundle.class.getDeclaredField("cacheKey");
            f2.setAccessible(true);
            m = Map.class.getDeclaredMethod("clear");
            m.setAccessible(true);
        } catch (NoSuchFieldException e) {
            f = null;
        } catch (NoSuchMethodException e) {
            m = null;
        } catch (IllegalAccessException e) {
            bundle = null;
        }
        RESOURCE_BUNDLE_CACHE_FIELD = f;
        RESOURCE_BUNDLE_NONEXISTENT_CACHEKEY = f2;
        CLEAR_METHOD = m;
        NONEXISTENT_RB = bundle;
    }

    @Override
    public boolean appliesTo(ClassPath classpath) {
        return RESOURCE_BUNDLE_CACHE_FIELD != null && CLEAR_METHOD != null;
    }

    @Override
    public void dispose(ClassLoader classLoader, ClassLoader... affectedLoaders) throws Exception {
        clearCaches();
    }

    @Override
    void afterUse(ClassLoader leakingLoader, ClassLoader... affectedLoaders) throws Exception {
        clearCaches();
    }

    private void clearCaches() throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        Object cache = RESOURCE_BUNDLE_CACHE_FIELD.get(null);
        cache.getClass().getDeclaredMethod("clear").invoke(cache);
        RESOURCE_BUNDLE_NONEXISTENT_CACHEKEY.set(NONEXISTENT_RB, null);
    }
}
