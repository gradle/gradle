/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.wrapper;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * @author Hans Dockter
 */
public class BootstrapMainStarter {
    public void start(String[] args, String gradleHome, String version) throws Exception {
        System.setProperty("gradle.home", gradleHome);
        boolean debug = WrapperMain.isDebug();
        File gradleJar = new File(gradleHome, "lib/gradle-" + version + ".jar");
        if (debug) {
            System.out.println("gradleJar = " + gradleJar.getAbsolutePath());
        }
        URLClassLoader contextClassLoader = new URLClassLoader(new URL[] { gradleJar.toURI().toURL() });
        Thread.currentThread().setContextClassLoader(contextClassLoader);
        Class mainClass = contextClassLoader.loadClass("org.gradle.BootstrapMain");
        Method mainMethod = mainClass.getMethod("main", String[].class);
        mainMethod.invoke(null, new Object[] {args});
    }
}
