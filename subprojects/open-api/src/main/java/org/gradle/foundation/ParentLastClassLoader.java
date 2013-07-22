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
package org.gradle.foundation;

import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLStreamHandlerFactory;

/**
 * <p>This class loader delegates to the parent class loader ONLY if it cannot find it itself. This is meant to solve classloading issues when running something as, say, a plugin inside an application
 * that may have already loaded a different version of some required jars. This makes sure it looks locally first. This is the opposite of a ClassLoader's typical behavior, but it necessary when you
 * can't control the environment in which you're running.
 *
 * <p>Using this class can be very dangerous. You must carefully make sure you understand the ramifications of using this. You should also probably make this the first class loader between your plugin
 * and the plugin's owner.
 * @deprecated No replacement
 */
@Deprecated
public class ParentLastClassLoader extends URLClassLoader {
    public ParentLastClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    public ParentLastClassLoader(URL[] urls) {
        super(urls);
    }

    public ParentLastClassLoader(URL[] urls, ClassLoader parent, URLStreamHandlerFactory factory) {
        super(urls, parent, factory);
    }

    /*
    This has been overridden to look at the parent class loader last.
    */
    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        // First check whether it's already been loaded, if so use it
        Class loadedClass = findLoadedClass(name);

        // Not loaded, try to load it
        if (loadedClass == null) {
            try {
                // Ignore parent delegation and just try to load locally
                loadedClass = findClass(name);
            } catch (ClassNotFoundException e) {
                // Swallow exception - does not exist locally
            }

            // If not found locally, use normal parent delegation in URLClassloader
            if (loadedClass == null) {
                // throws ClassNotFoundException if not found in delegation hierarchy at all
                loadedClass = super.loadClass(name);
            }
        }
        // will never return null (ClassNotFoundException will be thrown)
        return loadedClass;
    }
}

