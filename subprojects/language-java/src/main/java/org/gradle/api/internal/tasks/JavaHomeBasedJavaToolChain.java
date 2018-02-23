/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks;

import org.gradle.api.JavaVersion;
import org.gradle.api.internal.tasks.compile.JavaCompilerFactory;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.jvm.inspection.JvmVersionDetector;
import org.gradle.process.internal.ExecActionFactory;

import java.io.File;

/**
 * A Java toolchain which uses the given Java home to locate the corresponding tools.
 *
 * The {@link #getJavaVersion()} is determined by examining the Java home.
 * It supports compiling by forking the executable for the tool.
 *
 * @see CurrentJvmJavaToolChain
 */
public class JavaHomeBasedJavaToolChain extends AbstractJavaToolChain {
    private final JavaVersion javaVersion;

    public JavaHomeBasedJavaToolChain(File javaHome, JavaCompilerFactory compilerFactory, ExecActionFactory execActionFactory, JvmVersionDetector jvmVersionDetector) {
        super(compilerFactory, execActionFactory);
        this.javaVersion = jvmVersionDetector.getJavaVersion(Jvm.forHome(javaHome));
    }

    @Override
    public JavaVersion getJavaVersion() {
        return javaVersion;
    }
}
