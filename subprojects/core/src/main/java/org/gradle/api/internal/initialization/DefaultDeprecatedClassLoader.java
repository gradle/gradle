/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.initialization;

import org.gradle.internal.classloader.DeprecatedClassloader;
import org.gradle.util.DeprecationLogger;

import java.net.URL;

public class DefaultDeprecatedClassLoader extends ClassLoader implements DeprecatedClassloader {

    private final ClassLoader deprecatedUsageLoader;
    private final ClassLoader nonDeprecatedParent;

    public DefaultDeprecatedClassLoader(ClassLoader deprecatedUsageLoader, ClassLoader nonDeprecatedParent) {
        super(null);
        this.deprecatedUsageLoader = deprecatedUsageLoader;
        this.nonDeprecatedParent = nonDeprecatedParent;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return loadClass(name, false);
    }

    @Override
    public URL getResource(String name) {
        URL resource = nonDeprecatedParent.getResource(name);
        if (resource != null) {
            return resource;
        }
        // avoid duplicate deprecation when loading class as resource first by our url classloaders
        resource = deprecatedUsageLoader.getResource(name);
        if (resource != null && !name.endsWith(".class")) {
            DeprecationLogger.nagUserOfDeprecated("Using buildSrc resources in settings", "Do not use '" + name + "' in settings.");
        }
        return resource;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        try {
            return nonDeprecatedParent.loadClass(name);
        } catch (ClassNotFoundException e) {
            // Expected
        }

        try {
            Class<?> deprecatedUsageClass = deprecatedUsageLoader.loadClass(name);
            DeprecationLogger.nagUserOfDeprecated("Using buildSrc classes in settings", "Do not use '" + name + "' in settings.");
            return deprecatedUsageClass;
        } catch (ClassNotFoundException e) {
            // Expected
        }

        throw new ClassNotFoundException(String.format("%s not found.", name));
    }
}

