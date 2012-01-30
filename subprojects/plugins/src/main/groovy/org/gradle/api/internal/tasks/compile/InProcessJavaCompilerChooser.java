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

import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.util.ReflectionUtil;

public class InProcessJavaCompilerChooser implements JavaCompilerChooser {
    private static final boolean JDK_6_COMPILER_AVAILABLE =
            ReflectionUtil.isClassAvailable("javax.tools.ToolProvider")
                    && ReflectionUtil.isClassAvailable("org.gradle.api.internal.tasks.compile.jdk6.Jdk6JavaCompiler");
    private static final boolean SUN_COMPILER_AVAILABLE = ReflectionUtil.isClassAvailable("com.sun.tools.javac.Main");
    
    public JavaCompiler choose(CompileOptions options) {
        if (JDK_6_COMPILER_AVAILABLE) {
            return CompilerFactory.createJdk6Compiler();
        }
        if (SUN_COMPILER_AVAILABLE) {
            return CompilerFactory.createSunCompiler();
        }
        return CompilerFactory.createAntCompiler();
    }
    
    private static class CompilerFactory {
        static JavaCompiler createJdk6Compiler() {
            return null;
        }
        
        static JavaCompiler createSunCompiler() {
            return new SunJavaCompiler();
        }
        
        static JavaCompiler createAntCompiler() {
            return null;
        }
    }
}
