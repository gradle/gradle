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
import java.util.HashMap;
import java.util.Map;

public class Jvm {
    private final OperatingSystem os;

    public static Jvm current() {
        String vendor = System.getProperty("java.vm.vendor");
        if (vendor.toLowerCase().startsWith("apple inc.")) {
            return new AppleJvm(OperatingSystem.current());
        }
        return new Jvm(OperatingSystem.current());
    }

    public Jvm(OperatingSystem os) {
        this.os = os;
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

    public File getJpsExecutable() {
        return new File(JavaEnvUtils.getJdkExecutable("jps"));
    }

    public boolean isJava5Compatible() {
        return System.getProperty("java.version").startsWith("1.5") || isJava6Compatible();
    }

    public boolean isJava6Compatible() {
        return System.getProperty("java.version").startsWith("1.6");
    }

    public File getJavaHome() {
        File toolsJar = getToolsJar();
        return toolsJar == null ? getDefaultJavaHome() : toolsJar.getParentFile().getParentFile();
    }

    private File getDefaultJavaHome() {
        return GFileUtils.canonicalise(new File(System.getProperty("java.home")));
    }

    public File getRuntimeJar() {
        File javaHome = getDefaultJavaHome();
        File runtimeJar = new File(javaHome, "lib/rt.jar");
        return runtimeJar.exists() ? runtimeJar : null;
    }

    public File getToolsJar() {
        File javaHome = getDefaultJavaHome();
        File toolsJar = new File(javaHome, "lib/tools.jar");
        if (toolsJar.exists()) {
            return toolsJar;
        }
        if (javaHome.getName().equalsIgnoreCase("jre")) {
            javaHome = javaHome.getParentFile();
            toolsJar = new File(javaHome, "lib/tools.jar");
            if (toolsJar.exists()) {
                return toolsJar;
            }
        }
        if (javaHome.getName().matches("jre\\d+") && os.isWindows()) {
            javaHome = new File(javaHome.getParentFile(), String.format("jdk%s", System.getProperty("java.version")));
            toolsJar = new File(javaHome, "lib/tools.jar");
            if (toolsJar.exists()) {
                return toolsJar;
            }
        }

        return null;
    }

    public Map<String, ?> getInheritableEnvironmentVariables(Map<String, ?> envVars) {
        return envVars;
    }

    /**
     * Note: Implementation assumes that an Apple JVM always comes with a JDK rather than a JRE,
     * but this is likely an over-simplification.
     */
    public static class AppleJvm extends Jvm {
        public AppleJvm(OperatingSystem os) {
            super(os);
        }

        @Override
        public File getJavaHome() {
            return super.getDefaultJavaHome();
        }

        @Override
        public File getRuntimeJar() {
            File javaHome = super.getDefaultJavaHome();
            File runtimeJar = new File(javaHome.getParentFile(), "Classes/classes.jar");
            return runtimeJar.exists() ? runtimeJar : null;
        }

        @Override
        public File getToolsJar() {
            return getRuntimeJar();
        }

        @Override
        public Map<String, ?> getInheritableEnvironmentVariables(Map<String, ?> envVars) {
            Map<String, Object> vars = new HashMap<String, Object>();
            for (Map.Entry<String, ?> entry : envVars.entrySet()) {
                if (entry.getKey().matches("APP_NAME_\\d+") || entry.getKey().matches("JAVA_MAIN_CLASS_\\d+")) {
                    continue;
                }
                vars.put(entry.getKey(), entry.getValue());
            }
            return vars;
        }
    }
}
