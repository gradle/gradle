/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.classpath;

import org.gradle.internal.classloader.DefaultClassLoaderFactory;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.util.internal.GUtil;
import org.jspecify.annotations.NullMarked;

import java.net.URL;
import java.util.Properties;

/**
 * Provides access to information about the Gradle distribution, loaded from the
 * {@code gradle-runtime-api-info} module.
 */
@NullMarked
@ServiceScope(Scope.Global.class)
public class RuntimeApiInfo {

    private final ClassLoader classLoader;

    public static RuntimeApiInfo create(ClassPath apiInfoClasspath) {
        ClassLoader classLoader = new DefaultClassLoaderFactory().createIsolatedClassLoader(
            "runtime-api-info",
            apiInfoClasspath
        );
        return new RuntimeApiInfo(classLoader);
    }

    /**
     * Load the runtime info directly from the classpath -- useful in unit tests
     * when a complete distribution is not available.
     */
    public RuntimeApiInfo(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public Properties getGradlePluginsProperties() {
        return GUtil.loadProperties(getResource("gradle-plugins.properties"));
    }

    public Properties getGradleImplementationPluginsProperties() {
        return GUtil.loadProperties(getResource("gradle-implementation-plugins.properties"));
    }

    public URL getDefaultImportsResource() {
        return getResource("default-imports.txt");
    }

    public URL getApiMappingResource() {
        return getResource("api-mapping.txt");
    }

    private URL getResource(String resource) {
        URL url = classLoader.getResource(resource);
        if (url == null) {
            throw new IllegalStateException("Cannot find resource '" + resource + "' in classloader " + classLoader);
        }
        return url;
    }

}
