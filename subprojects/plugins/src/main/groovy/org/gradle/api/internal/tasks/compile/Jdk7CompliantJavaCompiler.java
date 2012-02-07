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
import org.gradle.util.Jvm;

public class Jdk7CompliantJavaCompiler extends JavaCompilerSupport {
    private JavaCompiler compilerDelegate;
    private Jvm jvm;

    public Jdk7CompliantJavaCompiler(JavaCompiler delegate) {
        this(delegate, Jvm.current());
    }

    Jdk7CompliantJavaCompiler(JavaCompiler delegate, Jvm jvm) {
        this.compilerDelegate = delegate;
        this.jvm = jvm;
    }

    public WorkResult execute() {
        augmentBoostrapClasspath();
        configure(compilerDelegate);
        return compilerDelegate.execute();
    }

    private void augmentBoostrapClasspath() {
        if (!jdk7SourceCompatibility()){
            if (bootstrapClasspathNotSet()){
                String rtPath = jvm.getRuntimeJar().getAbsolutePath();
                compileOptions.setBootClasspath(rtPath);
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
