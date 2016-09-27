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

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import net.rubygrapefruit.platform.SystemInfo;
import net.rubygrapefruit.platform.WindowsRegistry;
import org.gradle.api.JavaVersion;
import org.gradle.api.Nullable;
import org.gradle.api.specs.Spec;
import org.gradle.integtests.fixtures.executer.GradleDistribution;
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution;
import org.gradle.integtests.fixtures.jvm.InstalledJvmLocator;
import org.gradle.integtests.fixtures.jvm.JvmInstallation;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.jvm.Jre;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.nativeintegration.filesystem.FileCanonicalizer;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.testfixtures.internal.NativeServicesTestFixture;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Allows the tests to get hold of an alternative Java installation when needed.
 */
public abstract class AvailableJavaHomes {
    private static List<JvmInstallation> jvms;
    private static GradleDistribution underTest = new UnderDevelopmentGradleDistribution();

    @Nullable
    public static Jvm getJava5() {
        return getJdk(JavaVersion.VERSION_1_5);
    }

    @Nullable
    public static Jvm getJdk6() {
        return getJdk(JavaVersion.VERSION_1_6);
    }

    @Nullable
    public static Jvm getJdk(final JavaVersion version) {
        return Iterables.getFirst(getAvailableJdks(version), null);
    }

    /**
     * Returns a JDK for each of the given java versions, if available.
     */
    public static List<Jvm> getJdks(final String... versions) {
        List<JavaVersion> javaVersions = Lists.transform(Arrays.asList(versions), new Function<String, JavaVersion>() {
            @Override
            public JavaVersion apply(@javax.annotation.Nullable String version) {
                return JavaVersion.toVersion(version);
            }
        });
        return getJdks(Iterables.toArray(javaVersions, JavaVersion.class));
    }

    /**
     * Returns a JDK for each of the given java versions, if available.
     */
    public static List<Jvm> getJdks(JavaVersion... versions) {
        final Set<JavaVersion> remaining = Sets.newHashSet(versions);
        return getAvailableJdks(new Spec<JvmInstallation>() {
            @Override
            public boolean isSatisfiedBy(JvmInstallation element) {
                return remaining.remove(element.getJavaVersion());
            }
        });
    }

    /**
     * Returns all JDKs for the given java version.
     */
    public static List<Jvm> getAvailableJdks(final JavaVersion version) {
        return getAvailableJdks(new Spec<JvmInstallation>() {
            @Override
            public boolean isSatisfiedBy(JvmInstallation element) {
                return version.equals(element.getJavaVersion());
            }
        });
    }

    public static List<Jvm> getAvailableJvms() {
        return FluentIterable.from(getJvms())
            .transform(new Function<JvmInstallation, Jvm>() {
                @Override
                public Jvm apply(JvmInstallation input) {
                    return Jvm.discovered(input.getJavaHome(), input.getJavaVersion());
                }
            }).toList();
    }

    public static List<Jvm> getAvailableJdks(final Spec<? super JvmInstallation> filter) {
        return FluentIterable.from(getJvms())
            .filter(new Predicate<JvmInstallation>() {
                @Override
                public boolean apply(JvmInstallation input) {
                    return input.isJdk() && filter.isSatisfiedBy(input);
                }
            })
            .transform(new Function<JvmInstallation, Jvm>() {
                @Override
                public Jvm apply(JvmInstallation input) {
                    return Jvm.discovered(input.getJavaHome(), input.getJavaVersion());
                }
            }).toList();
    }

    public static Map<Jvm, JavaVersion> getAvailableJdksWithVersion() {
        Map<Jvm, JavaVersion> result = new HashMap<Jvm, JavaVersion>();
        for (JavaVersion javaVersion : JavaVersion.values()) {
            for (Jvm javaInfo : getAvailableJdks(javaVersion)) {
                result.put(javaInfo, javaVersion);
            }
        }
        return result;
    }

