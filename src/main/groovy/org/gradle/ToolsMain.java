/*
 * Copyright 2007 the original author or authors.
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

package org.gradle;

import org.apache.tools.ant.launch.Locator;
import org.gradle.util.GradleUtil;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Steven Devijver
 */
public class ToolsMain {
    // As we don't want to start logging before logging is configured, we use this variable for messages from ToolsMain
    static List toolsMainInfo = new ArrayList();

    public static void main(String[] args) throws Exception {
        boolean modernCompilerFound = false;

        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        try {
            classLoader.loadClass("com.sun.tools.javac.Main");
            modernCompilerFound = true;
            toolsMainInfo.add("Modern compiler found.");
        } catch (ClassNotFoundException e) {
            toolsMainInfo.add("No modern compiler.");
        }

        if (!modernCompilerFound) {
            ClassLoader _cl = classLoader;
            while (_cl.getParent() != null) {
                _cl = _cl.getParent();
            }
            File toolsJar = Locator.getToolsJar();
            List jars = new ArrayList();

            File[] files = GradleUtil.getGradleClasspath();
            for (int i = 0; i < files.length; i++) {
                jars.add(Locator.fileToURL(files[i]));
            }
            jars.add(Locator.fileToURL(toolsJar));
            ClassLoader contextClassLoader = new URLClassLoader((URL[]) jars.toArray(new URL[jars.size()]), _cl);
            contextClassLoader.loadClass("com.sun.tools.javac.Main");
            classLoader = contextClassLoader;
            Thread.currentThread().setContextClassLoader(contextClassLoader);

        }

        Class mainClass = classLoader.loadClass("org.gradle.Main");
        Method mainMethod = mainClass.getMethod("main", new Class[]{String[].class});
        mainMethod.invoke(null, new Object[]{args});

    }
}