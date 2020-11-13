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

import net.rubygrapefruit.platform.Native;
import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.NativeIntegrationUnavailableException;
import net.rubygrapefruit.platform.Process;
import net.rubygrapefruit.platform.ProcessLauncher;
import net.rubygrapefruit.platform.SystemInfo;
import net.rubygrapefruit.platform.WindowsRegistry;
import net.rubygrapefruit.platform.file.FileEvents;
import net.rubygrapefruit.platform.file.Files;
import net.rubygrapefruit.platform.file.PosixFiles;
import net.rubygrapefruit.platform.internal.DefaultProcessLauncher;
import net.rubygrapefruit.platform.memory.Memory;
import net.rubygrapefruit.platform.terminal.Terminals;
import org.gradle.api.JavaVersion;
import org.gradle.internal.Cast;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.nativeintegration.NativeCapabilities;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.internal.nativeintegration.console.ConsoleDetector;
import org.gradle.internal.nativeintegration.console.FallbackConsoleDetector;
import org.gradle.internal.nativeintegration.console.NativePlatformConsoleDetector;
import org.gradle.internal.nativeintegration.console.TestOverrideConsoleDetector;
import org.gradle.internal.nativeintegration.console.WindowsConsoleDetector;
import org.gradle.internal.nativeintegration.filesystem.FileMetadataAccessor;
import org.gradle.internal.nativeintegration.filesystem.services.FallbackFileMetadataAccessor;
import org.gradle.internal.nativeintegration.filesystem.services.FileSystemServices;
import org.gradle.internal.nativeintegration.filesystem.services.NativePlatformBackedFileMetadataAccessor;
import org.gradle.internal.nativeintegration.filesystem.services.UnavailablePosixFiles;
import org.gradle.internal.nativeintegration.jansi.JansiBootPathConfigurer;
import org.gradle.internal.nativeintegration.jna.UnsupportedEnvironment;
import org.gradle.internal.nativeintegration.network.HostnameLookup;
import org.gradle.internal.nativeintegration.processenvironment.NativePlatformBackedProcessEnvironment;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceCreationException;
import org.gradle.internal.service.ServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.gradle.internal.nativeintegration.filesystem.services.JdkFallbackHelper.newInstanceOrFallback;

/**
 * Provides various native platform integration services.
 */
