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

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import net.rubygrapefruit.platform.WindowsRegistry;
import org.gradle.api.JavaVersion;
import org.gradle.api.internal.file.TestFiles;
import org.gradle.api.specs.Spec;
import org.gradle.integtests.fixtures.executer.GradleDistribution;
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution;
import org.gradle.integtests.fixtures.jvm.JvmInstallation;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.jvm.Jre;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.jvm.inspection.CachingJvmVersionDetector;
import org.gradle.internal.jvm.inspection.DefaultJvmVersionDetector;
import org.gradle.internal.jvm.inspection.JvmVersionDetector;
import org.gradle.internal.operations.TestBuildOperationExecutor;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.jvm.toolchain.internal.AsdfInstallationSupplier;
import org.gradle.jvm.toolchain.internal.CurrentInstallationSupplier;
import org.gradle.jvm.toolchain.internal.InstallationLocation;
import org.gradle.jvm.toolchain.internal.InstallationSupplier;
import org.gradle.jvm.toolchain.internal.JabbaInstallationSupplier;
import org.gradle.jvm.toolchain.internal.LinuxInstallationSupplier;
import org.gradle.jvm.toolchain.internal.OsXInstallationSupplier;
import org.gradle.jvm.toolchain.internal.SdkmanInstallationSupplier;
import org.gradle.jvm.toolchain.internal.SharedJavaInstallationRegistry;
import org.gradle.jvm.toolchain.internal.WindowsInstallationSupplier;
import org.gradle.process.internal.ExecHandleFactory;
import org.gradle.testfixtures.internal.NativeServicesTestFixture;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gradle.util.TestUtil.providerFactory;

/**
 * Allows the tests to get hold of an alternative Java installation when needed.
 */
public abstract class AvailableJavaHomes {

    private static Supplier<List<JvmInstallation>> jvms = Suppliers.memoize(AvailableJavaHomes::discoverLocalInstallations);

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
    public static Jvm getJdk7() {
        return getJdk(JavaVersion.VERSION_1_7);
    }

    @Nullable
    public static Jvm getJdk8() {
        return getJdk(JavaVersion.VERSION_1_8);
    }

    @Nullable
    public static Jvm getJdk9() {
        return getJdk(JavaVersion.VERSION_1_9);
    }

    @Nullable
    public static Jvm getJdk(final JavaVersion version) {
        return Iterables.getFirst(getAvailableJdks(version), null);
    }

    /**
     * Returns a JDK for each of the given java versions, if available.
     */
    public static List<Jvm> getJdks(final String... versions) {
        List<JavaVersion> javaVersions = Lists.transform(Arrays.asList(versions), version -> JavaVersion.toVersion(version));
        return getJdks(Iterables.toArray(javaVersions, JavaVersion.class));
    }

    /**
     * Returns a JDK for each of the given java versions, if available.
     */
    public static List<Jvm> getJdks(JavaVersion... versions) {
        final Set<JavaVersion> remaining = Sets.newHashSet(versions);
        return getAvailableJdks(element -> remaining.remove(element.getJavaVersion()));
    }

    /**
     * Returns all JDKs for the given java version.
     */
    public static List<Jvm> getAvailableJdks(final JavaVersion version) {
        return getAvailableJdks(element -> version.equals(element.getJavaVersion()));
    }

    public static List<Jvm> getAvailableJvms() {
        return FluentIterable.from(getJvms())
            .transform(input -> Jvm.discovered(input.getJavaHome(), null, input.getJavaVersion())).toList();
    }

    public static List<Jvm> getAvailableJdks(final Spec<? super JvmInstallation> filter) {
        return FluentIterable.from(getJvms())
            .filter(input -> input.isJdk() && filter.isSatisfiedBy(input))
            .transform(input -> Jvm.discovered(input.getJavaHome(), null, input.getJavaVersion())).toList();
    }

    public static Map<Jvm, JavaVersion> getAvailableJdksWithVersion() {
        Map<Jvm, JavaVersion> result = new HashMap<>();
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
        return underTest.worksWith(Jvm.discovered(jvmInstallation.getJavaHome(), null, jvmInstallation.getJavaVersion()));
    }

    /**
     * Returns a JDK is that has a different Java home to the current one, and which is supported by the Gradle version under test.
     */
    @Nullable
    public static Jvm getDifferentJdk() {
        return getAvailableJdk(element -> !element.getJavaHome().equals(Jvm.current().getJavaHome()) && isSupportedVersion(element));
    }

    /**
     * Returns a JDK is that has a different Java version to the current one, and which is supported by the Gradle version under test.
     */
    @Nullable
    public static Jvm getDifferentVersion() {
        return getAvailableJdk(element -> !element.getJavaVersion().equals(Jvm.current().getJavaVersion()) && isSupportedVersion(element));
    }

