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

package org.gradle.internal.jvm;

import org.gradle.internal.classloader.DefaultClassLoaderFactory;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.reflect.DirectInstantiator;

import javax.tools.JavaCompiler;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Subset replacement for {@link javax.tools.ToolProvider} that avoids the application class loader.
 */
public class JdkTools {

    // Copied from ToolProvider.defaultJavaCompilerName
    private static final String DEFAULT_COMPILER_IMPL_NAME = "com.sun.tools.javac.api.JavacTool";
    private static final AtomicReference<JdkTools> INSTANCE = new AtomicReference<JdkTools>();

    private final ClassLoader isolatedToolsLoader;

    public static JdkTools current() {
        JdkTools jdkTools = INSTANCE.get();
        if (jdkTools == null) {
            INSTANCE.compareAndSet(null, new JdkTools(Jvm.current()));
            jdkTools = INSTANCE.get();
        }
        return jdkTools;
    }

    JdkTools(JavaInfo javaInfo) {
        File toolsJar = javaInfo.getToolsJar();
        if (toolsJar == null) {
            throw new IllegalStateException("Could not find tools.jar");
        }

        isolatedToolsLoader = new DefaultClassLoaderFactory().createIsolatedClassLoader(new DefaultClassPath(toolsJar));
    }

    public JavaCompiler getSystemJavaCompiler() {
        Class<?> compilerImplClass;
        try {
            compilerImplClass = isolatedToolsLoader.loadClass(DEFAULT_COMPILER_IMPL_NAME);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Could not load class '" + DEFAULT_COMPILER_IMPL_NAME + "' from " + Jvm.current().getToolsJar());
        }

        return DirectInstantiator.instantiate(compilerImplClass.asSubclass(JavaCompiler.class));
    }

}
