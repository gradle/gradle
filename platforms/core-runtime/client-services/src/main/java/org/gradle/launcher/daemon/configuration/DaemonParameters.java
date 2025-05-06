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
import org.gradle.internal.buildconfiguration.tasks.DaemonJvmPropertiesAccessor;
import org.gradle.internal.nativeintegration.services.NativeServices.NativeServicesMode;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JvmImplementation;
import org.gradle.jvm.toolchain.JvmVendorSpec;
import org.gradle.jvm.toolchain.internal.DefaultToolchainConfiguration;
import org.gradle.jvm.toolchain.internal.ToolchainConfiguration;
import org.gradle.launcher.daemon.context.DaemonRequestContext;
import org.gradle.launcher.daemon.toolchain.DaemonJvmCriteria;
import org.gradle.launcher.daemon.toolchain.ToolchainDownloadUrlProvider;
import org.gradle.process.internal.JvmOptions;
import org.gradle.util.internal.GUtil;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DaemonParameters {
    static final int DEFAULT_IDLE_TIMEOUT = 3 * 60 * 60 * 1000;
    public static final int DEFAULT_PERIODIC_CHECK_INTERVAL_MILLIS = 10 * 1000;

    public static final List<String> DEFAULT_JVM_ARGS = ImmutableList.of("-Xmx512m", "-Xms256m", "-XX:MaxMetaspaceSize=384m", "-XX:+HeapDumpOnOutOfMemoryError");

    private final ToolchainConfiguration toolchainConfiguration;
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
    private ToolchainDownloadUrlProvider toolchainDownloadUrlProvider;

    public DaemonParameters(File gradleUserHomeDir, FileCollectionFactory fileCollectionFactory) {
        this(gradleUserHomeDir, fileCollectionFactory, Collections.emptyMap());
    }

    public DaemonParameters(File gradleUserHomeDir, FileCollectionFactory fileCollectionFactory, Map<String, String> extraSystemProperties) {
        this(gradleUserHomeDir, fileCollectionFactory, extraSystemProperties, null);
    }

    public DaemonParameters(File gradleUserHomeDir, FileCollectionFactory fileCollectionFactory, Map<String, String> extraSystemProperties, @Nullable Map<String, String> environmentVariables) {
        this.jvmOptions = new JvmOptions(fileCollectionFactory);
        if (!extraSystemProperties.isEmpty()) {
            jvmOptions.systemProperties(extraSystemProperties);
        }
        jvmOptions.jvmArgs(DEFAULT_JVM_ARGS);
        this.gradleUserHomeDir = gradleUserHomeDir;
        this.baseDir = new File(gradleUserHomeDir, "daemon");
        this.envVariables = environmentVariables == null ? new HashMap<>(System.getenv()) : environmentVariables;
        toolchainConfiguration = new DefaultToolchainConfiguration(this.envVariables);
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

    public void setRequestedJvmCriteriaFromMap(@Nullable Map<String, String> daemonJvmProperties) {
        DaemonJvmPropertiesAccessor daemonJvmAccessor = new DaemonJvmPropertiesAccessor(daemonJvmProperties);
        JavaLanguageVersion requestedVersion = daemonJvmAccessor.getVersion();
        if (requestedVersion != null) {
            JvmVendorSpec requestedJavaVendor = daemonJvmAccessor.getVendor();
            this.requestedJvmCriteria = new DaemonJvmCriteria.Spec(requestedVersion, requestedJavaVendor, JvmImplementation.VENDOR_SPECIFIC, daemonJvmAccessor.getNativeImageCapable());
            this.toolchainDownloadUrlProvider = new ToolchainDownloadUrlProvider(daemonJvmAccessor.getToolchainDownloadUrls());
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

    public void addJvmArgs(Iterable<String> jvmArgs) {
        jvmOptions.jvmArgs(jvmArgs);
    }

    public void setJvmArgs(Iterable<String> jvmArgs) {
        jvmOptions.setAllJvmArgs(jvmArgs);
    }

    public void setDebug(boolean debug) {
        jvmOptions.setDebug(debug);
    }

    public void setDebugPort(int debug) {
        jvmOptions.getDebugSpec().setPort(debug);
    }

    public void setDebugHost(String host) {
        jvmOptions.getDebugSpec().setHost(host);
    }

    public void setDebugSuspend(boolean suspend) {
        jvmOptions.getDebugSpec().setSuspend(suspend);
    }

    public void setDebugServer(boolean server) {
        jvmOptions.getDebugSpec().setServer(server);
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

    public ToolchainDownloadUrlProvider getToolchainDownloadUrlProvider() {
        return toolchainDownloadUrlProvider;
    }

    public DaemonPriority getPriority() {
        return priority;
    }

    public void setPriority(DaemonPriority priority) {
        this.priority = priority;
    }
}
