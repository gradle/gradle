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
package org.gradle.jvm.toolchain.internal;

import org.gradle.internal.os.OperatingSystem;
import org.gradle.model.Managed;

import java.io.File;

@Managed
public abstract class InstalledJdkInternal implements InstalledJdk {
    // We use static private methods because private/protected methods are disallowed
    // in managed types.
    private static File getBinDir(InstalledJdk jdk) {
        return new File(jdk.getJavaHome(), "bin");
    }

    private static File getExecutable(InstalledJdk jdk, String name) {
        return new File(getBinDir(jdk), OperatingSystem.current().getExecutableName(name));
    }

    public File getJavaExecutable() {
        return getExecutable(this, "java");
    }

    public File getJavaCompileExecutable() {
        return getExecutable(this, "javac");
    }

    public File getJavadocExecutable() {
        return getExecutable(this, "javadoc");
    }
}
