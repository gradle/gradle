/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.tasks.compile;

import org.gradle.internal.Factory;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.jvm.Jvm;

import javax.tools.JavaCompiler;
import java.io.File;
import java.io.Serializable;

public class JavaHomeBasedJavaCompilerFactory implements Factory<JavaCompiler>, Serializable {
    private final Factory<? extends File> currentJvmJavaHomeFactory;
    private final Factory<? extends File> systemPropertiesJavaHomeFactory;
    private final Factory<? extends JavaCompiler> systemJavaCompilerFactory;

    public JavaHomeBasedJavaCompilerFactory() {
        this(new CurrentJvmJavaHomeFactory(), new SystemPropertiesJavaHomeFactory(), new SystemJavaCompilerFactory());
    }

    JavaHomeBasedJavaCompilerFactory(Factory<? extends File> currentJvmJavaHomeFactory, Factory<? extends File> systemPropertiesJavaHomeFactory, Factory<? extends JavaCompiler> systemJavaCompilerFactory) {
        this.currentJvmJavaHomeFactory = currentJvmJavaHomeFactory;
        this.systemPropertiesJavaHomeFactory = systemPropertiesJavaHomeFactory;
        this.systemJavaCompilerFactory = systemJavaCompilerFactory;
    }

    @Override
    public JavaCompiler create() {
        JavaCompiler compiler = findCompiler();

        if (compiler == null) {
            throw new RuntimeException("Cannot find System Java Compiler. Ensure that you have installed a JDK (not just a JRE) and configured your JAVA_HOME system variable to point to the according directory.");
        }

        return compiler;
    }

    private JavaCompiler findCompiler() {
        File realJavaHome = currentJvmJavaHomeFactory.create();
        File javaHomeFromToolProvidersPointOfView = systemPropertiesJavaHomeFactory.create();
        if (realJavaHome.equals(javaHomeFromToolProvidersPointOfView)) {
            return systemJavaCompilerFactory.create();
        }

        return SystemProperties.getInstance().withJavaHome(realJavaHome, systemJavaCompilerFactory);
    }

    public static class CurrentJvmJavaHomeFactory implements Factory<File>, Serializable {
        @Override
        public File create() {
            return Jvm.current().getJavaHome();
        }
    }

    public static class SystemPropertiesJavaHomeFactory implements Factory<File>, Serializable {
        @Override
        public File create() {
            return SystemProperties.getInstance().getJavaHomeDir();
        }
    }

    public static class SystemJavaCompilerFactory implements Factory<JavaCompiler>, Serializable {
        @Override
        public JavaCompiler create() {
            return JdkTools.current().getSystemJavaCompiler();
        }
    }
}
