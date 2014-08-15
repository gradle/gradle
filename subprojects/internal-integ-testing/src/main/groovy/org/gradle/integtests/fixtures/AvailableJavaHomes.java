/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.fixtures;

import org.gradle.api.JavaVersion;
import org.gradle.api.Nullable;
import org.gradle.api.specs.Spec;
import org.gradle.integtests.fixtures.jvm.InstalledJvmLocator;
import org.gradle.integtests.fixtures.jvm.JvmInstallation;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.jvm.JavaInfo;
import org.gradle.internal.jvm.Jre;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.nativeintegration.filesystem.FileCanonicalizer;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Allows the tests to get hold of an alternative Java installation when needed.
 */
abstract public class AvailableJavaHomes {
    private static List<JvmInstallation> jvms;

    @Nullable
    public static JavaInfo getJava5() {
        return getJdk(JavaVersion.VERSION_1_5);
    }

    /**
     * Locates a JDK installation for the given version.
     *
     * @return null if not found.
     */
    @Nullable
    public static JavaInfo getJdk(JavaVersion version) {
        for (JvmInstallation candidate : getJvms()) {
            if (candidate.getJavaVersion().equals(version) && candidate.isJdk()) {
                return Jvm.forHome(candidate.getJavaHome());
            }
        }
        return null;
    }

    /**
     * Locates a JDK installation that is different to the current JVM, ie for which java.home is different.
     *
     * @return null if not found.
     */
    @Nullable
    public static JavaInfo getDifferentJdk() {
        Jvm jvm = Jvm.current();
        for (JvmInstallation candidate : getJvms()) {
            if (candidate.getJavaHome().equals(jvm.getJavaHome())) {
                continue;
            }

            // Currently tests implicitly assume a JDK
            if (!candidate.isJdk()) {
                continue;
            }
            return Jvm.forHome(candidate.getJavaHome());
        }

        return null;
    }

    /**
     * Locates a JDK installation that has a different version to the current JVM, ie for which java.version is different.
     *
     * @return null if not found.
     */
    @Nullable
    public static JavaInfo getDifferentVersion() {
        Jvm jvm = Jvm.current();
        for (JvmInstallation candidate : getJvms()) {
            if (candidate.getJavaVersion().equals(jvm.getJavaVersion())) {
                continue;
            }
            // Currently tests implicitly assume a JDK
            if (!candidate.isJdk()) {
                continue;
            }
            return Jvm.forHome(candidate.getJavaHome());
        }

        return null;
    }

    /**
     * Locates a JRE installation for the current JVM. Prefers a stand-alone JRE installation over one that is part of a JDK install.
     *
     * @return The JRE home directory, or null if not found
     */
    public static File getBestJre() {
        Jvm jvm = Jvm.current();
        Jre jre = jvm.getStandaloneJre();
        if (jre != null) {
            return jre.getHomeDir();
        }
        jre = jvm.getJre();
        if (jre != null) {
            return jre.getHomeDir();
        }
        return null;
    }

    private static List<JvmInstallation> getJvms() {
        if (jvms == null) {
            FileCanonicalizer fileCanonicalizer = NativeServices.getInstance().get(FileCanonicalizer.class);
            jvms = new ArrayList<JvmInstallation>();
            jvms.addAll(new DevInfrastructureJvmLocator(fileCanonicalizer).findJvms());
            jvms.addAll(new InstalledJvmLocator().findJvms());
            jvms.addAll(new HomeDirJvmLocator(fileCanonicalizer).findJvms());
            // Order from most recent to least recent
            Collections.sort(jvms, new Comparator<JvmInstallation>() {
                public int compare(JvmInstallation o1, JvmInstallation o2) {
                    return o2.getVersion().compareTo(o1.getVersion());
                }
            });
        }
        System.out.println("Found the following JVMs:");
        for (JvmInstallation jvm : jvms) {
            System.out.println("    " + jvm);
        }
        return jvms;
    }

    private static class DevInfrastructureJvmLocator {
        final FileCanonicalizer fileCanonicalizer;

        private DevInfrastructureJvmLocator(FileCanonicalizer fileCanonicalizer) {
            this.fileCanonicalizer = fileCanonicalizer;
        }

        public List<JvmInstallation> findJvms() {
            List<JvmInstallation> jvms = new ArrayList<JvmInstallation>();
            if (OperatingSystem.current().isLinux()) {
                jvms = addJvm(jvms, JavaVersion.VERSION_1_5, "1.5.0", new File("/opt/jdk/sun-jdk-5"), true, JvmInstallation.Arch.i386);
                jvms = addJvm(jvms, JavaVersion.VERSION_1_6, "1.6.0", new File("/opt/jdk/sun-jdk-6"), true, JvmInstallation.Arch.x86_64);
                jvms = addJvm(jvms, JavaVersion.VERSION_1_6, "1.6.0", new File("/opt/jdk/ibm-jdk-6"), true, JvmInstallation.Arch.x86_64);
                jvms = addJvm(jvms, JavaVersion.VERSION_1_7, "1.7.0", new File("/opt/jdk/oracle-jdk-7"), true, JvmInstallation.Arch.x86_64);
                jvms = addJvm(jvms, JavaVersion.VERSION_1_8, "1.8.0", new File("/opt/jdk/oracle-jdk-8"), true, JvmInstallation.Arch.x86_64);
            }
            return CollectionUtils.filter(jvms, new Spec<JvmInstallation>() {
                public boolean isSatisfiedBy(JvmInstallation element) {
                    return element.getJavaHome().isDirectory();
                }
            });
        }

        private List<JvmInstallation> addJvm(List<JvmInstallation> jvms, JavaVersion javaVersion, String versionString, File javaHome, boolean jdk, JvmInstallation.Arch arch) {
            if(javaHome.exists()) {
                jvms.add(new JvmInstallation(javaVersion, versionString, fileCanonicalizer.canonicalize(javaHome), jdk, arch));
            }
            return jvms;
        }
    }

    private static class HomeDirJvmLocator {
        private static final Pattern JDK_DIR = Pattern.compile("jdk(\\d+\\.\\d+\\.\\d+(_\\d+)?)");
        final FileCanonicalizer fileCanonicalizer;

        private HomeDirJvmLocator(FileCanonicalizer fileCanonicalizer) {
            this.fileCanonicalizer = fileCanonicalizer;
        }

        public List<JvmInstallation> findJvms() {
            Set<File> javaHomes = new HashSet<File>();
            List<JvmInstallation> jvms = new ArrayList<JvmInstallation>();
            for (File file : new File(SystemProperties.getUserHome()).listFiles()) {
                Matcher matcher = JDK_DIR.matcher(file.getName());
                if (!matcher.matches()) {
                    continue;
                }
                File javaHome = fileCanonicalizer.canonicalize(file);
                if (!javaHomes.add(javaHome)) {
                    continue;
                }
                if (!new File(file, "bin/javac").isFile()) {
                    continue;
                }
                String version = matcher.group(1);
                jvms.add(new JvmInstallation(JavaVersion.toVersion(version), version, file, true, JvmInstallation.Arch.Unknown));
            }
            return jvms;
        }
    }
}