    /**
     * Returns a JDK that has a different Java home to the current one, is supported by the Gradle version under tests and has a valid JRE.
     */
    public static Jvm getDifferentJdkWithValidJre() {
        return AvailableJavaHomes.getAvailableJdk(jvm -> !jvm.getJavaHome().equals(Jvm.current().getJavaHome())
            && isSupportedVersion(jvm)
            && Jvm.discovered(jvm.getJavaHome(), null, jvm.getJavaVersion()).getJre() != null);
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
        jre = jvm.getEmbeddedJre();
        if (jre != null) {
            return jre.getHomeDir();
        }
        // Use the JDK instead
        return jvm.getJavaHome();
    }

    private static List<JvmInstallation> getJvms() {
        return jvms.get();
    }

    private static List<JvmInstallation> discoverLocalInstallations() {
        ExecHandleFactory execHandleFactory = TestFiles.execHandleFactory();
        JvmVersionDetector versionDetector = new CachingJvmVersionDetector(new DefaultJvmVersionDetector(execHandleFactory));
        final List<JvmInstallation> jvms = new SharedJavaInstallationRegistry(defaultInstallationSuppliers(), new TestBuildOperationExecutor())
            .listInstallations().stream()
            .map(i -> asJvmInstallation(i, versionDetector))
            .sorted(Comparator.comparing(JvmInstallation::getJavaVersion))
            .collect(Collectors.toList());

        System.out.println("Found the following JVMs:");
        for (JvmInstallation jvm : jvms) {
            System.out.println("    " + jvm);
        }
        return jvms;
    }

    private static List<InstallationSupplier> defaultInstallationSuppliers() {
        WindowsRegistry windowsRegistry = NativeServicesTestFixture.getInstance().get(WindowsRegistry.class);
        return Lists.newArrayList(
            new AsdfInstallationSupplier(providerFactory()),
            new BaseDirJvmLocator("/opt"),
            new BaseDirJvmLocator(SystemProperties.getInstance().getUserHome()),
            new CurrentInstallationSupplier(providerFactory()),
            new DevInfrastructureJvmLocator(),
            new EnvVariableJvmLocator(),
            new JabbaInstallationSupplier(providerFactory()),
            new LinuxInstallationSupplier(providerFactory()),
            new OsXInstallationSupplier(TestFiles.execHandleFactory(), providerFactory(), OperatingSystem.current()),
            new SdkmanInstallationSupplier(providerFactory()),
            new WindowsInstallationSupplier(windowsRegistry, OperatingSystem.current(), providerFactory())
        );
    }

    private static JvmInstallation asJvmInstallation(File javaHome, JvmVersionDetector versionDetector) {
        JavaVersion version = versionDetector.getJavaVersion(new File(javaHome, "bin/java").getAbsolutePath());
        boolean isJdk = new File(javaHome, OperatingSystem.current().getExecutableName("bin/javac")).exists();
        return new JvmInstallation(version, javaHome, isJdk);
    }


    /**
     * Locate Java installations based on environment variables such as "JDK8", "JDK11", "JDK14", etc.
     * This is a convention from https://docs.gradle.com/enterprise/test-distribution-agent/#capabilities
     */
    private static class EnvVariableJvmLocator implements InstallationSupplier {

        private static final Pattern JDK_PATTERN = Pattern.compile("JDK\\d\\d?");

        @Override
        public Set<InstallationLocation> get() {
            return System.getenv().entrySet()
                .stream()
                .filter(it -> JDK_PATTERN.matcher(it.getKey()).matches())
                .map(entry -> new InstallationLocation(new File(entry.getValue()), "env var " + entry.getKey()))
                .collect(Collectors.toSet());
        }
    }

    private static class DevInfrastructureJvmLocator implements InstallationSupplier {

        @Override
        public Set<InstallationLocation> get() {
            return Stream.of("sun-jdk-5", "sun-jdk-6", "ibm-jdk-6", "oracle-jdk-7", "oracle-jdk-8", "oracle-jdk-9")
                .map(name -> new File("/opt/jdk/", name))
                .filter(File::exists)
                .map(home -> new InstallationLocation(home, "dev infrastructure "))
                .collect(Collectors.toSet());
        }

    }

    private static class BaseDirJvmLocator implements InstallationSupplier {

        private static final Pattern JDK_DIR = Pattern.compile("jdk(\\d+\\.\\d+\\.\\d+(_\\d+)?)");

        private final File baseDir;

        private BaseDirJvmLocator(String baseDir) {
            this.baseDir = new File(baseDir);
        }

        @Override
        public Set<InstallationLocation> get() {
            final File[] files = baseDir.listFiles();
            if (files != null) {
                return Arrays.stream(files)
                    .filter(file -> JDK_DIR.matcher(file.getName()).matches())
                    .map(file -> new InstallationLocation(file, "base dir " + baseDir.getName()))
                    .collect(Collectors.toSet());
            }
            return Collections.emptySet();
        }

    }

}
