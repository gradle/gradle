/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.launcher.cli;

import com.google.common.collect.Lists;
import net.rubygrapefruit.platform.WindowsRegistry;
import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.internal.provider.DefaultProviderFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.jvm.inspection.CachingJvmMetadataDetector;
import org.gradle.internal.jvm.inspection.DefaultJvmMetadataDetector;
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata;
import org.gradle.internal.jvm.inspection.JvmInstallationMetadataComparator;
import org.gradle.internal.jvm.inspection.JvmMetadataDetector;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.jvm.toolchain.internal.AsdfInstallationSupplier;
import org.gradle.jvm.toolchain.internal.CurrentInstallationSupplier;
import org.gradle.jvm.toolchain.internal.EnvironmentVariableListInstallationSupplier;
import org.gradle.jvm.toolchain.internal.InstallationSupplier;
import org.gradle.jvm.toolchain.internal.IntellijInstallationSupplier;
import org.gradle.jvm.toolchain.internal.JabbaInstallationSupplier;
import org.gradle.jvm.toolchain.internal.JavaInstallationRegistry;
import org.gradle.jvm.toolchain.internal.LinuxInstallationSupplier;
import org.gradle.jvm.toolchain.internal.LocationListInstallationSupplier;
import org.gradle.jvm.toolchain.internal.MavenToolchainsInstallationSupplier;
import org.gradle.jvm.toolchain.internal.OsXInstallationSupplier;
import org.gradle.jvm.toolchain.internal.SdkmanInstallationSupplier;
import org.gradle.jvm.toolchain.internal.WindowsInstallationSupplier;
import org.gradle.process.internal.ExecFactory;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Daemon JVM auto-detection implementation for use in the launcher.
 */
public class DaemonJvmSelector {

    private final ExecFactory execHandleFactory;
    private final WindowsRegistry windowsRegistry;
    private final JvmMetadataDetector detector;

    public DaemonJvmSelector(
        ExecFactory execHandleFactory,
        TemporaryFileProvider temporaryFileProvider,
        WindowsRegistry windowsRegistry
    ) {
        this.execHandleFactory = execHandleFactory;
        this.windowsRegistry = windowsRegistry;

        // TODO: This cache probably doesn't do much in the launcher. We should be caching something to disk here so ideally we
        // can skip the entire detection process
        this.detector = new CachingJvmMetadataDetector(
            new DefaultJvmMetadataDetector(execHandleFactory, temporaryFileProvider));
    }

    public JvmInstallationMetadata getDaemonJvmInstallation(int version, Parameters parameters) {
        Optional<JvmInstallationMetadata> installation = getInstallation(
            parameters,
            it -> it.getLanguageVersion().isCompatibleWith(JavaVersion.toVersion(version))
        );
        if (!installation.isPresent()) {
            throw new GradleException("No valid JVM installation found compatible with Java " + version + ".");
        }
        return installation.get();
    }

    private Optional<JvmInstallationMetadata> getInstallation(Parameters parameters, Predicate<? super JvmInstallationMetadata> criteria) {

        List<InstallationSupplier> installationSuppliers = getInstallationSuppliers(
            new BasicProviderFactory(parameters), execHandleFactory, windowsRegistry);
        JavaInstallationRegistry registry = new JavaInstallationRegistry(installationSuppliers, null, OperatingSystem.current());

        // TODO: What are the performance implications of doing this in the launcher?
        // Probably not good.
        return registry.listInstallations().stream()
            .map(detector::getMetadata)
            .filter(JvmInstallationMetadata::isValidInstallation)
            .filter(criteria)
            .min(new JvmInstallationMetadataComparator(Jvm.current().getJavaHome()));
    }

    // TODO: We should standardize our installation suppliers across AvailableJavaHomes, this, and PlatformJvmServices.
    private static List<InstallationSupplier> getInstallationSuppliers(ProviderFactory providerFactory, ExecFactory execFactory, WindowsRegistry windowsRegistry) {

        // TODO: Also leverage the AutoInstalledInstallationSupplier by moving it to a
        // subproject accessible from here. This will require moving a few other things around.

        FileResolver resolver = new IdentityFileResolver();
        return Lists.newArrayList(
            new AsdfInstallationSupplier(providerFactory),
            new CurrentInstallationSupplier(providerFactory),
            new EnvironmentVariableListInstallationSupplier(providerFactory, resolver),
            new IntellijInstallationSupplier(providerFactory, resolver),
            new JabbaInstallationSupplier(providerFactory),
            new LinuxInstallationSupplier(providerFactory),
            new LocationListInstallationSupplier(providerFactory, resolver),
            new MavenToolchainsInstallationSupplier(providerFactory, resolver),
            new OsXInstallationSupplier(execFactory, providerFactory, OperatingSystem.current()),
            new SdkmanInstallationSupplier(providerFactory),
            new WindowsInstallationSupplier(windowsRegistry, OperatingSystem.current(), providerFactory)
        );
    }

    /**
     * A {@link org.gradle.api.provider.ProviderFactory} which only provides
     * environment variables, system properties, and Gradle properties. A fully featured
     * provider factory is not otherwise available at this service scope.
     */
    private static class BasicProviderFactory extends DefaultProviderFactory {

        private final Parameters parameters;

        public BasicProviderFactory(Parameters parameters) {
            this.parameters = parameters;
        }

        @Override
        public Provider<String> environmentVariable(Provider<String> variableName) {
            return variableName.map(System::getenv);
        }

        @Override
        public Provider<String> systemProperty(Provider<String> propertyName) {
            return propertyName.map(this::getProperty);
        }

        @Override
        public Provider<String> gradleProperty(Provider<String> propertyName) {
            return propertyName.map(this::getProperty);
        }

        @Nullable
        private String getProperty(String key) {
            String val = System.getProperty(key);
            if (val != null) {
                return val;
            }
            val = parameters.getStartParameter().getProjectProperties().get(key);
            if (val != null) {
                return val;
            }
            return parameters.getStartParameter().getSystemPropertiesArgs().get(key);
        }
    }

}
