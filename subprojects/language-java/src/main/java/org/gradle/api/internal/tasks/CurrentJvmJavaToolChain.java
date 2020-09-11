/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.jvm.toolchain.internal.JavaCompilerFactory;
import org.gradle.process.internal.ExecActionFactory;

/**
 * A Java toolchain which uses the JVM executing Gradle itself to locate tools.
 *
 * The {@link #getJavaVersion()} is the version of the current JVM.
 * It supports compiling in the same JVM and in a forked JVM with the same version.
 *
 * @see JavaHomeBasedJavaToolChain
 */
public class CurrentJvmJavaToolChain extends AbstractJavaToolChain {
    private JavaVersion javaVersion;

    public CurrentJvmJavaToolChain(JavaCompilerFactory compilerFactory, ExecActionFactory execActionFactory) {
        super(compilerFactory, execActionFactory);
        this.javaVersion = JavaVersion.current();
    }

    @Override
    public JavaVersion getJavaVersion() {
        return javaVersion;
    }
}
