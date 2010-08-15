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

package org.gradle.util;

import org.apache.tools.ant.util.JavaEnvUtils;

import java.io.File;

public class Jvm {
    public static Jvm current() {
        return new Jvm();
    }

    Jvm() {
    }

    @Override
    public String toString() {
        return String.format("%s (%s %s)", System.getProperty("java.version"), System.getProperty("java.vm.vendor"), System.getProperty("java.vm.version"));
    }

    public File getJavaExecutable() {
        return new File(JavaEnvUtils.getJdkExecutable("java"));
    }

    public File getJavadocExecutable() {
        return new File(JavaEnvUtils.getJdkExecutable("javadoc"));
    }

    public boolean isJava5Compatible() {
        return System.getProperty("java.version").startsWith("1.5") || isJava6Compatible();
    }

    public boolean isJava6Compatible() {
        return System.getProperty("java.version").startsWith("1.6");
    }

    public File getToolsJar() {
        File javaHome = new File(System.getProperty("java.home"));
        File toolsJar = new File(javaHome, "/lib/tools.jar");
        if (toolsJar.exists()) {
            return toolsJar;
        }
        if (javaHome.getName().equalsIgnoreCase("jre")) {
            javaHome = javaHome.getParentFile();
            toolsJar = new File(javaHome + "/lib/tools.jar");
        }
        if (!toolsJar.exists()) {
            return null;
        }
        return toolsJar;
    }

}
