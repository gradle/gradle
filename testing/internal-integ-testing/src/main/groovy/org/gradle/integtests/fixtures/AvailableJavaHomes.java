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
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import net.rubygrapefruit.platform.WindowsRegistry;
import org.gradle.api.JavaVersion;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.api.internal.file.TestFiles;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.specs.Spec;
import org.gradle.integtests.fixtures.executer.GradleDistribution;
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.jvm.JavaInfo;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.jvm.SupportedJavaVersions;
import org.gradle.internal.jvm.inspection.CachingJvmMetadataDetector;
import org.gradle.internal.jvm.inspection.DefaultJavaInstallationRegistry;
import org.gradle.internal.jvm.inspection.DefaultJvmMetadataDetector;
import org.gradle.internal.jvm.inspection.JavaInstallationCapability;
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata;
import org.gradle.internal.jvm.inspection.JvmInstallationProblemReporter;
import org.gradle.internal.jvm.inspection.JvmMetadataDetector;
import org.gradle.internal.operations.TestBuildOperationRunner;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.progress.NoOpProgressLoggerFactory;
import org.gradle.jvm.toolchain.internal.AsdfInstallationSupplier;
import org.gradle.jvm.toolchain.internal.CurrentInstallationSupplier;
import org.gradle.jvm.toolchain.internal.DefaultOsXJavaHomeCommand;
import org.gradle.jvm.toolchain.internal.DefaultToolchainConfiguration;
import org.gradle.jvm.toolchain.internal.InstallationLocation;
import org.gradle.jvm.toolchain.internal.InstallationSupplier;
import org.gradle.jvm.toolchain.internal.IntellijInstallationSupplier;
import org.gradle.jvm.toolchain.internal.JabbaInstallationSupplier;
import org.gradle.jvm.toolchain.internal.LinuxInstallationSupplier;
import org.gradle.jvm.toolchain.internal.OsXInstallationSupplier;
import org.gradle.jvm.toolchain.internal.SdkmanInstallationSupplier;
import org.gradle.jvm.toolchain.internal.ToolchainConfiguration;
import org.gradle.jvm.toolchain.internal.WindowsInstallationSupplier;
import org.gradle.process.internal.ExecHandleFactory;
import org.gradle.testfixtures.internal.NativeServicesTestFixture;

import javax.annotation.Nullable;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.gradle.jvm.toolchain.internal.LocationListInstallationSupplier.JAVA_INSTALLATIONS_PATHS_PROPERTY;

/**
 * Allows the tests to get hold of an alternative Java installation when needed.
 */
public abstract class AvailableJavaHomes {

    private static final Supplier<List<JvmInstallationMetadata>> INSTALLATIONS = Suppliers.memoize(AvailableJavaHomes::discoverLocalInstallations);

    private static final GradleDistribution DISTRIBUTION = new UnderDevelopmentGradleDistribution();

    @Nullable
    public static Jvm getJdk7() {
        return getJdk(JavaVersion.VERSION_1_7);
    }

    @Nullable
    public static Jvm getJdk8() {
        return getJdk(JavaVersion.VERSION_1_8);
    }

    @Nullable
    public static Jvm getJdk11() {
        return getJdk(JavaVersion.VERSION_11);
    }

    @Nullable
    public static Jvm getJdk17() {
        return getJdk(JavaVersion.VERSION_17);
    }
    @Nullable
    public static Jvm getJdk21() {
        return getJdk(JavaVersion.VERSION_21);
    }

    @Nullable
    public static Jvm getJdk22() {
        return getJdk(JavaVersion.VERSION_22);
    }

    @Nullable
    public static Jvm getJdk23() {
        return getJdk(JavaVersion.VERSION_23);
    }

    @Nullable
    public static Jvm getHighestSupportedLTS() {
        return getJdk21();
    }

    @Nullable
    public static Jvm getLowestSupportedLTS() {
        return getJdk8();
    }

    /**
     * Get a JVM for each major Java version that is not able to run the Gradle daemon, if available.
     */
    public static List<Jvm> getUnsupportedDaemonJdks() {
        return getJdks(
            IntStream.range(1, SupportedJavaVersions.MINIMUM_JAVA_VERSION)
                .mapToObj(JavaVersion::toVersion)
                .toArray(JavaVersion[]::new)
        );
    }

    /**
     * Get a JVM that is not able to run the Gradle daemon.
     */
    @Nullable
    public static Jvm getUnsupportedDaemonJdk() {
        return getAvailableJdk(element -> {
            int majorVersion = Integer.parseInt(element.getLanguageVersion().getMajorVersion());
            return majorVersion < SupportedJavaVersions.MINIMUM_JAVA_VERSION;
        });
    }

