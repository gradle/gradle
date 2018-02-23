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

package org.gradle.internal.classloader;

import org.gradle.api.GradleException;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.reflect.JavaMethod;
import org.gradle.internal.reflect.JavaReflectionUtil;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClasspathUtil {
    public static void addUrl(URLClassLoader classLoader, Iterable<URL> classpathElements) {
        try {
            Set<URI> original = new HashSet<URI>();
            for (URL url : classLoader.getURLs()) {
                original.add(toURI(url));
            }
            JavaMethod<URLClassLoader, Object> method = JavaReflectionUtil.method(URLClassLoader.class, Object.class, "addURL", URL.class);
            for (URL classpathElement : classpathElements) {
                if (original.add(toURI(classpathElement))) {
                    method.invoke(classLoader, classpathElement);
                }
            }
        } catch (Throwable t) {
            throw new RuntimeException(String.format("Could not add URLs %s to class path for ClassLoader %s", classpathElements, classLoader), t);
        }
    }

    public static ClassPath getClasspath(ClassLoader classLoader) {
        final List<File> implementationClassPath = new ArrayList<File>();
        new ClassLoaderVisitor() {
            @Override
            public void visitClassPath(URL[] classPath) {
                for (URL url : classPath) {
                    if (url.getProtocol() != null && url.getProtocol().equals("file")) {
                        try {
                            implementationClassPath.add(new File(toURI(url)));
                        } catch (URISyntaxException e) {
                            throw new UncheckedException(e);
                        }
                    }
                }
            }
        }.visit(classLoader);
        return DefaultClassPath.of(implementationClassPath);
    }

    public static File getClasspathForClass(String targetClassName) {
        try {
            Class clazz = Class.forName(targetClassName);
            if (clazz.getClassLoader() == null) {
                return null;
            } else {
                return getClasspathForClass(Class.forName(targetClassName));
            }
        } catch (ClassNotFoundException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public static File getClasspathForClass(Class<?> targetClass) {
        URI location;
        try {
            CodeSource codeSource = targetClass.getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                location = toURI(codeSource.getLocation());
                if (location.getScheme().equals("file")) {
                    return new File(location);
                }
            }
            if (targetClass.getClassLoader() != null) {
                String resourceName = targetClass.getName().replace('.', '/') + ".class";
                URL resource = targetClass.getClassLoader().getResource(resourceName);
                if (resource != null) {
                    return getClasspathForResource(resource, resourceName);
                }
            }
            throw new GradleException(String.format("Cannot determine classpath for class %s.", targetClass.getName()));
        } catch (URISyntaxException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
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
            location = toURI(resource);
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

    private static URI toURI(URL url) throws URISyntaxException {
        try {
            return url.toURI();
        } catch (URISyntaxException e) {
            try {
                return new URL(url.getProtocol(),
                               url.getHost(),
                               url.getPort(),
                               url.getFile().replace(" ", "%20")).toURI();
            } catch (MalformedURLException e1) {
                throw new UncheckedException(e1);
            }
        }
    }
}
