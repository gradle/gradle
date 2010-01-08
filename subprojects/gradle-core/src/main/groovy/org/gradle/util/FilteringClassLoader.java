/*
 * Copyright 2009 the original author or authors.
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

import java.io.IOException;
import java.net.URL;
import java.util.*;

/**
 * A ClassLoader which hides all non-system classes, packages and resources from the parent ClassLoader. Allows certain
 * non-system packages to be declared as visible. By default, only the Java system classes, packages and resources are
 * visible.
 */
public class FilteringClassLoader extends ClassLoader {
    private final Set<String> packageNames = new HashSet<String>();
    private final Set<String> packagePrefixes = new HashSet<String>();
    private final Set<String> resourcePrefixes = new HashSet<String>();
    private final Set<ClassLoader> systemClassLoaders = new HashSet<ClassLoader>();
    private final Set<Package> systemPackages = new HashSet<Package>();
    private final ClassLoader extClassLoader;

    public FilteringClassLoader(ClassLoader classLoader) {
        super(classLoader);
        extClassLoader = ClassLoader.getSystemClassLoader().getParent();
        for (ClassLoader cl = extClassLoader; cl != null; cl = cl.getParent()) {
            systemClassLoaders.add(cl);
        }
        systemPackages.addAll(Arrays.asList((Package[]) ReflectionUtil.invoke(extClassLoader, "getPackages", new Object[0])));
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> cl = super.loadClass(name, resolve);
        if (!allowed(cl)) {
            throw new ClassNotFoundException(String.format("%s not found.", cl.getName()));
        }
        return cl;
    }

    @Override
    protected Package getPackage(String name) {
        Package p = super.getPackage(name);
        if (p == null || !allowed(p)) {
            return null;
        }
        return p;
    }

    @Override
    protected Package[] getPackages() {
        List<Package> packages = new ArrayList<Package>();
        for (Package p : super.getPackages()) {
            if (allowed(p)) {
                packages.add(p);
            }
        }
        return packages.toArray(new Package[packages.size()]);
    }

    @Override
    public URL getResource(String name) {
        if (allowed(name)) {
            return super.getResource(name);
        }
        return extClassLoader.getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        if (allowed(name)) {
            return super.getResources(name);
        }
        return extClassLoader.getResources(name);
    }

    private boolean allowed(String resourceName) {
        for (String resourcePrefix : resourcePrefixes) {
            if (resourceName.startsWith(resourcePrefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean allowed(Package p) {
        if (systemPackages.contains(p)) {
            return true;
        }
        for (String packageName : packageNames) {
            if (p.getName().equals(packageName)) {
                return true;
            }
        }
        for (String packagePrefix : packagePrefixes) {
            if (p.getName().startsWith(packagePrefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean allowed(Class<?> cl) {
        if (cl.getClassLoader() == null || systemClassLoaders.contains(cl)) {
            return true;
        }
        for (String packagePrefix : packagePrefixes) {
            if (cl.getName().startsWith(packagePrefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Marks a package and all its sub-packages as visible.
     *
     * @param packageName The package name
     */
    public void allowPackage(String packageName) {
        packageNames.add(packageName);
        packagePrefixes.add(packageName + ".");
        resourcePrefixes.add(packageName.replace('.', '/') + '/');
    }
}
