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

package org.gradle.internal.jvm;

import com.google.common.collect.ImmutableSet;
import org.gradle.internal.nativeintegration.filesystem.FileCanonicalizer;

import java.io.File;
import java.io.FileFilter;
import java.util.Set;

public class UnixJavaInstallationsDirLocatorStrategy implements JavaInstallationsDirLocatorStrategy {

    private static final File[] CONVENTIONAL_ROOTS = new File[]{
        new File("/usr/lib/jvm"),       // *deb, Arch
        new File("/opt"),               // *rpm, Gentoo, HP/UX
        new File("/usr/lib"),           // Slackware 32
        new File("/usr/lib64"),         // Slackware 64
        new File("/usr/local"),         // OpenBSD, FreeBSD
        new File("/usr/pkg/java"),      // NetBSD
        new File("/usr/jdk/instances"), // Solaris
    };

    private static final String[] CONVENTIONAL_DIR_NAME_PARTS = new String[]{
        "jdk", "jre", "java", "zulu"
    };

    private final FileCanonicalizer fileCanonicalizer;

    public UnixJavaInstallationsDirLocatorStrategy(FileCanonicalizer fileCanonicalizer) {
        this.fileCanonicalizer = fileCanonicalizer;
    }

    @Override
    public Set<File> findJavaInstallationsDirs() {
        ImmutableSet.Builder<File> builder = ImmutableSet.<File>builder();
        for (File root : CONVENTIONAL_ROOTS) {
            File[] javaInstallations = root.listFiles(new JavaInstallationFileFilter());
            if (javaInstallations != null) {
                for (File javaInstall : javaInstallations) {
                    builder.add(fileCanonicalizer.canonicalize(javaInstall));
                }
            }
        }
        return builder.build();
    }

    public static class JavaInstallationFileFilter implements FileFilter {
        @Override
        public boolean accept(File candidate) {
            if (candidate.isDirectory()) {
                for (String dirNamePart : CONVENTIONAL_DIR_NAME_PARTS) {
                    if (candidate.getName().toLowerCase().contains(dirNamePart)
                        && new File(candidate, "bin" + File.separator + "java").isFile()) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
