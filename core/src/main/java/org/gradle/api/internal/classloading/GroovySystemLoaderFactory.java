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
import org.gradle.util.internal.VersionNumber;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class GroovySystemLoaderFactory {
    private static final NoOpGroovySystemLoader NO_OP = new NoOpGroovySystemLoader();

    public GroovySystemLoader forClassLoader(ClassLoader classLoader) {
        try {
            Class<?> groovySystem = getGroovySystem(classLoader);
            if (groovySystem == null || groovySystem.getClassLoader() != classLoader) {
                return NO_OP;
            }
            GroovySystemLoader classInfoCleaningLoader = createClassInfoCleaningLoader(groovySystem, classLoader);
            GroovySystemLoader preferenceCleaningLoader = new PreferenceCleaningGroovySystemLoader(classLoader);
            return new CompositeGroovySystemLoader(classInfoCleaningLoader, preferenceCleaningLoader);
        } catch (Exception e) {
            throw new GradleException("Could not inspect the Groovy system for ClassLoader " + classLoader, e);
        }
    }

    private Class<?> getGroovySystem(ClassLoader classLoader) {
        try {
            return classLoader.loadClass("groovy.lang.GroovySystem");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private GroovySystemLoader createClassInfoCleaningLoader(Class<?> groovySystem, ClassLoader classLoader) throws Exception {
        VersionNumber groovyVersion = getGroovyVersion(groovySystem);
        return isGroovy24OrLater(groovyVersion) ? new ClassInfoCleaningGroovySystemLoader(classLoader) : NO_OP;
    }

    private VersionNumber getGroovyVersion(Class<?> groovySystem) throws IllegalAccessException, InvocationTargetException {
        try {
            Method getVersion = groovySystem.getDeclaredMethod("getVersion");
            String versionString = (String) getVersion.invoke(null);
            return VersionNumber.parse(versionString);
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    private boolean isGroovy24OrLater(VersionNumber groovyVersion) {
        if (groovyVersion == null) {
            return false;
        }
        return groovyVersion.getMajor() == 2 && groovyVersion.getMinor() >= 4 || groovyVersion.getMajor() > 2;
    }
}
