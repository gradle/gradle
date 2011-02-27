/*
 * Copyright 2010 the original author or authors.
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

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Hans Dockter
 */
public class ClasspathUtil {
    public static void addUrl(URLClassLoader classLoader, Iterable<URL> classpathElements) {
        try {
            Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
            for (URL classpathElement : classpathElements) {
                method.invoke(classLoader, classpathElement);
            }
        } catch (Throwable t) {
            throw new RuntimeException("Error, could not add URL to classloader", t);
        }
    }

    public static List<URL> getClasspath(ClassLoader classLoader) {
        List<URL> implementationClassPath = new ArrayList<URL>();
        ClassLoader stopAt = ClassLoader.getSystemClassLoader() == null ? null : ClassLoader.getSystemClassLoader().getParent();
        for (ClassLoader cl = classLoader; cl != null && cl != stopAt; cl = cl.getParent()) {
            if (cl instanceof URLClassLoader) {
                implementationClassPath.addAll(Arrays.asList(((URLClassLoader) cl).getURLs()));
            }
        }
        return implementationClassPath;
    }
}
