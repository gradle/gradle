/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.JavaVersion;
import org.gradle.internal.classloader.ClassLoaderFactory;
import org.gradle.internal.classloader.DefaultClassLoaderFactory;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.jvm.JavaInfo;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.reflect.DirectInstantiator;

import javax.tools.JavaCompiler;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.ClassLoader.getSystemClassLoader;

/**
 * Subset replacement for {@link javax.tools.ToolProvider} that avoids the application class loader.
 */
public class JdkTools {

    // Copied from ToolProvider.defaultJavaCompilerName
    private static final String DEFAULT_COMPILER_IMPL_NAME = "com.sun.tools.javac.api.JavacTool";
    private static final AtomicReference<JdkTools> INSTANCE = new AtomicReference<JdkTools>();

    private final ClassLoader isolatedToolsLoader;
    private final boolean isJava9Compatible;

    public static JdkTools current() {
        JdkTools jdkTools = INSTANCE.get();
        if (jdkTools == null) {
            INSTANCE.compareAndSet(null, new JdkTools(Jvm.current()));
            jdkTools = INSTANCE.get();
        }
        return jdkTools;
    }

    JdkTools(JavaInfo javaInfo) {
        DefaultClassLoaderFactory defaultClassLoaderFactory = new DefaultClassLoaderFactory();
        JavaVersion javaVersion = Jvm.current().getJavaVersion();
        boolean java9Compatible = javaVersion.isJava9Compatible();
        ClassLoader filteringClassLoader = getSystemFilteringClassLoader(defaultClassLoaderFactory, java9Compatible);
        if (!java9Compatible) {
            File toolsJar = javaInfo.getToolsJar();
            if (toolsJar == null) {
                throw new IllegalStateException("Could not find tools.jar. Please check that "
                                                + javaInfo.getJavaHome().getAbsolutePath()
                                                + " contains a valid JDK installation.");
            }
            DefaultClassPath defaultClassPath = new DefaultClassPath(toolsJar);
            isolatedToolsLoader = new VisitableURLClassLoader(filteringClassLoader, defaultClassPath.getAsURLs());
            isJava9Compatible = false;
        } else {
            isolatedToolsLoader = filteringClassLoader;
            isJava9Compatible = true;
        }
    }

    private ClassLoader getSystemFilteringClassLoader(ClassLoaderFactory classLoaderFactory, boolean java9compatible) {
        FilteringClassLoader.Spec filterSpec = new FilteringClassLoader.Spec();
        if (java9compatible) {
            filterSpec.allowPackage("com.sun.tools");
        }
        return classLoaderFactory.createFilteringClassLoader(getSystemClassLoader(), filterSpec);
    }

    public JavaCompiler getSystemJavaCompiler() {
        Class<?> clazz;
        try {
            if (isJava9Compatible) {
                clazz = isolatedToolsLoader.loadClass("javax.tools.ToolProvider");
                try {
                    return (JavaCompiler) clazz.getDeclaredMethod("getSystemJavaCompiler").invoke(null);
                } catch (IllegalAccessException e) {
                    cannotCreateJavaCompiler(e);
                } catch (InvocationTargetException e) {
                    cannotCreateJavaCompiler(e);
                } catch (NoSuchMethodException e) {
                    cannotCreateJavaCompiler(e);
                }
            } else {
                clazz = isolatedToolsLoader.loadClass(DEFAULT_COMPILER_IMPL_NAME);
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Could not load class '" + DEFAULT_COMPILER_IMPL_NAME);
        }
        return DirectInstantiator.instantiate(clazz.asSubclass(JavaCompiler.class));
    }

    private void cannotCreateJavaCompiler(Exception e) {
        throw new IllegalStateException("Could not create system Java compiler", e);
    }
}
