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

import java.io.File;

/**
 * Should be synced with {@link org.gradle.internal.jvm.Jvm}.
 */
public class JvmInstallation {

    private final JavaVersion javaVersion;
    private final File javaHome;
    private final boolean jdk;

    public JvmInstallation(JavaVersion javaVersion, File javaHome, boolean jdk) {
        this.javaVersion = javaVersion;
        this.javaHome = javaHome;
        this.jdk = jdk;
    }

    @Override
    public String toString() {
        return String.format("%s %s %s", jdk ? "JDK" : "JRE", javaVersion, javaHome);
    }

    public boolean isJdk() {
        return jdk;
    }

    public JavaVersion getJavaVersion() {
        return javaVersion;
    }

    public File getJavaHome() {
        return javaHome;
    }
}
