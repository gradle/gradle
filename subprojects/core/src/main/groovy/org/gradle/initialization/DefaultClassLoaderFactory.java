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
import org.gradle.util.ClasspathUtil;
import org.gradle.util.GFileUtils;
import org.gradle.util.Jvm;
import org.gradle.util.MultiParentClassLoader;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;

public class DefaultClassLoaderFactory implements ClassLoaderFactory {
    private final URLClassLoader rootClassLoader;

    public DefaultClassLoaderFactory(ClassPathRegistry classPathRegistry) {
        // Add in tools.jar to the Ant classloader
        ClassLoader antClassloader = Project.class.getClassLoader();
        File toolsJar = Jvm.current().getToolsJar();
        if (toolsJar != null) {
            ClasspathUtil.addUrl((URLClassLoader) antClassloader, GFileUtils.toURLs(Collections.singleton(toolsJar)));
        }

        // Add in libs for plugins
        ClassLoader runtimeClassloader = getClass().getClassLoader();
        URL[] classPath = classPathRegistry.getClassPathUrls("GRADLE_PLUGINS");
        rootClassLoader = new URLClassLoader(classPath, runtimeClassloader);
    }

    public ClassLoader getRootClassLoader() {
        return rootClassLoader;
    }

    public MultiParentClassLoader createScriptClassLoader() {
        return new MultiParentClassLoader(rootClassLoader);
    }
}