    public static Jvm getAvailableJdk(final Spec<? super JvmInstallation> filter) {
        return Iterables.getFirst(getAvailableJdks(filter), null);
    }

    private static boolean isSupportedVersion(JvmInstallation jvmInstallation) {
        return underTest.worksWith(Jvm.discovered(jvmInstallation.getJavaHome(), jvmInstallation.getJavaVersion()));
    }

    /**
     * Returns a JDK is that has a different Java home to the current one, and which is supported by the Gradle version under test.
     */
    @Nullable
    public static Jvm getDifferentJdk() {
        return getAvailableJdk(new Spec<JvmInstallation>() {
            @Override
            public boolean isSatisfiedBy(JvmInstallation element) {
                return !element.getJavaHome().equals(Jvm.current().getJavaHome()) && isSupportedVersion(element);
            }
        });
    }

    /**
     * Returns a JDK is that has a different Java version to the current one, and which is supported by the Gradle version under test.
     */
    @Nullable
    public static Jvm getDifferentVersion() {
        return getAvailableJdk(new Spec<JvmInstallation>() {
            @Override
            public boolean isSatisfiedBy(JvmInstallation element) {
                return !element.getJavaVersion().equals(Jvm.current().getJavaVersion()) && isSupportedVersion(element);
            }
        });
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
            NativeServices nativeServices = NativeServicesTestFixture.getInstance();
            FileCanonicalizer fileCanonicalizer = nativeServices.get(FileCanonicalizer.class);
            jvms = new ArrayList<JvmInstallation>();
            jvms.addAll(new DevInfrastructureJvmLocator(fileCanonicalizer).findJvms());
            InstalledJvmLocator installedJvmLocator = new InstalledJvmLocator(OperatingSystem.current(), Jvm.current(), nativeServices.get(WindowsRegistry.class), nativeServices.get(SystemInfo.class), fileCanonicalizer);
            jvms.addAll(installedJvmLocator.findJvms());
            if (OperatingSystem.current().isLinux()) {
                jvms.addAll(new BaseDirJvmLocator(fileCanonicalizer, new File("/opt")).findJvms());
            }
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
                jvms = addJvm(jvms, JavaVersion.VERSION_1_9, "1.9.0", new File("/opt/jdk/oracle-jdk-9"), true, JvmInstallation.Arch.x86_64);
            }
            return CollectionUtils.filter(jvms, new Spec<JvmInstallation>() {
                public boolean isSatisfiedBy(JvmInstallation element) {
                    return element.getJavaHome().isDirectory();
                }
            });
        }

        private List<JvmInstallation> addJvm(List<JvmInstallation> jvms, JavaVersion javaVersion, String versionString, File javaHome, boolean jdk, JvmInstallation.Arch arch) {
            if (javaHome.exists()) {
                jvms.add(new JvmInstallation(javaVersion, versionString, fileCanonicalizer.canonicalize(javaHome), jdk, arch));
            }
            return jvms;
        }
    }

    private static class BaseDirJvmLocator {
        private static final Pattern JDK_DIR = Pattern.compile("jdk(\\d+\\.\\d+\\.\\d+(_\\d+)?)");
        private final FileCanonicalizer fileCanonicalizer;
        private final File baseDir;

        private BaseDirJvmLocator(FileCanonicalizer fileCanonicalizer, File baseDir) {
            this.fileCanonicalizer = fileCanonicalizer;
            this.baseDir = baseDir;
        }

        public List<JvmInstallation> findJvms() {
            Set<File> javaHomes = new HashSet<File>();
            List<JvmInstallation> jvms = new ArrayList<JvmInstallation>();
            for (File file : baseDir.listFiles()) {
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

    private static class HomeDirJvmLocator extends BaseDirJvmLocator {

        private HomeDirJvmLocator(FileCanonicalizer fileCanonicalizer) {
            super(fileCanonicalizer, new File(SystemProperties.getInstance().getUserHome()));
        }
    }
}
