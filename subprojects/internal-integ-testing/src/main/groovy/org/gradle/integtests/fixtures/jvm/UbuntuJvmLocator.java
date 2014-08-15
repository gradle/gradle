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
import org.gradle.internal.nativeintegration.filesystem.FileCanonicalizer;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Searches for JVM installations in /usr/lib/jvm. Currently assumes the Ubuntu OpenJDK convention.
 */
public class UbuntuJvmLocator {
    private static final Pattern JAVA_HOME_DIR_PATTERN = Pattern.compile("java-(\\d+.\\d+(?:\\.\\d+)?)-\\w+-(\\w+)");
    private final FileCanonicalizer fileCanonicalizer;
    private final File libDir;

    public UbuntuJvmLocator(FileCanonicalizer fileCanonicalizer) {
        this(new File("/usr/lib/jvm"), fileCanonicalizer);
    }

    UbuntuJvmLocator(File libDir, FileCanonicalizer fileCanonicalizer) {
        this.libDir = libDir;
        this.fileCanonicalizer = fileCanonicalizer;
    }

    public Collection<JvmInstallation> findJvms() {
        List<JvmInstallation> jvms = new ArrayList<JvmInstallation>();
        if (libDir.isDirectory()) {
            for (File javaHome : libDir.listFiles()) {
                Matcher matcher = JAVA_HOME_DIR_PATTERN.matcher(javaHome.getName());
                if (!matcher.matches()) {
                    continue;
                }
                if (!new File(javaHome, "jre/bin/java").isFile()) {
                    continue;
                }
                String version = matcher.group(1);
                String arch = matcher.group(2);
                boolean jdk = new File(javaHome, "bin/javac").isFile();
                jvms.add(new JvmInstallation(JavaVersion.toVersion(version), version, fileCanonicalizer.canonicalize(javaHome), jdk, toArch(arch)));
            }
        }
        return jvms;
    }

    private JvmInstallation.Arch toArch(String arch) {
        if (arch.equals("amd64")) {
            return JvmInstallation.Arch.x86_64;
        } else if (arch.equals("i386")) {
            return JvmInstallation.Arch.i386;
        } else {
            return JvmInstallation.Arch.Unknown;
        }
    }
}
