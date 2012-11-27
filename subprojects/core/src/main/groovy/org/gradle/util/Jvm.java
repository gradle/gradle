/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.internal.jvm.JavaHomeException;
import org.gradle.internal.jvm.JavaInfo;

import java.io.File;
import java.util.Map;

@Deprecated
public class Jvm implements JavaInfo {
    private final org.gradle.internal.jvm.Jvm jvm;

    public Jvm(org.gradle.internal.jvm.Jvm current) {
        this.jvm = current;
    }

    public static Jvm current() {
        DeprecationLogger.nagUserOfDeprecated("The class org.gradle.util.Jvm");
        return new Jvm(org.gradle.internal.jvm.Jvm.current());
    }

    public File getJavaExecutable() throws JavaHomeException {
        return jvm.getJavaExecutable();
    }

    public File getJavadocExecutable() throws JavaHomeException {
        return jvm.getJavadocExecutable();
    }

    public File getExecutable(String name) throws JavaHomeException {
        return jvm.getExecutable(name);
    }

    public boolean isJava5() {
        return jvm.getJavaVersion().isJava5();
    }

    public boolean isJava6() {
        return jvm.getJavaVersion().isJava6();
    }

    public boolean isJava7() {
        return jvm.getJavaVersion().isJava7();
    }

    public boolean isJava5Compatible() {
        return jvm.getJavaVersion().isJava5Compatible();
    }

    public boolean isJava6Compatible() {
        return jvm.getJavaVersion().isJava6Compatible();
    }

    public File getJavaHome() {
        return jvm.getJavaHome();
    }

    public File getRuntimeJar() {
        return jvm.getRuntimeJar();
    }

    public File getToolsJar() {
        return jvm.getToolsJar();
    }

    public Map<String, ?> getInheritableEnvironmentVariables(Map<String, ?> envVars) {
        return jvm.getInheritableEnvironmentVariables(envVars);
    }

    public boolean isIbmJvm() {
        return jvm.isIbmJvm();
    }
    
    public String toString(){
        return jvm.toString();
    }
}
