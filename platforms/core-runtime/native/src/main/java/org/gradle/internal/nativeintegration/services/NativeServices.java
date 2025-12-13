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
package org.gradle.internal.nativeintegration.services;

import com.google.common.annotations.VisibleForTesting;
import net.rubygrapefruit.platform.Native;
import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.NativeIntegration;
import net.rubygrapefruit.platform.NativeIntegrationUnavailableException;
import net.rubygrapefruit.platform.Process;
import net.rubygrapefruit.platform.ProcessLauncher;
import net.rubygrapefruit.platform.SystemInfo;
import net.rubygrapefruit.platform.WindowsRegistry;
import net.rubygrapefruit.platform.file.FileSystems;
import net.rubygrapefruit.platform.file.Files;
import net.rubygrapefruit.platform.file.PosixFiles;
import net.rubygrapefruit.platform.internal.DefaultProcessLauncher;
import net.rubygrapefruit.platform.memory.Memory;
import net.rubygrapefruit.platform.terminal.Terminals;
import org.gradle.api.JavaVersion;
import org.gradle.api.internal.file.temp.GradleUserHomeTemporaryFileProvider;
import org.gradle.fileevents.FileEvents;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.internal.Cast;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.file.FileMetadataAccessor;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.nativeintegration.NativeCapabilities;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.internal.nativeintegration.console.ConsoleDetector;
import org.gradle.internal.nativeintegration.console.FallbackConsoleDetector;
import org.gradle.internal.nativeintegration.console.NativePlatformConsoleDetector;
import org.gradle.internal.nativeintegration.console.TestOverrideConsoleDetector;
import org.gradle.internal.nativeintegration.console.WindowsConsoleDetector;
import org.gradle.internal.nativeintegration.filesystem.services.FallbackFileMetadataAccessor;
import org.gradle.internal.nativeintegration.filesystem.services.FileSystemServices;
import org.gradle.internal.nativeintegration.filesystem.services.NativePlatformBackedFileMetadataAccessor;
import org.gradle.internal.nativeintegration.filesystem.services.UnavailablePosixFiles;
import org.gradle.internal.nativeintegration.jansi.JansiBootPathConfigurer;
import org.gradle.internal.nativeintegration.jna.UnsupportedEnvironment;
import org.gradle.internal.nativeintegration.network.HostnameLookup;
import org.gradle.internal.nativeintegration.processenvironment.NativePlatformBackedProcessEnvironment;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceCreationException;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.util.internal.VersionNumber;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.gradle.internal.nativeintegration.filesystem.services.JdkFallbackHelper.newInstanceOrFallback;

/**
 * Provides various native platform integration services.
 */
