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
import org.gradle.internal.jvm.JavaInfo;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.jvm.inspection.JvmVersionDetector;
import org.gradle.process.internal.ExecActionFactory;

import java.io.File;

public class ForkedJavaToolChain extends AbstractJavaToolChain {
    private final JavaInfo javaInfo;
    private final JavaVersion javaVersion;

    public ForkedJavaToolChain(File javaHome, JavaCompilerFactory compilerFactory, ExecActionFactory execActionFactory, JvmVersionDetector jvmVersionDetector) {
        super(compilerFactory, execActionFactory);
        this.javaInfo = Jvm.forHome(javaHome);
        this.javaVersion = jvmVersionDetector.getJavaVersion(javaInfo);
    }

    @Override
    public JavaVersion getJavaVersion() {
        return javaVersion;
    }
}
