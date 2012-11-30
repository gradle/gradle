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

import org.gradle.StartParameter;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.jvm.JavaHomeException;
import org.gradle.internal.jvm.Jvm;
import org.gradle.process.internal.JvmOptions;
import org.gradle.util.GFileUtils;
import org.gradle.util.GUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

import static java.util.Arrays.asList;
import static org.gradle.util.GFileUtils.canonicalise;

public class DaemonParameters {
    public static final String IDLE_TIMEOUT_SYS_PROPERTY = "org.gradle.daemon.idletimeout";
    public static final String BASE_DIR_SYS_PROPERTY = "org.gradle.daemon.registry.base";
    public static final String JVM_ARGS_SYS_PROPERTY = "org.gradle.jvmargs";
    public static final String JAVA_HOME_SYS_PROPERTY = "org.gradle.java.home";
    public static final String DAEMON_SYS_PROPERTY = "org.gradle.daemon";
    public static final String DEBUG_SYS_PROPERTY = "org.gradle.debug";
    static final int DEFAULT_IDLE_TIMEOUT = 3 * 60 * 60 * 1000;
    private final String uid;
    private File baseDir = new File(StartParameter.DEFAULT_GRADLE_USER_HOME, "daemon");
    private int idleTimeout = DEFAULT_IDLE_TIMEOUT;
    private final JvmOptions jvmOptions = new JvmOptions(new IdentityFileResolver());
    private boolean usingDefaultJvmArgs = true;
    private boolean enabled;
    private File javaHome;

    public DaemonParameters(String uid) {
        this.uid = uid;
        jvmOptions.setAllJvmArgs(getDefaultJvmArgs());
    }

    public DaemonParameters() {
        this(UUID.randomUUID().toString());
    }

    List<String> getDefaultJvmArgs() {
        return new LinkedList<String>(asList("-Xmx1024m", "-XX:MaxPermSize=256m", "-XX:+HeapDumpOnOutOfMemoryError"));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getUid() {
        return uid;
    }

    public File getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(File baseDir) {
        this.baseDir = GFileUtils.canonicalise(baseDir);
    }

    public int getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(int idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public List<String> getEffectiveJvmArgs() {
        return jvmOptions.getAllImmutableJvmArgs();
    }

    public List<String> getAllJvmArgs() {
        return jvmOptions.getAllJvmArgs();
    }

    public boolean isUsingDefaultJvmArgs() {
        return usingDefaultJvmArgs;
    }

    public File getEffectiveJavaHome() {
        if (javaHome == null) {
            return canonicalise(Jvm.current().getJavaHome());
        }
        return javaHome;
    }
    
    public String getEffectiveJavaExecutable() {
        if (javaHome == null) {
            return Jvm.current().getJavaExecutable().getAbsolutePath();
        }
        return Jvm.forHome(javaHome).getJavaExecutable().getAbsolutePath();
    }

    public void setJavaHome(File javaHome) {
        this.javaHome = javaHome;
    }

    public Map<String, String> getSystemProperties() {
        Map<String, String> systemProperties = new HashMap<String, String>();
        GUtil.addToMap(systemProperties, jvmOptions.getSystemProperties());
        return systemProperties;
    }

    public Map<String, String> getEffectiveSystemProperties() {
        Map<String, String> systemProperties = new HashMap<String, String>();
        GUtil.addToMap(systemProperties, jvmOptions.getSystemProperties());
        GUtil.addToMap(systemProperties, System.getProperties());
        return systemProperties;
    }

    public void setJvmArgs(Iterable<String> jvmArgs) {
        usingDefaultJvmArgs = false;
        jvmOptions.setAllJvmArgs(jvmArgs);
    }

    public void configureFromGradleUserHome(File gradleUserHomeDir) {
        setBaseDir(new File(gradleUserHomeDir, "daemon"));
        maybeConfigureFrom(new File(gradleUserHomeDir, Project.GRADLE_PROPERTIES));
    }

    public void configureFromSystemProperties(Map<?, ?> properties) {
        Object propertyValue = properties.get(BASE_DIR_SYS_PROPERTY);
        if (propertyValue != null) {
            setBaseDir(new File(propertyValue.toString()));
        }
        configureFrom(properties);
    }

    public void configureFromBuildDir(File currentDir, boolean searchUpwards) {
        BuildLayoutFactory factory = new BuildLayoutFactory();
        BuildLayout layout = factory.getLayoutFor(currentDir, searchUpwards);
        maybeConfigureFrom(new File(layout.getRootDirectory(), Project.GRADLE_PROPERTIES));
    }

    private void maybeConfigureFrom(File propertiesFile) {
        if (!propertiesFile.isFile()) {
            return;
        }

        Properties properties = new Properties();
        try {
            FileInputStream inputStream = new FileInputStream(propertiesFile);
            try {
                properties.load(inputStream);
            } finally {
                inputStream.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        configureFrom(properties);
    }

    void configureFrom(Map<?, ?> properties) {
        Object propertyValue = properties.get(IDLE_TIMEOUT_SYS_PROPERTY);
        if (propertyValue != null) {
            try {
                idleTimeout = Integer.parseInt(propertyValue.toString());
            } catch (NumberFormatException e) {
                throw new GradleException(String.format("Unable to parse %s property. The value should be an int but is: %s", IDLE_TIMEOUT_SYS_PROPERTY, propertyValue));
            }
        }
        propertyValue = properties.get(JVM_ARGS_SYS_PROPERTY);
        if (propertyValue != null) {
            setJvmArgs(JvmOptions.fromString(propertyValue.toString()));
        }
        propertyValue = properties.get(DAEMON_SYS_PROPERTY);
        if (propertyValue != null) {
            enabled = propertyValue.toString().equalsIgnoreCase("true");
        }

        propertyValue = properties.get(JAVA_HOME_SYS_PROPERTY);
        if (propertyValue != null) {
            javaHome = new File(propertyValue.toString());
            if (!javaHome.isDirectory()) {
                throw new GradleException(String.format("Java home supplied via '%s' is invalid. Dir does not exist: %s", JAVA_HOME_SYS_PROPERTY, propertyValue));
            }
            try {
                Jvm.forHome(javaHome);
            } catch (JavaHomeException e) {
                throw new GradleException(String.format("Java home supplied via '%s' seems to be invalid: %s", JAVA_HOME_SYS_PROPERTY, propertyValue));
            }
        }

        propertyValue = properties.get(DEBUG_SYS_PROPERTY);
        if (propertyValue != null) {
            jvmOptions.setDebug(propertyValue.toString().equalsIgnoreCase("true"));
        }
    }
}