    /**
     * Get a JVM that can run the Gradle daemon, but will not be able to in the next major version.
     */
    @Nullable
    public static Jvm getDeprecatedDaemonJdk() {
        return getAvailableJdk(element -> {
            int majorVersion = Integer.parseInt(element.getLanguageVersion().getMajorVersion());
            return majorVersion >= SupportedJavaVersions.MINIMUM_JAVA_VERSION &&
                majorVersion < SupportedJavaVersions.FUTURE_MINIMUM_JAVA_VERSION;
            }
        );
    }

    /**
     * Get a JVM that can run the Gradle daemon and will continue to be able to in the next major version.
     */
    @Nullable
    public static Jvm getNonDeprecatedDaemonJdk() {
        return getAvailableJdk(element -> {
            int majorVersion = Integer.parseInt(element.getLanguageVersion().getMajorVersion());
            return majorVersion > SupportedJavaVersions.FUTURE_MINIMUM_JAVA_VERSION;
        });
    }

    @Nullable
    public static Jvm getJdk(final JavaVersion version) {
        return Iterables.getFirst(getAvailableJdks(version), null);
    }

    /**
     * Returns a JDK for each of the given java versions, if available.
     */
    public static List<Jvm> getJdks(final String... versions) {
        List<JavaVersion> javaVersions = Arrays.stream(versions).map(JavaVersion::toVersion).collect(Collectors.toList());
        return getJdks(Iterables.toArray(javaVersions, JavaVersion.class));
    }

    /**
     * Returns a JDK for each of the given java versions, if available.
     */
    public static List<Jvm> getJdks(JavaVersion... versions) {
        final Set<JavaVersion> remaining = Sets.newHashSet(versions);
        return getAvailableJdks(element -> remaining.remove(element.getLanguageVersion()));
    }

    /**
     * Returns all JDKs for the given java version.
     */
    public static List<Jvm> getAvailableJdks(final JavaVersion version) {
        return getAvailableJdks(element -> version.equals(element.getLanguageVersion()));
    }

    public static List<Jvm> getAvailableJdks(final Spec<? super JvmInstallationMetadata> filter) {
        return getAvailableJvmMetadatas().stream()
            .filter(input -> input.getCapabilities().containsAll(JavaInstallationCapability.JDK_CAPABILITIES))
            .filter(filter::isSatisfiedBy)
            .map(AvailableJavaHomes::jvmFromMetadata)
            .collect(Collectors.toList());
    }

    public static List<Jvm> getAvailableJvms() {
        return getAvailableJvmMetadatas().stream().map(AvailableJavaHomes::jvmFromMetadata).collect(Collectors.toList());
    }

