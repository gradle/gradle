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
package org.gradle.launcher;

import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.DefaultClassPathRegistry;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

public class ProcessBootstrap {
    void run(String mainClassName, String[] args) {
        try {
            runNoExit(mainClassName, args);
            System.exit(0);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            System.exit(1);
        }
    }

    private void runNoExit(String mainClassName, String[] args) throws Exception {
        ClassPathRegistry classPathRegistry = new DefaultClassPathRegistry();
        URL[] antClasspath = classPathRegistry.getClassPathUrls("ANT");
        URL[] runtimeClasspath = classPathRegistry.getClassPathUrls("GRADLE_RUNTIME");
        ClassLoader rootClassLoader = ClassLoader.getSystemClassLoader().getParent();
        URLClassLoader antClassLoader = new URLClassLoader(antClasspath, rootClassLoader);
        URLClassLoader runtimeClassLoader = new URLClassLoader(runtimeClasspath, antClassLoader);
        Thread.currentThread().setContextClassLoader(runtimeClassLoader);
        Class mainClass = runtimeClassLoader.loadClass(mainClassName);
        Method mainMethod = mainClass.getMethod("main", String[].class);
        mainMethod.invoke(null, new Object[]{args});
    }
}
