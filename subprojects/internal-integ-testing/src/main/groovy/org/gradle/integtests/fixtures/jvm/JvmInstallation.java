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

package org.gradle.integtests.fixtures.jvm;

import org.gradle.api.JavaVersion;
import org.gradle.util.VersionNumber;

import java.io.File;

public class JvmInstallation {
    public enum Arch { i386, x86_64, Unknown }

    private final JavaVersion javaVersion;
    private final VersionNumber version;
    private final File javaHome;
    private final boolean jdk;
    private final Arch arch;

    public JvmInstallation(JavaVersion javaVersion, String version, File javaHome, boolean jdk, Arch arch) {
        this.javaVersion = javaVersion;
        this.version = VersionNumber.withPatchNumber().parse(version);
        this.javaHome = javaHome;
        this.jdk = jdk;
        this.arch = arch;
    }

    @Override
    public String toString() {
        return String.format("%s %s (%s) %s %s", jdk ? "JDK" : "JRE", javaVersion, version, arch, javaHome);
    }

    public Arch getArch() {
        return arch;
    }

    public boolean isJdk() {
        return jdk;
    }

    public VersionNumber getVersion() {
        return version;
    }

    public JavaVersion getJavaVersion() {
        return javaVersion;
    }

    public File getJavaHome() {
        return javaHome;
    }
}
