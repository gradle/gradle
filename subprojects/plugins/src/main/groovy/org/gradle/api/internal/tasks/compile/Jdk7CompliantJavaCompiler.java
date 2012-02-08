/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.tasks.WorkResult;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

public class Jdk7CompliantJavaCompiler extends JavaCompilerSupport {
    JavaCompiler compilerDelegate;
    private String defaultBootClasspath;

    public Jdk7CompliantJavaCompiler(JavaCompiler delegate) {
        this.compilerDelegate = delegate;
        final RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        if(runtimeMXBean.isBootClassPathSupported()){
            this.defaultBootClasspath = runtimeMXBean.getBootClassPath();
        }
    }

    public WorkResult execute() {
        augmentBoostrapClasspath();
        configure(compilerDelegate);
        return compilerDelegate.execute();
    }

    private void augmentBoostrapClasspath() {
        if (!jdk7SourceCompatibility()){
            if (bootstrapClasspathNotSet()){
                compileOptions.setBootClasspath(defaultBootClasspath);
            }
        }
    }

    private boolean bootstrapClasspathNotSet() {
        return compileOptions.getBootClasspath() == null || compileOptions.getBootClasspath().equals("");
    }

    private boolean jdk7SourceCompatibility() {
        return sourceCompatibility == null || (sourceCompatibility.equals("7") || sourceCompatibility.equals("1.7"));
    }
}