public class NativeServices extends DefaultServiceRegistry implements ServiceRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(NativeServices.class);
    private static boolean useNativeIntegrations;
    private static boolean useFileSystemWatching;
    private static final NativeServices INSTANCE = new NativeServices();
    private static final JansiBootPathConfigurer JANSI_BOOT_PATH_CONFIGURER = new JansiBootPathConfigurer();
    private static boolean initialized;

    public static final String NATIVE_DIR_OVERRIDE = "org.gradle.native.dir";

    /**
     * Initializes the native services to use the given user home directory to store native libs and other resources. Does nothing if already initialized.
     */
    public static void initialize(File userHomeDir) {
        initialize(userHomeDir, true);
    }

    /**
     * Initializes the native services to use the given user home directory to store native libs and other resources. Does nothing if already initialized.
     *
     * @param initializeAdditionalNativeLibraries Whether to initialize additional native libraries like jansi and file-events.
     */
    public static synchronized void initialize(File userHomeDir, boolean initializeAdditionalNativeLibraries) {
        try {
            if (!initialized) {
                useNativeIntegrations = "true".equalsIgnoreCase(System.getProperty("org.gradle.native", "true"));
                if (useNativeIntegrations) {
                    File nativeBaseDir = getNativeServicesDir(userHomeDir).getAbsoluteFile();
                    try {
                        net.rubygrapefruit.platform.Native.init(nativeBaseDir);
                    } catch (NativeIntegrationUnavailableException ex) {
                        LOGGER.debug("Native-platform is not available.");
                        useNativeIntegrations = false;
                    } catch (NativeException ex) {
                        if (ex.getCause() instanceof UnsatisfiedLinkError && ex.getCause().getMessage().toLowerCase().contains("already loaded in another classloader")) {
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
                    if (initializeAdditionalNativeLibraries) {
                        if (useNativeIntegrations) {
                            useFileSystemWatching = true;
                            try {
                                FileEvents.init(nativeBaseDir);
                            } catch (NativeIntegrationUnavailableException ex) {
                                LOGGER.debug("Native file system watching is not available for this operating system.", ex);
                                useFileSystemWatching = false;
                            }
                        }
                        JANSI_BOOT_PATH_CONFIGURER.configure(nativeBaseDir);
                    }
                    LOGGER.info("Initialized native services in: " + nativeBaseDir);
                }
                initialized = true;
            }
        } catch (RuntimeException e) {
            throw new ServiceCreationException("Could not initialize native services.", e);
        }
    }

    public static File getNativeServicesDir(File userHomeDir) {
        String overrideProperty = getNativeDirOverride();
        if (overrideProperty == null) {
            return new File(userHomeDir, "native");
        } else {
            return new File(overrideProperty);
        }
    }

    private static String getNativeDirOverride() {
        return System.getProperty(NATIVE_DIR_OVERRIDE, System.getenv(NATIVE_DIR_OVERRIDE));
    }

    public static synchronized NativeServices getInstance() {
        if (!initialized) {
            // If this occurs while running gradle or running integration tests, it is indicative of a problem.
            // If this occurs while running unit tests, then either use the NativeServicesTestFixture or the '@UsesNativeServices' annotation.
            throw new IllegalStateException("Cannot get an instance of NativeServices without first calling initialize().");
        }
        return INSTANCE;
    }

    private NativeServices() {
        addProvider(new FileSystemServices());
    }

    @Override
    public void close() {
        // Don't close
    }

    protected OperatingSystem createOperatingSystem() {
        return OperatingSystem.current();
    }

    protected Jvm createJvm() {
        return Jvm.current();
    }

    protected ProcessEnvironment createProcessEnvironment(OperatingSystem operatingSystem) {
        if (useNativeIntegrations) {
            try {
                net.rubygrapefruit.platform.Process process = net.rubygrapefruit.platform.Native.get(Process.class);
                return new NativePlatformBackedProcessEnvironment(process);
            } catch (NativeIntegrationUnavailableException ex) {
                LOGGER.debug("Native-platform process integration is not available. Continuing with fallback.");
            }
        }

        return new UnsupportedEnvironment();
    }

    protected ConsoleDetector createConsoleDetector(OperatingSystem operatingSystem) {
        return new TestOverrideConsoleDetector(backingConsoleDetector(operatingSystem));
    }

    private ConsoleDetector backingConsoleDetector(OperatingSystem operatingSystem) {
        if (useNativeIntegrations) {
            try {
                Terminals terminals = net.rubygrapefruit.platform.Native.get(Terminals.class);
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

    protected WindowsRegistry createWindowsRegistry(OperatingSystem operatingSystem) {
        if (useNativeIntegrations && operatingSystem.isWindows()) {
            return net.rubygrapefruit.platform.Native.get(WindowsRegistry.class);
        }
        return notAvailable(WindowsRegistry.class);
    }

    protected SystemInfo createSystemInfo() {
        if (useNativeIntegrations) {
            try {
                return net.rubygrapefruit.platform.Native.get(SystemInfo.class);
            } catch (NativeIntegrationUnavailableException e) {
                LOGGER.debug("Native-platform system info is not available. Continuing with fallback.");
            }
        }
        return notAvailable(SystemInfo.class);
    }

    protected Memory createMemory() {
        if (useNativeIntegrations) {
            try {
                return net.rubygrapefruit.platform.Native.get(Memory.class);
            } catch (NativeIntegrationUnavailableException e) {
                LOGGER.debug("Native-platform memory integration is not available. Continuing with fallback.");
            }
        }
        return notAvailable(Memory.class);
    }

    protected ProcessLauncher createProcessLauncher() {
        if (useNativeIntegrations) {
            try {
                return net.rubygrapefruit.platform.Native.get(ProcessLauncher.class);
            } catch (NativeIntegrationUnavailableException e) {
                LOGGER.debug("Native-platform process launcher is not available. Continuing with fallback.");
            }
        }
        return new DefaultProcessLauncher();
    }

    protected PosixFiles createPosixFiles() {
        if (useNativeIntegrations) {
            try {
                return net.rubygrapefruit.platform.Native.get(PosixFiles.class);
            } catch (NativeIntegrationUnavailableException e) {
                LOGGER.debug("Native-platform posix files integration is not available. Continuing with fallback.");
            }
        }
        return notAvailable(UnavailablePosixFiles.class);
    }

    protected HostnameLookup createHostnameLookup() {
        if (useNativeIntegrations) {
            try {
                String hostname = Native.get(SystemInfo.class).getHostname();
                return new FixedHostname(hostname);
            } catch (NativeIntegrationUnavailableException e) {
                LOGGER.debug("Native-platform posix files integration is not available. Continuing with fallback.");
            }
        }
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostname = InetAddress.getLoopbackAddress().getHostAddress();
        }
        return new FixedHostname(hostname);
    }

    protected FileMetadataAccessor createFileMetadataAccessor(OperatingSystem operatingSystem) {
        // Based on the benchmark found in org.gradle.internal.nativeintegration.filesystem.FileMetadataAccessorBenchmark
        // and the results in the PR https://github.com/gradle/gradle/pull/12966
        // we're using "native platform" for all OSes if available.
        // If it isn't available, we fall back to using Java NIO and, if that fails, to using the old `File` APIs.

        if (useNativeIntegrations) {
            try {
                return new NativePlatformBackedFileMetadataAccessor(net.rubygrapefruit.platform.Native.get(Files.class));
            } catch (NativeIntegrationUnavailableException e) {
                LOGGER.debug("Native-platform files integration is not available. Continuing with fallback.");
            }
        }

        if (JavaVersion.current().isJava7Compatible()) {
            return newInstanceOrFallback("org.gradle.internal.nativeintegration.filesystem.jdk7.NioFileMetadataAccessor", NativeServices.class.getClassLoader(), FallbackFileMetadataAccessor.class);
        }

        return new FallbackFileMetadataAccessor();
    }

    protected NativeCapabilities createNativeCapabilities() {
        return new NativeCapabilities() {
            @Override
            public boolean useNativeIntegrations() {
                return useNativeIntegrations;
            }

            @Override
            public boolean useFileSystemWatching() {
                return useFileSystemWatching;
            }
        };
    }

    private <T> T notAvailable(Class<T> type) {
        return Cast.uncheckedNonnullCast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, new BrokenService(type.getSimpleName())));
    }

    private static String format(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        builder.append(throwable.toString());
        for (Throwable current = throwable.getCause(); current != null; current = current.getCause()) {
            builder.append(SystemProperties.getInstance().getLineSeparator());
            builder.append("caused by: ");
            builder.append(current.toString());
        }
        return builder.toString();
    }

    private static class BrokenService implements InvocationHandler {
        private final String type;

        private BrokenService(String type) {
            this.type = type;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            throw new org.gradle.internal.nativeintegration.NativeIntegrationUnavailableException(String.format("%s is not supported on this operating system.", type));
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
}
