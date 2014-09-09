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
import org.gradle.internal.classloader.*;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.jvm.Jvm;

import java.io.File;
import java.net.URLClassLoader;

public class DefaultClassLoaderRegistry implements ClassLoaderRegistry, JdkToolsInitializer {
    private final ClassLoader apiOnlyClassLoader;
    private final ClassLoader apiAndPluginsClassLoader;
    private final ClassLoader extensionsClassLoader;

    public DefaultClassLoaderRegistry(ClassPathRegistry classPathRegistry, ClassLoaderFactory classLoaderFactory) {
        ClassLoader runtimeClassLoader = getClass().getClassLoader();

        apiOnlyClassLoader = restrictToGradleApi(classLoaderFactory, runtimeClassLoader);

        ClassPath pluginsClassPath = classPathRegistry.getClassPath("GRADLE_EXTENSIONS");
        extensionsClassLoader = new MutableURLClassLoader(runtimeClassLoader, pluginsClassPath);

        this.apiAndPluginsClassLoader = restrictToGradleApi(classLoaderFactory, extensionsClassLoader);
    }

    private CachingClassLoader restrictToGradleApi(ClassLoaderFactory classLoaderFactory, ClassLoader classLoader) {
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

    public void initializeJdkTools() {
        // Add in tools.jar to the systemClassloader parent
        File toolsJar = Jvm.current().getToolsJar();
        if (toolsJar != null) {
            final ClassLoader systemClassLoaderParent = ClassLoader.getSystemClassLoader().getParent();
            ClasspathUtil.addUrl((URLClassLoader) systemClassLoaderParent, new DefaultClassPath(toolsJar).getAsURLs());
        }
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
