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
import org.gradle.api.JavaVersion;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.internal.jvm.JavaInfo;
import org.gradle.internal.jvm.JpmsConfiguration;
import org.gradle.internal.jvm.Jvm;
import org.gradle.launcher.configuration.BuildLayoutResult;
import org.gradle.util.internal.GUtil;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DaemonParameters {
    static final int DEFAULT_IDLE_TIMEOUT = 3 * 60 * 60 * 1000;
    public static final int DEFAULT_PERIODIC_CHECK_INTERVAL_MILLIS = 10 * 1000;

    public static final List<String> DEFAULT_JVM_ARGS = ImmutableList.of("-Xmx512m", "-Xms256m", "-XX:MaxPermSize=384m", "-XX:+HeapDumpOnOutOfMemoryError");
    public static final List<String> DEFAULT_JVM_8_ARGS = ImmutableList.of("-Xmx512m", "-Xms256m", "-XX:MaxMetaspaceSize=384m", "-XX:+HeapDumpOnOutOfMemoryError");
    public static final List<String> ALLOW_ENVIRONMENT_VARIABLE_OVERWRITE = ImmutableList.of("--add-opens=java.base/java.util=ALL-UNNAMED");

    private final File gradleUserHomeDir;

    private File baseDir;
    private int idleTimeout = DEFAULT_IDLE_TIMEOUT;

    private int periodicCheckInterval = DEFAULT_PERIODIC_CHECK_INTERVAL_MILLIS;
    private final DaemonJvmOptions jvmOptions;
    private boolean applyInstrumentationAgent = true;
    private Map<String, String> envVariables;
    private boolean enabled = true;
    private boolean hasJvmArgs;
    private boolean userDefinedImmutableJvmArgs;
    private boolean foreground;
    private boolean stop;
    private boolean status;
    private Priority priority = Priority.NORMAL;
    private JavaInfo jvm = Jvm.current();

    public DaemonParameters(BuildLayoutResult layout, FileCollectionFactory fileCollectionFactory) {
        this(layout, fileCollectionFactory, Collections.<String, String>emptyMap());
    }

    public DaemonParameters(BuildLayoutResult layout, FileCollectionFactory fileCollectionFactory, Map<String, String> extraSystemProperties) {
        jvmOptions = new DaemonJvmOptions(fileCollectionFactory);
        if (!extraSystemProperties.isEmpty()) {
            List<String> immutableBefore = jvmOptions.getAllImmutableJvmArgs();
            jvmOptions.systemProperties(extraSystemProperties);
            List<String> immutableAfter = jvmOptions.getAllImmutableJvmArgs();
            userDefinedImmutableJvmArgs = !immutableBefore.equals(immutableAfter);
        }
        baseDir = new File(layout.getGradleUserHomeDir(), "daemon");
        gradleUserHomeDir = layout.getGradleUserHomeDir();
        envVariables = new HashMap<>(System.getenv());
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

    public List<String> getEffectiveSingleUseJvmArgs() {
        return jvmOptions.getAllSingleUseImmutableJvmArgs();
    }

    public JavaInfo getEffectiveJvm() {
        return jvm;
    }

    @Nullable
    public DaemonParameters setJvm(JavaInfo jvm) {
        this.jvm = jvm == null ? Jvm.current() : jvm;
        return this;
    }

    public void applyDefaultsFor(JavaVersion javaVersion) {
        if (javaVersion.compareTo(JavaVersion.VERSION_1_9) >= 0) {
            Set<String> jpmsArgs = new LinkedHashSet<>(ALLOW_ENVIRONMENT_VARIABLE_OVERWRITE);
            jpmsArgs.addAll(JpmsConfiguration.GRADLE_DAEMON_JPMS_ARGS);
            jvmOptions.jvmArgs(jpmsArgs);
        }
        if (hasJvmArgs) {
            return;
        }
        if (javaVersion.compareTo(JavaVersion.VERSION_1_8) >= 0) {
            jvmOptions.jvmArgs(DEFAULT_JVM_8_ARGS);
        } else {
            jvmOptions.jvmArgs(DEFAULT_JVM_ARGS);
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
        GUtil.addToMap(systemProperties, jvmOptions.getImmutableDaemonProperties());
        return systemProperties;
    }

    public Map<String, String> getMutableAndImmutableSystemProperties() {
        Map<String, String> systemProperties = new HashMap<String, String>();
        GUtil.addToMap(systemProperties, jvmOptions.getMutableSystemProperties());
        GUtil.addToMap(systemProperties, jvmOptions.getImmutableDaemonProperties());
        return systemProperties;
    }

    public void setJvmArgs(Iterable<String> jvmArgs) {
        hasJvmArgs = true;
        List<String> immutableBefore = jvmOptions.getAllImmutableJvmArgs();
        jvmOptions.setAllJvmArgs(jvmArgs);
        List<String> immutableAfter = jvmOptions.getAllImmutableJvmArgs();
        userDefinedImmutableJvmArgs = userDefinedImmutableJvmArgs || !immutableBefore.equals(immutableAfter);
    }

    public boolean hasUserDefinedImmutableJvmArgs() {
        return userDefinedImmutableJvmArgs;
    }

    public void setEnvironmentVariables(Map<String, String> envVariables) {
        this.envVariables = envVariables == null ? new HashMap<String, String>(System.getenv()) : envVariables;
    }

    public void setDebug(boolean debug) {
        userDefinedImmutableJvmArgs = userDefinedImmutableJvmArgs || debug;
        jvmOptions.setDebug(debug);
    }

    public void setDebugPort(int debug) {
        jvmOptions.getDebugOptions().getPort().set(debug);
    }

    public void setDebugHost(String host) {
        jvmOptions.getDebugOptions().getHost().set(host);
    }

    public void setDebugSuspend(boolean suspend) {
        jvmOptions.getDebugOptions().getSuspend().set(suspend);
    }

    public void setDebugServer(boolean server) {
        jvmOptions.getDebugOptions().getServer().set(server);
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

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public enum Priority {
        LOW,
        NORMAL,
    }
}
