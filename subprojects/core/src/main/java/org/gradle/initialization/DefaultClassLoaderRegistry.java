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
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classloader.HashingClassLoaderFactory;
import org.gradle.internal.classloader.HashingClassLoaderFactory.CustomClassLoaderFactory;
import org.gradle.internal.classpath.ClassPath;

public class DefaultClassLoaderRegistry implements ClassLoaderRegistry {
    private final ClassLoader apiOnlyClassLoader;
    private final ClassLoader apiAndPluginsClassLoader;
    private final ClassLoader extensionsClassLoader;
    private final HashingClassLoaderFactory classLoaderFactory;

    public DefaultClassLoaderRegistry(ClassPathRegistry classPathRegistry, HashingClassLoaderFactory classLoaderFactory) {
        this.classLoaderFactory = classLoaderFactory;
        ClassLoader runtimeClassLoader = getClass().getClassLoader();

        apiOnlyClassLoader = restrictToGradleApi(runtimeClassLoader);

        ClassPath pluginsClassPath = classPathRegistry.getClassPath("GRADLE_EXTENSIONS");
        extensionsClassLoader = classLoaderFactory.createCustomClassLoader(runtimeClassLoader, pluginsClassPath, new CustomClassLoaderFactory() {
            @Override
            public ClassLoader create(ClassLoader parent, ClassPath classPath) {
                return new MixInLegacyTypesClassLoader(parent, classPath);
            }
        });

        this.apiAndPluginsClassLoader = restrictToGradleApi(extensionsClassLoader);
    }

    private ClassLoader restrictToGradleApi(ClassLoader classLoader) {
        FilteringClassLoader.Spec rootSpec = new FilteringClassLoader.Spec();
        rootSpec.allowPackage("org.gradle");
        rootSpec.allowResources("META-INF/gradle-plugins");
        rootSpec.allowPackage("org.apache.tools.ant");
        rootSpec.allowPackage("groovy");
        rootSpec.allowPackage("org.codehaus.groovy");
        rootSpec.allowPackage("groovyjarjarantlr");
        rootSpec.allowPackage("org.slf4j");
        rootSpec.allowPackage("org.apache.commons.logging");
        rootSpec.allowPackage("org.apache.log4j");
        rootSpec.allowPackage("javax.inject");
        ClassLoader rootClassLoader = classLoaderFactory.createFilteringClassLoader(classLoader, rootSpec);
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
