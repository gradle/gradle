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
package org.gradle.launcher.daemon.configuration;

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.internal.buildconfiguration.DaemonJvmPropertiesDefaults;
import org.gradle.internal.jvm.inspection.JvmVendor;
import org.gradle.internal.nativeintegration.services.NativeServices.NativeServicesMode;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JvmImplementation;
import org.gradle.jvm.toolchain.JvmVendorSpec;
import org.gradle.jvm.toolchain.internal.DefaultJavaLanguageVersion;
import org.gradle.jvm.toolchain.internal.DefaultJvmVendorSpec;
import org.gradle.jvm.toolchain.internal.DefaultToolchainConfiguration;
import org.gradle.jvm.toolchain.internal.ToolchainConfiguration;
import org.gradle.launcher.configuration.BuildLayoutResult;
import org.gradle.launcher.daemon.context.DaemonRequestContext;
import org.gradle.launcher.daemon.toolchain.DaemonJvmCriteria;
import org.gradle.process.internal.JvmOptions;
import org.gradle.util.internal.GUtil;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DaemonParameters {
    static final int DEFAULT_IDLE_TIMEOUT = 3 * 60 * 60 * 1000;
    public static final int DEFAULT_PERIODIC_CHECK_INTERVAL_MILLIS = 10 * 1000;

    public static final List<String> DEFAULT_JVM_ARGS = ImmutableList.of("-Xmx512m", "-Xms256m", "-XX:MaxMetaspaceSize=384m", "-XX:+HeapDumpOnOutOfMemoryError");

    private final ToolchainConfiguration toolchainConfiguration = new DefaultToolchainConfiguration();

    private final File gradleUserHomeDir;

    private File baseDir;
    private int idleTimeout = DEFAULT_IDLE_TIMEOUT;

    private int periodicCheckInterval = DEFAULT_PERIODIC_CHECK_INTERVAL_MILLIS;
    private final JvmOptions jvmOptions;
    private boolean applyInstrumentationAgent = true;
    private NativeServicesMode nativeServicesMode = NativeServicesMode.ENABLED;
    private Map<String, String> envVariables;
    private boolean enabled = true;
    private boolean foreground;
    private boolean stop;
    private boolean status;
    private DaemonPriority priority = DaemonPriority.NORMAL;
    private DaemonJvmCriteria requestedJvmCriteria = new DaemonJvmCriteria.LauncherJvm();

    public DaemonParameters(BuildLayoutResult layout, FileCollectionFactory fileCollectionFactory) {
        this(layout, fileCollectionFactory, Collections.<String, String>emptyMap());
    }

    public DaemonParameters(BuildLayoutResult layout, FileCollectionFactory fileCollectionFactory, Map<String, String> extraSystemProperties) {
        jvmOptions = new JvmOptions(fileCollectionFactory);
        if (!extraSystemProperties.isEmpty()) {
            jvmOptions.systemProperties(extraSystemProperties);
        }
        jvmOptions.jvmArgs(DEFAULT_JVM_ARGS);
        baseDir = new File(layout.getGradleUserHomeDir(), "daemon");
        gradleUserHomeDir = layout.getGradleUserHomeDir();
        envVariables = new HashMap<>(System.getenv());
    }

    public DaemonRequestContext toRequestContext() {
        return new DaemonRequestContext(getRequestedJvmCriteria(), getEffectiveJvmArgs(), shouldApplyInstrumentationAgent(), getNativeServicesMode(), getPriority());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public File getBaseDir() {
        return baseDir;
    }

    public File getGradleUserHomeDir() {
        return gradleUserHomeDir;
    }

    public int getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(int idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public int getPeriodicCheckInterval() {
        return periodicCheckInterval;
    }

    public void setPeriodicCheckInterval(int periodicCheckInterval) {
        this.periodicCheckInterval = periodicCheckInterval;
    }

    public List<String> getEffectiveJvmArgs() {
        return jvmOptions.getAllImmutableJvmArgs();
    }

    public DaemonJvmCriteria getRequestedJvmCriteria() {
        return requestedJvmCriteria;
    }

    public void setRequestedJvmCriteria(DaemonJvmCriteria requestedJvmCriteria) {
        this.requestedJvmCriteria = requestedJvmCriteria;
    }

    public void setRequestedJvmCriteriaFromMap(@Nullable Map<String, String> buildProperties) {
        String requestedVersion = buildProperties.get(DaemonJvmPropertiesDefaults.TOOLCHAIN_VERSION_PROPERTY);
        if (requestedVersion != null) {
            JavaLanguageVersion javaVersion;
            try {
                javaVersion = DefaultJavaLanguageVersion.fromFullVersion(requestedVersion);
            } catch (Exception e) {
                // TODO: This should be pushed somewhere else so we consistently report this message in the right context.
                throw new IllegalArgumentException(String.format("Value '%s' given for %s is an invalid Java version", requestedVersion, DaemonJvmPropertiesDefaults.TOOLCHAIN_VERSION_PROPERTY));
            }

            final JvmVendorSpec javaVendor;
            String requestedVendor = buildProperties.get(DaemonJvmPropertiesDefaults.TOOLCHAIN_VENDOR_PROPERTY);

            if (requestedVendor != null) {
                Optional<JvmVendor.KnownJvmVendor> knownVendor =
                    Arrays.stream(JvmVendor.KnownJvmVendor.values()).filter(e -> e.name().equals(requestedVendor)).findFirst();

                if (knownVendor.isPresent() && knownVendor.get()!=JvmVendor.KnownJvmVendor.UNKNOWN) {
                    javaVendor = DefaultJvmVendorSpec.of(knownVendor.get());
                } else {
                    javaVendor = DefaultJvmVendorSpec.matching(requestedVendor);
                }
            } else {
                // match any vendor
                javaVendor = DefaultJvmVendorSpec.any();
            }

            this.requestedJvmCriteria = new DaemonJvmCriteria.Spec(javaVersion, javaVendor, JvmImplementation.VENDOR_SPECIFIC);
        }
    }

    public Map<String, String> getSystemProperties() {
        Map<String, String> systemProperties = new HashMap<String, String>();
        GUtil.addToMap(systemProperties, jvmOptions.getMutableSystemProperties());
        return systemProperties;
    }

    public Map<String, String> getEffectiveSystemProperties() {
        Map<String, String> systemProperties = new HashMap<String, String>();
        GUtil.addToMap(systemProperties, System.getProperties());
        GUtil.addToMap(systemProperties, jvmOptions.getMutableSystemProperties());
        GUtil.addToMap(systemProperties, jvmOptions.getImmutableSystemProperties());
        return systemProperties;
    }

    public Map<String, String> getMutableAndImmutableSystemProperties() {
        Map<String, String> systemProperties = new HashMap<String, String>();
        GUtil.addToMap(systemProperties, jvmOptions.getMutableSystemProperties());
        GUtil.addToMap(systemProperties, jvmOptions.getImmutableSystemProperties());
        return systemProperties;
    }

    public void setJvmArgs(Iterable<String> jvmArgs) {
        jvmOptions.setAllJvmArgs(jvmArgs);
    }

    public void setEnvironmentVariables(Map<String, String> envVariables) {
        this.envVariables = envVariables == null ? new HashMap<String, String>(System.getenv()) : envVariables;
    }

    public void setDebug(boolean debug) {
        jvmOptions.setDebug(debug);
    }

    public void setDebugPort(int debug) {
        jvmOptions.getDebugOptions().setPort(debug);
    }

    public void setDebugHost(String host) {
        jvmOptions.getDebugOptions().setHost(host);
    }

    public void setDebugSuspend(boolean suspend) {
        jvmOptions.getDebugOptions().setSuspend(suspend);
    }

    public void setDebugServer(boolean server) {
        jvmOptions.getDebugOptions().setServer(server);
    }

    public DaemonParameters setBaseDir(File baseDir) {
        this.baseDir = baseDir;
        return this;
    }

    public boolean getDebug() {
        return jvmOptions.getDebug();
    }

    public boolean shouldApplyInstrumentationAgent() {
        return applyInstrumentationAgent;
    }

    public DaemonParameters setApplyInstrumentationAgent(boolean applyInstrumentationAgent) {
        this.applyInstrumentationAgent = applyInstrumentationAgent;
        return this;
    }

    public NativeServicesMode getNativeServicesMode() {
        return nativeServicesMode;
    }

    public DaemonParameters setNativeServicesMode(NativeServicesMode nativeServicesMode) {
        this.nativeServicesMode = nativeServicesMode;
        return this;
    }

    public boolean isForeground() {
        return foreground;
    }

    public void setForeground(boolean foreground) {
        this.foreground = foreground;
    }

    public boolean isStop() {
        return stop;
    }

    public void setStop(boolean stop) {
        this.stop = stop;
    }

    public boolean isStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public Map<String, String> getEnvironmentVariables() {
        return envVariables;
    }

    public ToolchainConfiguration getToolchainConfiguration() {
        return toolchainConfiguration;
    }

    public DaemonPriority getPriority() {
        return priority;
    }

    public void setPriority(DaemonPriority priority) {
        this.priority = priority;
    }
}