    public static List<Jvm> getAvailableJvms(final Spec<? super JvmInstallationMetadata> filter) {
        return getAvailableJvmMetadatas().stream()
            .filter(filter::isSatisfiedBy)
            .map(AvailableJavaHomes::jvmFromMetadata)
            .collect(Collectors.toList());
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

    public static Map<JavaInfo, JavaVersion> getAvailableJdksWithJavac() {
        return getAvailableJdksWithVersion()
            .entrySet()
            .stream()
            .filter(entry -> entry.getKey().getJavacExecutable()!=null && entry.getValue().isJava8Compatible())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Nullable
    public static Jvm getAvailableJdk(final Spec<? super JvmInstallationMetadata> filter) {
        return Iterables.getFirst(getAvailableJdks(filter), null);
    }

    @Nullable
    private static Jvm getSupportedJdk(final Spec<? super JvmInstallationMetadata> filter) {
        return getAvailableJdk(it -> isSupportedVersion(it) && filter.isSatisfiedBy(it));
    }

    @Nullable
    private static Jvm getSupportedJvm(final Spec<? super JvmInstallationMetadata> filter) {
        return Iterables.getFirst(getAvailableJvms(it -> isSupportedVersion(it) && filter.isSatisfiedBy(it)), null);
    }

    private static boolean isSupportedVersion(JvmInstallationMetadata jvmInstallation) {
        return DISTRIBUTION.worksWith(jvmFromMetadata(jvmInstallation));
    }

    /**
     * Returns a JDK that has a different Java home than the current one, and which is supported by the Gradle version under test.
     */
    @Nullable
    public static Jvm getDifferentJdk() {
        return getSupportedJdk(element -> !isCurrentJavaHome(element));
    }

    /**
     * Returns a JDK that has a different Java home than the current one, and which is supported by the Gradle version under test.
     */
    @Nullable
    public static Jvm getDifferentJdk(final Spec<? super JvmInstallationMetadata> filter) {
        return getSupportedJdk(element -> !isCurrentJavaHome(element) && filter.isSatisfiedBy(element));
    }

    /**
     * Returns a JDK that has a different Java version to the current one, and which is supported by the Gradle version under test.
     */
    @Nullable
    public static Jvm getDifferentVersion() {
        return getSupportedJdk(element -> !element.getLanguageVersion().equals(Jvm.current().getJavaVersion()));
    }

    /**
     * Returns a JDK that has a different Java version to the current one, and which is supported by the Gradle version under test.
     */
    @Nullable
    public static Jvm getDifferentVersion(final Spec<? super JvmInstallationMetadata> filter) {
        return getSupportedJdk(element -> !element.getLanguageVersion().equals(Jvm.current().getJavaVersion()) && filter.isSatisfiedBy(element));
    }

    /**
     * Returns a JDK that has a different Java version to the current one and to the provided one,
     * and which is supported by the Gradle version under test.
     */
    @Nullable
    public static Jvm getDifferentVersion(JavaVersion excludeVersion) {
        return getSupportedJdk(element -> !element.getLanguageVersion().equals(Jvm.current().getJavaVersion()) && !element.getLanguageVersion().equals(excludeVersion));
    }

    /**
     * Returns a JRE that has a different Java version to the current one, and which is supported by the Gradle version under test.
     */
    @Nullable
    public static Jvm getDifferentVersionJreOnly() {
        return getSupportedJvm(element -> !element.getLanguageVersion().equals(Jvm.current().getJavaVersion()) && Collections.disjoint(element.getCapabilities(), JavaInstallationCapability.JDK_CAPABILITIES));
    }

    /**
     * Returns a JDK that has a different Java home to the current one, is supported by the Gradle version under tests and has a valid JRE.
     */
    public static Jvm getDifferentJdkWithValidJre() {
        return getSupportedJdk(jvm -> !isCurrentJavaHome(jvm)
            && Jvm.discovered(jvm.getJavaHome().toFile(), null, Integer.parseInt(jvm.getLanguageVersion().getMajorVersion())).getJre() != null);
    }

    public static boolean isCurrentJavaHome(JvmInstallationMetadata metadata) {
        return metadata.getJavaHome().toFile().equals(Jvm.current().getJavaHome());
    }

    /**
     * Locates a JRE installation for the current JVM. Prefers a stand-alone JRE installation over one that is part of a JDK install.
     *
     * @return The JRE home directory, or null if not found
     */
    public static File getBestJre() {
        // TODO: We should improve this to look for any JRE from any JVM installation
        return Jvm.current().getJre();
    }

    public static JvmInstallationMetadata getJvmInstallationMetadata(Jvm jvm) {
        Path targetJavaHome = jvm.getJavaHome().toPath();
        return INSTALLATIONS.get().stream()
            .filter(it -> it.getJavaHome().equals(targetJavaHome))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No JVM installation found for " + jvm));
    }

    private static Jvm jvmFromMetadata(JvmInstallationMetadata metadata) {
        return Jvm.discovered(metadata.getJavaHome().toFile(), metadata.getJavaVersion(), Integer.parseInt(metadata.getLanguageVersion().getMajorVersion()));
    }

    public static List<JvmInstallationMetadata> getAvailableJvmMetadatas() {
        return INSTALLATIONS.get();
    }

    private static List<JvmInstallationMetadata> discoverLocalInstallations() {
        ExecHandleFactory execHandleFactory = TestFiles.execHandleFactory();
        TemporaryFileProvider temporaryFileProvider = TestFiles.tmpDirTemporaryFileProvider(new File(SystemProperties.getInstance().getJavaIoTmpDir()));
        DefaultJvmMetadataDetector defaultJvmMetadataDetector =
            new DefaultJvmMetadataDetector(execHandleFactory, temporaryFileProvider);
        JvmMetadataDetector metadataDetector = new CachingJvmMetadataDetector(defaultJvmMetadataDetector);
        ToolchainConfiguration toolchainConfiguration = new DefaultToolchainConfiguration();
        final List<JvmInstallationMetadata> jvms = new DefaultJavaInstallationRegistry(toolchainConfiguration, defaultInstallationSuppliers(toolchainConfiguration), metadataDetector, new TestBuildOperationRunner(), OperatingSystem.current(), new NoOpProgressLoggerFactory(), new IdentityFileResolver(), Collections::emptySet, new JvmInstallationProblemReporter())
            .toolchains()
            .stream()
            .map(x -> x.metadata)
            .filter(JvmInstallationMetadata::isValidInstallation)
            .sorted(Comparator.comparing(JvmInstallationMetadata::getDisplayName).thenComparing(JvmInstallationMetadata::getLanguageVersion))
            .collect(Collectors.toList());

        System.out.println("Found the following JVMs:");
        for (JvmInstallationMetadata jvm : jvms) {
            String name = jvm.getDisplayName() + " " + jvm.getJavaVersion() + " ";
            System.out.println("    " + name + " - " + jvm.getJavaHome());
        }
        return jvms;
    }

    private static List<InstallationSupplier> defaultInstallationSuppliers(ToolchainConfiguration toolchainConfiguration) {
        WindowsRegistry windowsRegistry = NativeServicesTestFixture.getInstance().get(WindowsRegistry.class);
        return Lists.newArrayList(
            new AsdfInstallationSupplier(toolchainConfiguration),
            new BaseDirJvmLocator(SystemProperties.getInstance().getUserHome()),
            new CurrentInstallationSupplier(),
            new ToolchainInstallatioinPathsSystemPropertyJvmLocator(),
            new EnvVariableJvmLocator(),
            new IntellijInstallationSupplier(toolchainConfiguration),
            new JabbaInstallationSupplier(toolchainConfiguration),
            new LinuxInstallationSupplier(),
            new OsXInstallationSupplier(OperatingSystem.current(), new DefaultOsXJavaHomeCommand(TestFiles.execHandleFactory())),
            new SdkmanInstallationSupplier(toolchainConfiguration),
            new WindowsInstallationSupplier(windowsRegistry, OperatingSystem.current())
        );
    }

    /**
     * Locate Java installations based on environment variables such as "JDK8", "JDK11", "JDK14", etc.
     * This is a convention from https://docs.gradle.com/enterprise/test-distribution-agent/#capabilities
     */
    private static class EnvVariableJvmLocator implements InstallationSupplier {

        private static final Pattern JDK_PATTERN = Pattern.compile("JDK\\d\\d?");

        @Override
        public String getSourceName() {
            return "JDK env variables";
        }

        @Override
        public Set<InstallationLocation> get() {
            return System.getenv().entrySet()
                .stream()
                .filter(it -> JDK_PATTERN.matcher(it.getKey()).matches())
                .map(entry -> InstallationLocation.userDefined(new File(entry.getValue()), "env var " + entry.getKey()))
                .collect(Collectors.toSet());
        }
    }

    /**
     * On CI we pass -Porg.gradle.java.installations.paths=X,Y,Z to the build, then "forward" it
     * as system property to get deterministic results.
     */
    private static class ToolchainInstallatioinPathsSystemPropertyJvmLocator implements InstallationSupplier {

        @Override
        public String getSourceName() {
            return "System properties " + JAVA_INSTALLATIONS_PATHS_PROPERTY;
        }

        @Override
        public Set<InstallationLocation> get() {
            final String property = System.getProperty(JAVA_INSTALLATIONS_PATHS_PROPERTY);
            if (property != null) {
                return Arrays.stream(property.split(","))
                    .filter(path -> !path.trim().isEmpty())
                    .map(path -> InstallationLocation.userDefined(new File(path), getSourceName()))
                    .collect(Collectors.toSet());
            }
            return Collections.emptySet();
        }
    }

    private static class BaseDirJvmLocator implements InstallationSupplier {

        private final File baseDir;

        private BaseDirJvmLocator(String baseDir) {
            this.baseDir = new File(baseDir);
        }

        @Override
        public String getSourceName() {
            return "base dir " + baseDir.getName();
        }

        @Override
        public Set<InstallationLocation> get() {
            final File[] files = baseDir.listFiles();
            if (files != null) {
                return Arrays.stream(files)
                    .filter(File::isDirectory)
                    .filter(file -> file.getName().toLowerCase().contains("jdk") || file.getName().toLowerCase().contains("jre"))
                    .filter(file -> new File(file, OperatingSystem.current().getExecutableName("bin/java")).exists())
                    .map(file -> InstallationLocation.autoDetected(file, getSourceName()))
                    .collect(Collectors.toSet());
            }
            return Collections.emptySet();
        }

    }

}
