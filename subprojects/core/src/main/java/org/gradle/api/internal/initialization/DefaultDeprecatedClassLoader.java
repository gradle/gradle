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

import org.gradle.internal.classloader.ClassLoaderVisitor;
import org.gradle.internal.classloader.DeprecatedClassloader;
import org.gradle.util.DeprecationLogger;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;

public class DefaultDeprecatedClassLoader extends ClassLoader implements DeprecatedClassloader {

    private static final String BUILDSRC_IN_SETTINGS_DEPRECATION_WARNING = "Access to the buildSrc project and its dependencies in settings scripts";

    private final ClassLoader deprecatedUsageLoader;
    private final ClassLoader nonDeprecatedParent;

    boolean deprecationFired;

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
        URL resource;
        if (!deprecationFired) {
            resource = nonDeprecatedParent.getResource(name);
            if (resource != null) {
                return resource;
            }
        }

        resource = deprecatedUsageLoader.getResource(name);
        if (resource != null) {
            maybeEmitDeprecationWarning();
        }
        return resource;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!deprecationFired) {
            try {
                return nonDeprecatedParent.loadClass(name);
            } catch (ClassNotFoundException e) {
                // Expected
            }
        }
        try {
            Class<?> deprecatedUsageClass = deprecatedUsageLoader.loadClass(name);
            maybeEmitDeprecationWarning();
            return deprecatedUsageClass;
        } catch (ClassNotFoundException e) {
            // Expected
        }
        throw new ClassNotFoundException(String.format("%s not found.", name));
    }

    private void maybeEmitDeprecationWarning() {
        if (!deprecationFired) {
            DeprecationLogger.nagUserOfDeprecated(BUILDSRC_IN_SETTINGS_DEPRECATION_WARNING);
            deprecationFired = true;
        }
    }

    @Override
    public void visit(ClassLoaderVisitor visitor) {
        visitor.visit(deprecatedUsageLoader);
        visitor.visit(nonDeprecatedParent);
    }

    @Override
    public void close() throws IOException {
        if (deprecatedUsageLoader instanceof Closeable) {
            ((Closeable) deprecatedUsageLoader).close();
        }

        // not sure if this is required as its the parent of
        // deprecatedUsageLoader already
        if (nonDeprecatedParent instanceof Closeable) {
            ((Closeable) nonDeprecatedParent).close();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultDeprecatedClassLoader that = (DefaultDeprecatedClassLoader) o;

        if (deprecationFired != that.deprecationFired) {
            return false;
        }
        if (!deprecatedUsageLoader.equals(that.deprecatedUsageLoader)) {
            return false;
        }
        return nonDeprecatedParent.equals(that.nonDeprecatedParent);
    }

    @Override
    public int hashCode() {
        int result = deprecatedUsageLoader.hashCode();
        result = 31 * result + nonDeprecatedParent.hashCode();
        return result;
    }
}

