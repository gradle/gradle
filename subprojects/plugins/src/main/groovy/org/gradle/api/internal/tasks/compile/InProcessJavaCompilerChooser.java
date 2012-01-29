/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.AntBuilder;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.internal.Factory;
import org.gradle.util.Jvm;
import org.gradle.util.ReflectionUtil;

public class InProcessJavaCompilerChooser implements JavaCompilerChooser {
    private static final boolean SUN_COMPILER_AVAILABLE = ReflectionUtil.isClassAvailable("com.sun.tools.javac.Main");

    private final CompilerFactory compilerFactory;

    public InProcessJavaCompilerChooser(Factory<AntBuilder> antBuilderFactory) {
        compilerFactory = new CompilerFactory(antBuilderFactory);
    }

    public JavaCompiler choose(CompileOptions options) {
        if (Jvm.current().isJava6Compatible()) {
            return compilerFactory.createJdk6Compiler();
        }
        if (SUN_COMPILER_AVAILABLE) {
            return compilerFactory.createSunCompiler();
        }
        return compilerFactory.createAntCompiler();
    }

    // static to enforce lazy class loading
    private static class CompilerFactory {
        final Factory<AntBuilder> antBuilderFactory;

        CompilerFactory(Factory<AntBuilder> antBuilderFactory) {
            this.antBuilderFactory = antBuilderFactory;
        }

        JavaCompiler createJdk6Compiler() {
            try {
                // excluded when Gradle is compiled against JDK5, hence we can't reference it statically
                Class<?> clazz = getClass().getClassLoader().loadClass("org.gradle.api.internal.tasks.compile.jdk6.Jdk6JavaCompiler");
                return (JavaCompiler) clazz.newInstance();
            } catch (Exception e) {
                throw new GradleException("Internal error: couldn't load or instantiate class Jdk6JavaCompiler", e);
            }
        }
        
        JavaCompiler createSunCompiler() {
            return new SunJavaCompiler();
        }
        
        JavaCompiler createAntCompiler() {
            return new AntJavaCompiler(antBuilderFactory);
        }
    }
}
