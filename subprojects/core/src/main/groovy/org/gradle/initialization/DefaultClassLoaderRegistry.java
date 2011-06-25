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

import org.apache.tools.ant.Project;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.util.*;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;

public class DefaultClassLoaderRegistry implements ClassLoaderRegistry {
    private final FilteringClassLoader rootClassLoader;
    private final ClassLoader coreImplClassLoader;
    private final ClassLoader pluginsClassLoader;

    public DefaultClassLoaderRegistry(ClassPathRegistry classPathRegistry, ClassLoaderFactory classLoaderFactory) {
        // Add in tools.jar to the Ant classloader
        ClassLoader antClassLoader = Project.class.getClassLoader();
        File toolsJar = Jvm.current().getToolsJar();
        if (toolsJar != null) {
            ClasspathUtil.addUrl((URLClassLoader) antClassLoader, GFileUtils.toURLs(Collections.singleton(toolsJar)));
        }

        ClassLoader runtimeClassLoader = getClass().getClassLoader();

        // Core impl
        URL[] coreImplClassPath = classPathRegistry.getClassPathUrls("GRADLE_CORE_IMPL");
        coreImplClassLoader = new URLClassLoader(coreImplClassPath, runtimeClassLoader);
        FilteringClassLoader coreImplExports = classLoaderFactory.createFilteringClassLoader(coreImplClassLoader);
        coreImplExports.allowPackage("org.gradle");

        // Add in libs for plugins
        URL[] pluginsClassPath = classPathRegistry.getClassPathUrls("GRADLE_PLUGINS");
        MultiParentClassLoader pluginsImports = new MultiParentClassLoader(runtimeClassLoader, coreImplExports);
        pluginsClassLoader = new URLClassLoader(pluginsClassPath, pluginsImports);

        rootClassLoader = classLoaderFactory.createFilteringClassLoader(pluginsClassLoader);
        rootClassLoader.allowPackage("org.gradle");
        rootClassLoader.allowResources("META-INF/gradle-plugins");
        rootClassLoader.allowPackage("org.apache.tools.ant");
        rootClassLoader.allowPackage("groovy");
        rootClassLoader.allowPackage("org.codehaus.groovy");
        rootClassLoader.allowPackage("groovyjarjarantlr");
        rootClassLoader.allowPackage("org.apache.ivy");
        rootClassLoader.allowPackage("org.slf4j");
        rootClassLoader.allowPackage("org.apache.commons.logging");
        rootClassLoader.allowPackage("org.apache.log4j");
    }

    public ClassLoader getRootClassLoader() {
        return rootClassLoader;
    }

    public ClassLoader getCoreImplClassLoader() {
        return coreImplClassLoader;
    }

    public ClassLoader getPluginsClassLoader() {
        return pluginsClassLoader;
    }

    public MultiParentClassLoader createScriptClassLoader() {
        return new MultiParentClassLoader(rootClassLoader);
    }
}
