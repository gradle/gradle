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
import org.gradle.internal.jvm.Jvm;

import javax.tools.JavaCompiler;
import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JavaHomeBasedJavaCompilerFactory implements Factory<JavaCompiler>, Serializable {
    private final List<File> compilerPluginsClasspath;
    // We use a static cache here because we want to reuse classloaders in compiler workers as
    // it has a huge impact on performance. Previously there was a single, JdkTools.current()
    // instance, but we can have different "compiler plugins" classpath. For this reason we use
    // a map, but in practice it's likely there's only one instance in this map.
    private final static transient Map<List<File>, JdkTools> JDK_TOOLS = new ConcurrentHashMap<>();

    public JavaHomeBasedJavaCompilerFactory(List<File> compilerPluginsClasspath) {
        this.compilerPluginsClasspath = compilerPluginsClasspath;
    }

    protected List<File> getCompilerPluginsClasspath() {
        return compilerPluginsClasspath;
    }

    @Override
    public JavaCompiler create() {
        JdkTools jdkTools = JavaHomeBasedJavaCompilerFactory.JDK_TOOLS.computeIfAbsent(compilerPluginsClasspath, JavaHomeBasedJavaCompilerFactory::createJdkTools);
        return jdkTools.getSystemJavaCompiler(compilerPluginsClasspath);
    }

    private static JdkTools createJdkTools(List<File> compilerPluginsClasspath) {
        return new JdkTools(Jvm.current(), compilerPluginsClasspath);
    }
}
