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

    Jvm(OperatingSystem os) {
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

    public boolean isJava5Compatible() {
        return System.getProperty("java.version").startsWith("1.5") || isJava6Compatible();
    }

    public boolean isJava6Compatible() {
        return System.getProperty("java.version").startsWith("1.6");
    }

    public boolean isAppleJvm() {
        return false;
    }

    public File getJavaHome() {
        File toolsJar = getToolsJar();
        return toolsJar == null ? new File(System.getProperty("java.home")) : toolsJar.getParentFile().getParentFile();
    }

    public File getBinDir() {
        return new File(getJavaHome(), "bin");
    }

    public File getToolsJar() {
        File javaHome = new File(System.getProperty("java.home"));
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

    static class AppleJvm extends Jvm {
        AppleJvm(OperatingSystem os) {
            super(os);
        }

        @Override
        public boolean isAppleJvm() {
            return true;
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
