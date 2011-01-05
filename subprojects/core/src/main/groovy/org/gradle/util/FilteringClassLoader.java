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

import java.io.IOException;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;

/**
 * A ClassLoader which hides all non-system classes, packages and resources. Allows certain non-system packages and
 * classes to be declared as visible. By default, only the Java system classes, packages and resources are visible.
 */
public class FilteringClassLoader extends ClassLoader {
    private static final Set<ClassLoader> SYSTEM_CLASS_LOADERS = new HashSet<ClassLoader>();
    private static final ClassLoader EXT_CLASS_LOADER;
    private static final Set<Package> SYSTEM_PACKAGES = new HashSet<Package>();
    private final Set<String> packageNames = new HashSet<String>();
    private final Set<String> packagePrefixes = new HashSet<String>();
    private final Set<String> resourcePrefixes = new HashSet<String>();
    private final Set<String> classNames = new HashSet<String>();

    static {
        EXT_CLASS_LOADER = ClassLoader.getSystemClassLoader().getParent();
        for (ClassLoader cl = EXT_CLASS_LOADER; cl != null; cl = cl.getParent()) {
            SYSTEM_CLASS_LOADERS.add(cl);
        }
        JavaMethod<ClassLoader, Package[]> method = new JavaMethod<ClassLoader, Package[]>(ClassLoader.class,
                Package[].class, "getPackages");
        SYSTEM_PACKAGES.addAll(Arrays.asList((Package[]) method.invoke(EXT_CLASS_LOADER)));
    }

    public FilteringClassLoader(ClassLoader parent) {
        super(parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> cl;
        try {
            cl = super.loadClass(name, false);
        } catch (NoClassDefFoundError e) {
            if (classAllowed(name)) {
                throw e;
            }
            // The class isn't visible
            throw new ClassNotFoundException(String.format("%s not found.", name));
        }

        if (!allowed(cl)) {
            throw new ClassNotFoundException(String.format("%s not found.", cl.getName()));
        }
        if (resolve) {
            resolveClass(cl);
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
        return EXT_CLASS_LOADER.getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        if (allowed(name)) {
            return super.getResources(name);
        }
        return EXT_CLASS_LOADER.getResources(name);
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
        if (SYSTEM_PACKAGES.contains(p)) {
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

    private boolean allowed(final Class<?> cl) {
        boolean systemClass = AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
            public Boolean run() {
                return cl.getClassLoader() == null || SYSTEM_CLASS_LOADERS.contains(cl.getClassLoader());
            }
        });
        return systemClass || classAllowed(cl.getName());
    }

    private boolean classAllowed(String className) {
        if (classNames.contains(className)) {
            return true;
        }
        for (String packagePrefix : packagePrefixes) {
            if (className.startsWith(packagePrefix)) {
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

    /**
     * Marks a single class as visible
     *
     * @param aClass The class
     */
    public void allowClass(Class<?> aClass) {
        classNames.add(aClass.getName());
    }
}
