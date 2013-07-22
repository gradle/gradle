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

import org.gradle.api.GradleException;
import org.gradle.internal.UncheckedException;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

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
        new ClassLoaderBackedClasspathSource(classLoader).collectClasspath(implementationClassPath);
        return implementationClassPath;
    }

    public static File getClasspathForClass(Class<?> targetClass) {
        URI location;
        try {
            location = targetClass.getProtectionDomain().getCodeSource().getLocation().toURI();
        } catch (URISyntaxException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        if (!location.getScheme().equals("file")) {
            throw new GradleException(String.format("Cannot determine classpath for %s from codebase '%s'.", targetClass.getName(), location));
        }
        return new File(location.getPath());
    }

    public static File getClasspathForResource(ClassLoader classLoader, String name) {
        if (classLoader == null) {
            return getClasspathForResource(ClassLoader.getSystemResource(name), name);
        } else {
            return getClasspathForResource(classLoader.getResource(name), name);
        }
    }

    public static File getClasspathForResource(URL resource, String name) {
        URI location;
        try {
            location = resource.toURI();
            String path = location.getPath();
            if (location.getScheme().equals("file")) {
                assert path.endsWith("/" + name);
                return new File(path.substring(0, path.length() - (name.length() + 1)));
            } else if (location.getScheme().equals("jar")) {
                String schemeSpecificPart = location.getRawSchemeSpecificPart();
                int pos = schemeSpecificPart.indexOf("!");
                if (pos > 0) {
                    assert schemeSpecificPart.substring(pos + 1).equals("/" + name);
                    URI jarFile = new URI(schemeSpecificPart.substring(0, pos));
                    if (jarFile.getScheme().equals("file")) {
                        return new File(jarFile.getPath());
                    }
                }
            }
        } catch (URISyntaxException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        throw new GradleException(String.format("Cannot determine classpath for resource '%s' from location '%s'.", name, location));
    }
}