public class NativeServices implements ServiceRegistrationProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(NativeServices.class);
    private static NativeServices instance;

    // TODO All this should be static
    private static final JansiBootPathConfigurer JANSI_BOOT_PATH_CONFIGURER = new JansiBootPathConfigurer();

    public static final String NATIVE_SERVICES_OPTION = "org.gradle.native";
    public static final String NATIVE_DIR_OVERRIDE = "org.gradle.native.dir";

    private final boolean useNativeIntegrations;
    private final File userHomeDir;

    private final Native nativeIntegration;
    private final EnumSet<NativeFeatures> enabledFeatures = EnumSet.noneOf(NativeFeatures.class);

    private final ServiceRegistry services;

    public enum NativeFeatures {
        FILE_SYSTEM_WATCHING {
            @Override
            public boolean initialize(File nativeBaseDir, ServiceRegistryBuilder builder, boolean useNativeIntegrations) {
                if (!useNativeIntegrations) {
                    return false;
                }
                OperatingSystem operatingSystem = OperatingSystem.current();
                if (operatingSystem.isMacOsX()) {
                    String version = operatingSystem.getVersion();
                    if (VersionNumber.parse(version).getMajor() < 12) {
                        LOGGER.info("Disabling file system watching on macOS {}, as it is only supported for macOS 12+", version);
                        return false;
                    }
                }
                try {
                    final FileEvents fileEvents = FileEvents.init(nativeBaseDir);
                    LOGGER.info("Initialized file system watching services in: {}", nativeBaseDir);
                    builder.provider(new ServiceRegistrationProvider() {
                        @Provides
                        FileEventFunctionsProvider createFileEventFunctionsProvider() {
                            return new FileEventFunctionsProvider() {
                                @Override
                                public <T extends NativeIntegration> T getFunctions(Class<T> type) {
                                    if (fileEvents != null) {
                                        return fileEvents.get(type);
                                    } else {
                                        throw new NativeIntegrationUnavailableException("File events are not available.");
                                    }
                                }
                            };
                        }
                    });
                    return true;
                } catch (NativeIntegrationUnavailableException ex) {
                    logFileSystemWatchingUnavailable(ex);
                }
                return false;
            }

            @Override
            public void doWhenDisabled(ServiceRegistryBuilder builder) {
                // We still need to provide an implementation of FileEventFunctionsProvider,
                // even if file watching is disabled, otherwise the service registry will throw an exception for a missing service.
                builder.provider(new ServiceRegistrationProvider() {
                    @Provides
                    FileEventFunctionsProvider createFileEventFunctionsProvider() {
                        return new FileEventFunctionsProvider() {
                            @Override
                            public <T extends NativeIntegration> T getFunctions(Class<T> type) {
                                throw new UnsupportedOperationException("File system watching is disabled.");
                            }
                        };
                    }
                });
            }
        },
        JANSI {
            @Override
            public boolean initialize(File nativeBaseDir, ServiceRegistryBuilder builder, boolean useNativeIntegrations) {
                JANSI_BOOT_PATH_CONFIGURER.configure(nativeBaseDir);
                LOGGER.info("Initialized jansi services in: {}", nativeBaseDir);
                return true;
            }
            @Override
            public void doWhenDisabled(ServiceRegistryBuilder builder) {
            }
        };

        public abstract boolean initialize(File nativeBaseDir, ServiceRegistryBuilder builder, boolean canUseNativeIntegrations);
        public abstract void doWhenDisabled(ServiceRegistryBuilder builder);
    }

    public enum NativeServicesMode {
        ENABLED {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public boolean isPotentiallyEnabled() {
                return true;
            }
        },
        DISABLED {
            @Override
            public boolean isEnabled() {
                return false;
            }

            @Override
            public boolean isPotentiallyEnabled() {
                return false;
            }
        },
        NOT_SET {
            @Override
            public boolean isEnabled() {
                throw new UnsupportedOperationException("Cannot determine if native services are enabled or not for " + this + " mode.");
            }

            @Override
            public boolean isPotentiallyEnabled() {
                return true;
            }
        };

        public abstract boolean isEnabled();

        /**
         * Check if the native services might be enabled. This is used to determine if a process needs to be started with flags that allow native access.
         *
         * <p>
         * This is used instead of looking at all possible sources of system properties to determine if the native services would be used, as that would be expensive and complicated.
         * This could result in a process being started with flags that allow native access when it's not needed by Gradle.
         * As it's likely that the native services are enabled, this trade-off is acceptable.
         * </p>
         *
         * @return {@code true} if the native services might be enabled, {@code false} otherwise
         */
        public abstract boolean isPotentiallyEnabled();

        public static NativeServicesMode from(boolean isEnabled) {
            return isEnabled ? ENABLED : DISABLED;
        }

        public static NativeServicesMode fromSystemProperties() {
            return fromString(System.getProperty(NATIVE_SERVICES_OPTION));
        }

        public static NativeServicesMode fromProperties(Map<String, String> properties) {
            return fromString(properties.get(NATIVE_SERVICES_OPTION));
        }

        public static NativeServicesMode fromString(@Nullable String value) {
            // Default to enabled, make it disabled only if explicitly set to "false"
            value = (value == null ? "true" : value).trim();
            return from(!"false".equalsIgnoreCase(value));
        }
    }

    /**
     * Initializes the native services to use the given user home directory to store native libs and other resources. Does nothing if already initialized.
     *
     * Initializes all the services needed for the Gradle daemon.
     */
    public static void initializeOnDaemon(File userHomeDir, NativeServicesMode mode) {
        initialize(userHomeDir, EnumSet.allOf(NativeFeatures.class), mode);
    }

    /**
     * Initializes the native services to use the given user home directory to store native libs and other resources. Does nothing if already initialized.
     *
     * Initializes all the services needed for the CLI or the Tooling API.
     */
    public static void initializeOnClient(File userHomeDir, NativeServicesMode mode) {
        initialize(userHomeDir, EnumSet.of(NativeFeatures.JANSI), mode);
    }

    /**
     * Initializes the native services to use the given user home directory to store native libs and other resources. Does nothing if already initialized.
     *
     * Initializes all the services needed for the CLI or the Tooling API.
     */
    public static void initializeOnWorker(File userHomeDir, NativeServicesMode mode) {
        initialize(userHomeDir, EnumSet.noneOf(NativeFeatures.class), mode);
    }

    public static void logFileSystemWatchingUnavailable(NativeIntegrationUnavailableException ex) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("File system watching is not available", ex);
        } else {
            LOGGER.info("File system watching is not available: {}", ex.getMessage());
        }
    }

    /**
     * Initializes the native services to use the given user home directory to store native libs and other resources. Does nothing if already initialized.
     *
     * @param requestedFeatures Whether to initialize additional native libraries like jansi and file-events.
     */
    private static void initialize(File userHomeDir, EnumSet<NativeFeatures> requestedFeatures, NativeServicesMode mode) {
        checkNativeServicesMode(mode);
        if (instance == null) {
            try {
                instance = new NativeServices(userHomeDir, requestedFeatures, mode);
            } catch (RuntimeException e) {
                throw new ServiceCreationException("Could not initialize native services.", e);
            }
        }
    }

    private static void checkNativeServicesMode(NativeServicesMode mode) {
        if (mode != NativeServicesMode.ENABLED && mode != NativeServicesMode.DISABLED) {
            throw new IllegalArgumentException("Only explicit ENABLED or DISABLED mode is allowed for the NativeServices initialization, but was: " + mode);
        }
    }

    private NativeServices(File userHomeDir, EnumSet<NativeFeatures> requestedFeatures, NativeServicesMode mode) {
        this.userHomeDir = userHomeDir;

        boolean useNativeIntegrations = mode.isEnabled();
        Native nativeIntegration = null;
        File nativeBaseDir = getNativeServicesDir(userHomeDir).getAbsoluteFile();
        if (useNativeIntegrations) {
            try {
                nativeIntegration = Native.init(nativeBaseDir);
            } catch (NativeIntegrationUnavailableException ex) {
                LOGGER.debug("Native-platform is not available.", ex);
                useNativeIntegrations = false;
            } catch (NativeException ex) {
                if (ex.getCause() instanceof UnsatisfiedLinkError && ex.getCause().getMessage().toLowerCase(Locale.ROOT).contains("already loaded in another classloader")) {
                    LOGGER.debug("Unable to initialize native-platform. Failure: {}", format(ex));
                    useNativeIntegrations = false;
                } else if (ex.getMessage().equals("Could not extract native JNI library.")
                    && ex.getCause().getMessage().contains("native-platform.dll (The process cannot access the file because it is being used by another process)")) {
                    //triggered through tooling API of Gradle <2.3 - native-platform.dll is shared by tooling client (<2.3) and daemon (current) and it is locked by the client (<2.3 issue)
                    LOGGER.debug("Unable to initialize native-platform. Failure: {}", format(ex));
                    useNativeIntegrations = false;
                } else {
                    throw ex;
                }
            }
            LOGGER.info("Initialized native services in: {}", nativeBaseDir);
        }
        this.useNativeIntegrations = useNativeIntegrations;
        this.nativeIntegration = nativeIntegration;

        ServiceRegistryBuilder builder = ServiceRegistryBuilder.builder()
            .displayName("native services")
            .provider(new FileSystemServices())
            .provider(this)
            .provider(new ServiceRegistrationProvider() {
                @SuppressWarnings("unused")
                public void configure(ServiceRegistration registration) {
                    registration.add(GradleUserHomeTemporaryFileProvider.class);
                }
            });

        for (NativeFeatures nativeFeature : NativeFeatures.values()) {
            if (requestedFeatures.contains(nativeFeature) && nativeFeature.initialize(nativeBaseDir, builder, useNativeIntegrations)) {
                enabledFeatures.add(nativeFeature);
            } else {
                nativeFeature.doWhenDisabled(builder);
            }
        }

        this.services = builder.build();
    }

    private boolean isFeatureEnabled(NativeFeatures feature) {
        return enabledFeatures.contains(feature);
    }

    private static File getNativeServicesDir(File userHomeDir) {
        String overrideProperty = getNativeDirOverride();
        if (overrideProperty == null) {
            return new File(userHomeDir, "native");
        } else {
            return new File(overrideProperty);
        }
    }

    @Nullable
    private static String getNativeDirOverride() {
        return System.getProperty(NATIVE_DIR_OVERRIDE, System.getenv(NATIVE_DIR_OVERRIDE));
    }

    public static synchronized ServiceRegistry getInstance() {
        if (instance == null) {
            // If this occurs while running gradle or running integration tests, it is indicative of a problem.
            // If this occurs while running unit tests, then either use the NativeServicesTestFixture or the '@UsesNativeServices' annotation.
            throw new IllegalStateException("Cannot get an instance of NativeServices without first calling initialize().");
        }
        return instance.services;
    }

    @VisibleForTesting
    protected static synchronized Native getNative() {
        return checkNotNull(instance).nativeIntegration;
    }

    @Provides
    protected GradleUserHomeDirProvider createGradleUserHomeDirProvider() {
        return new GradleUserHomeDirProvider() {
            @Override
            public File getGradleUserHomeDirectory() {
                return userHomeDir;
            }
        };
    }

    @Provides
    protected OperatingSystem createOperatingSystem() {
        return OperatingSystem.current();
    }

    @Provides
    protected Jvm createJvm() {
        return Jvm.current();
    }

    @Provides
    protected ProcessEnvironment createProcessEnvironment(OperatingSystem operatingSystem) {
        if (useNativeIntegrations) {
            try {
                net.rubygrapefruit.platform.Process process = nativeIntegration.get(Process.class);
                return new NativePlatformBackedProcessEnvironment(process);
            } catch (NativeIntegrationUnavailableException ex) {
                LOGGER.debug("Native-platform process integration is not available. Continuing with fallback.");
            }
        }

        return new UnsupportedEnvironment();
    }

    @Provides
    protected ConsoleDetector createConsoleDetector(OperatingSystem operatingSystem) {
        return new TestOverrideConsoleDetector(backingConsoleDetector(operatingSystem));
    }

    private ConsoleDetector backingConsoleDetector(OperatingSystem operatingSystem) {
        if (useNativeIntegrations) {
            try {
                Terminals terminals = nativeIntegration.get(Terminals.class);
                return new NativePlatformConsoleDetector(terminals);
            } catch (NativeIntegrationUnavailableException ex) {
                LOGGER.debug("Native-platform terminal integration is not available. Continuing with fallback.");
            } catch (NativeException ex) {
                LOGGER.debug("Unable to load from native-platform backed ConsoleDetector. Continuing with fallback. Failure: {}", format(ex));
            }

            try {
                if (operatingSystem.isWindows()) {
                    return new WindowsConsoleDetector();
                }
            } catch (LinkageError e) {
                // Thrown when jna cannot initialize the native stuff
                LOGGER.debug("Unable to load native library. Continuing with fallback. Failure: {}", format(e));
            }
        }

        return new FallbackConsoleDetector();
    }

    @Provides
    protected WindowsRegistry createWindowsRegistry(OperatingSystem operatingSystem) {
        if (useNativeIntegrations && operatingSystem.isWindows()) {
            return nativeIntegration.get(WindowsRegistry.class);
        }
        return notAvailable(WindowsRegistry.class, operatingSystem);
    }

    @Provides
    public SystemInfo createSystemInfo(OperatingSystem operatingSystem) {
        if (useNativeIntegrations) {
            try {
                return nativeIntegration.get(SystemInfo.class);
            } catch (NativeIntegrationUnavailableException e) {
                LOGGER.debug("Native-platform system info is not available. Continuing with fallback.");
            }
        }
        return notAvailable(SystemInfo.class, operatingSystem);
    }

    @Provides
    protected Memory createMemory(OperatingSystem operatingSystem) {
        if (useNativeIntegrations) {
            try {
                return nativeIntegration.get(Memory.class);
            } catch (NativeIntegrationUnavailableException e) {
                LOGGER.debug("Native-platform memory integration is not available. Continuing with fallback.");
            }
        }
        return notAvailable(Memory.class, operatingSystem);
    }

    @Provides
    protected ProcessLauncher createProcessLauncher() {
        if (useNativeIntegrations) {
            try {
                return nativeIntegration.get(ProcessLauncher.class);
            } catch (NativeIntegrationUnavailableException e) {
                LOGGER.debug("Native-platform process launcher is not available. Continuing with fallback.");
            }
        }
        return new DefaultProcessLauncher();
    }

    @Provides
    protected PosixFiles createPosixFiles(OperatingSystem operatingSystem) {
        if (useNativeIntegrations) {
            try {
                return nativeIntegration.get(PosixFiles.class);
            } catch (NativeIntegrationUnavailableException e) {
                LOGGER.debug("Native-platform posix files integration is not available. Continuing with fallback.");
            }
        }
        return notAvailable(UnavailablePosixFiles.class, operatingSystem);
    }

    @Provides
    protected HostnameLookup createHostnameLookup() {
        if (useNativeIntegrations) {
            try {
                String hostname = nativeIntegration.get(SystemInfo.class).getHostname();
                return new FixedHostname(hostname);
            } catch (NativeIntegrationUnavailableException e) {
                LOGGER.debug("Native-platform posix files integration is not available. Continuing with fallback.");
            }
        }
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            //noinspection Since15
            hostname = InetAddress.getLoopbackAddress().getHostAddress();
        }
        return new FixedHostname(hostname);
    }

    @Provides
    protected FileMetadataAccessor createFileMetadataAccessor(OperatingSystem operatingSystem) {
        // Based on the benchmark found in org.gradle.internal.nativeintegration.filesystem.FileMetadataAccessorBenchmark
        // and the results in the PR https://github.com/gradle/gradle/pull/12966
        // we're using "native platform" for all OSes if available.
        // If it isn't available, we fall back to using Java NIO and, if that fails, to using the old `File` APIs.

        if (useNativeIntegrations) {
            try {
                return new NativePlatformBackedFileMetadataAccessor(nativeIntegration.get(Files.class));
            } catch (NativeIntegrationUnavailableException e) {
                LOGGER.debug("Native-platform files integration is not available. Continuing with fallback.");
            }
        }

        if (JavaVersion.current().isJava7Compatible()) {
            return newInstanceOrFallback("org.gradle.internal.file.nio.NioFileMetadataAccessor", NativeServices.class.getClassLoader(), FallbackFileMetadataAccessor.class);
        }

        return new FallbackFileMetadataAccessor();
    }

    @Provides
    public NativeCapabilities createNativeCapabilities() {
        return new NativeCapabilities() {
            @Override
            public boolean useNativeIntegrations() {
                return useNativeIntegrations;
            }

            @Override
            public boolean useFileSystemWatching() {
                return isFeatureEnabled(NativeFeatures.FILE_SYSTEM_WATCHING);
            }
        };
    }

    @Provides
    protected FileSystems createFileSystems(OperatingSystem operatingSystem) {
        if (useNativeIntegrations) {
            try {
                return nativeIntegration.get(FileSystems.class);
            } catch (NativeIntegrationUnavailableException e) {
                LOGGER.debug("Native-platform file systems information is not available. Continuing with fallback.");
            }
        }
        return notAvailable(FileSystems.class, operatingSystem);
    }

    private <T> T notAvailable(Class<T> type, OperatingSystem operatingSystem) {
        return Cast.uncheckedNonnullCast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, new BrokenService(type.getSimpleName(), useNativeIntegrations, operatingSystem)));
    }

    private static String format(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        builder.append(throwable);
        for (Throwable current = throwable.getCause(); current != null; current = current.getCause()) {
            builder.append(SystemProperties.getInstance().getLineSeparator());
            builder.append("caused by: ");
            builder.append(current);
        }
        return builder.toString();
    }

    private static class BrokenService implements InvocationHandler {
        private final String type;
        private final boolean useNativeIntegrations;
        private final OperatingSystem operatingSystem;

        private BrokenService(String type, boolean useNativeIntegrations, OperatingSystem operatingSystem) {
            this.type = type;
            this.useNativeIntegrations = useNativeIntegrations;
            this.operatingSystem = operatingSystem;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            throw new org.gradle.internal.nativeintegration.NativeIntegrationUnavailableException(String.format("Service '%s' is not available (os=%s, enabled=%s).", type, operatingSystem, useNativeIntegrations));
        }
    }

    private static class FixedHostname implements HostnameLookup {
        private final String hostname;

        public FixedHostname(String hostname) {
            this.hostname = hostname;
        }

        @Override
        public String getHostname() {
            return hostname;
        }
    }

    public interface FileEventFunctionsProvider {
        <T extends NativeIntegration> T getFunctions(Class<T> type);
    }
}
