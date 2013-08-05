/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.tasks.compile;

import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.internal.reflect.JavaReflectionUtil;

public class InProcessJavaCompilerFactory implements JavaCompilerFactory {
    private static final boolean SUN_COMPILER_AVAILABLE = JavaReflectionUtil.isClassAvailable("com.sun.tools.javac.Main");

    public Compiler<JavaCompileSpec> create(CompileOptions options) {
        if (JavaVersion.current().isJava6Compatible()) {
            return createJdk6Compiler();
        }
        if (SUN_COMPILER_AVAILABLE) {
            return new SunCompilerFactory().create();
        }
        throw new RuntimeException("Cannot find a Java compiler API. Please let us know which JDK/platform you are using. To work around this problem, try 'compileJava.options.useAnt=true'.");
    }

    private Compiler<JavaCompileSpec> createJdk6Compiler() {
        try {
            // excluded when Gradle is compiled against JDK5, hence we can't reference it statically
            Class<?> clazz = getClass().getClassLoader().loadClass("org.gradle.api.internal.tasks.compile.jdk6.Jdk6JavaCompiler");
            return (Compiler<JavaCompileSpec>) clazz.newInstance();
        } catch (Exception e) {
            throw new GradleException("Internal error: couldn't load or instantiate class Jdk6JavaCompiler", e);
        }
    }

    // nested class to enforce lazy class loading
    private static class SunCompilerFactory {
        Compiler<JavaCompileSpec> create() {
            return new SunJavaCompiler();
        }
    }
}
