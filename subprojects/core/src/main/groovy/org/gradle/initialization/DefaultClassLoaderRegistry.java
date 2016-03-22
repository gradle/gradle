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

package org.gradle.initialization;

import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.internal.classloader.CachingClassLoader;
import org.gradle.internal.classloader.ClassLoaderFactory;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classloader.MutableURLClassLoader;
import org.gradle.internal.classpath.ClassPath;

public class DefaultClassLoaderRegistry implements ClassLoaderRegistry {
    private final ClassLoader apiOnlyClassLoader;
    private final ClassLoader apiAndPluginsClassLoader;
    private final ClassLoader extensionsClassLoader;
    private final ClassLoaderFactory classLoaderFactory;

    public DefaultClassLoaderRegistry(ClassPathRegistry classPathRegistry, ClassLoaderFactory classLoaderFactory) {
        this.classLoaderFactory = classLoaderFactory;
        ClassLoader runtimeClassLoader = getClass().getClassLoader();

        apiOnlyClassLoader = restrictToGradleApi(runtimeClassLoader);

        ClassPath pluginsClassPath = classPathRegistry.getClassPath("GRADLE_EXTENSIONS");
        extensionsClassLoader = new MutableURLClassLoader(runtimeClassLoader, pluginsClassPath);

        this.apiAndPluginsClassLoader = restrictToGradleApi(extensionsClassLoader);
    }

    private ClassLoader restrictToGradleApi(ClassLoader classLoader) {
        FilteringClassLoader rootClassLoader = classLoaderFactory.createFilteringClassLoader(classLoader);
        rootClassLoader.allowPackage("org.gradle");
        rootClassLoader.allowResources("META-INF/gradle-plugins");
        rootClassLoader.allowPackage("org.apache.tools.ant");
        rootClassLoader.allowPackage("groovy");
        rootClassLoader.allowPackage("org.codehaus.groovy");
        rootClassLoader.allowPackage("groovyjarjarantlr");
        rootClassLoader.allowPackage("org.slf4j");
        rootClassLoader.allowPackage("org.apache.commons.logging");
        rootClassLoader.allowPackage("org.apache.log4j");
        rootClassLoader.allowPackage("javax.inject");
        return new CachingClassLoader(rootClassLoader);
    }

    public ClassLoader getRuntimeClassLoader() {
        return getClass().getClassLoader();
    }

    public ClassLoader getGradleApiClassLoader() {
        return apiAndPluginsClassLoader;
    }

    public ClassLoader getPluginsClassLoader() {
        return extensionsClassLoader;
    }

    public ClassLoader getGradleCoreApiClassLoader() {
        return apiOnlyClassLoader;
    }
}
