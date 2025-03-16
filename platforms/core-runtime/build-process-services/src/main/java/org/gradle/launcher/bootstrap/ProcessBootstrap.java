/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.launcher.bootstrap;

import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.DefaultClassPathProvider;
import org.gradle.api.internal.DefaultClassPathRegistry;
import org.gradle.api.internal.classpath.DefaultModuleRegistry;
import org.gradle.internal.classloader.ClassLoaderFactory;
import org.gradle.internal.classloader.ClassLoaderUtils;
import org.gradle.internal.classloader.DefaultClassLoaderFactory;
import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.installation.CurrentGradleInstallation;

import java.lang.reflect.Method;

public class ProcessBootstrap {
    /**
     * Sets up the ClassLoader structure for the given class, creates an instance and invokes {@link EntryPoint#run(String[])} on it.
     *
     * @param moduleName the name of the Gradle module to use for the main class implementation
     */
    public static void run(String bootstrapName, String moduleName, String mainClassName, String[] args) {
        try {
            runNoExit(bootstrapName, moduleName, mainClassName, args);
            System.exit(0);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            System.exit(1);
        }
    }

    private static void runNoExit(String bootstrapName, String moduleName, String mainClassName, String[] args) throws Exception {
        ClassLoader runtimeClassLoader;
        ClassLoader antClassLoader;

        try {
            DefaultModuleRegistry moduleRegistry = new DefaultModuleRegistry(CurrentGradleInstallation.get());
            ClassPathRegistry classPathRegistry = new DefaultClassPathRegistry(new DefaultClassPathProvider(moduleRegistry));
            ClassLoaderFactory classLoaderFactory = new DefaultClassLoaderFactory();
            ClassPath antClasspath = classPathRegistry.getClassPath("ANT");
            ClassPath runtimeClasspath = moduleRegistry.getModule(moduleName).getAllRequiredModulesClasspath();
            antClassLoader = classLoaderFactory.createIsolatedClassLoader("ant-loader", antClasspath);
            runtimeClassLoader = VisitableURLClassLoader.fromClassPath("ant-and-gradle-loader", antClassLoader, runtimeClasspath);
        } catch (NoClassDefFoundError e) {
            throw new RuntimeException(
                "Failed to bootstrap Gradle. Check MANIFEST.MF 'Class-Path' of the entry-point " +
                    "'" + bootstrapName + "' and ensure there are no missing dependencies for the manifest classpath", e);
        }

        ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(runtimeClassLoader);

        try {
            Class<?> mainClass = runtimeClassLoader.loadClass(mainClassName);
            Object entryPoint = mainClass.getConstructor().newInstance();
            Method mainMethod = mainClass.getMethod("run", String[].class);
            mainMethod.invoke(entryPoint, new Object[]{args});
        } finally {
            Thread.currentThread().setContextClassLoader(oldClassLoader);

            ClassLoaderUtils.tryClose(runtimeClassLoader);
            ClassLoaderUtils.tryClose(antClassLoader);
        }
    }
}
