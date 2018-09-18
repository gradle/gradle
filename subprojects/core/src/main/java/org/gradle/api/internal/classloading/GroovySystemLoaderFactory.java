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
import org.gradle.util.VersionNumber;

import java.lang.reflect.Method;

public class GroovySystemLoaderFactory {
    public static final NoOpGroovySystemLoader NOT_BROKEN = new NoOpGroovySystemLoader();

    public GroovySystemLoader forClassLoader(ClassLoader classLoader) {
        try {
            Class<?> groovySystem;
            try {
                groovySystem = classLoader.loadClass("groovy.lang.GroovySystem");
            } catch (ClassNotFoundException e) {
                // Not a Groovy implementation, or not an implementation that we need to deal with
                return NOT_BROKEN;
            }
            if (groovySystem.getClassLoader() != classLoader) {
                // Groovy implementation visible from somewhere else
                return NOT_BROKEN;
            }

            String versionString;
            try {
                Method getVersion = groovySystem.getDeclaredMethod("getVersion");
                versionString = (String) getVersion.invoke(null);
            } catch (NoSuchMethodException ex) {
                return NOT_BROKEN;
            }
            VersionNumber groovyVersion = VersionNumber.parse(versionString);
            boolean isFaultyGroovy = groovyVersion.getMajor() == 2 && groovyVersion.getMinor() == 4;
            return isFaultyGroovy ? new LeakyOnJava7GroovySystemLoader(classLoader) : NOT_BROKEN;
        } catch (Exception e) {
            throw new GradleException("Could not inspect the Groovy system for ClassLoader " + classLoader, e);
        }
    }
}
