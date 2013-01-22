/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.api.Nullable;
import org.gradle.api.Project;
import org.gradle.api.UncheckedIOException;
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.jvm.JavaHomeException;
import org.gradle.internal.jvm.Jvm;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

/**
 * by Szczepan Faber, created at: 1/22/13
 */
public class GradleProperties {

    public static final String IDLE_TIMEOUT_PROPERTY = "org.gradle.daemon.idletimeout";
    public static final String BASE_DIR_PROPERTY = "org.gradle.daemon.registry.base";
    public static final String JVM_ARGS_PROPERTY = "org.gradle.jvmargs";
    public static final String JAVA_HOME_PROPERTY = "org.gradle.java.home";
    public static final String DAEMON_ENABLED_PROPERTY = "org.gradle.daemon";
    public static final String DEBUG_MODE_PROPERTY = "org.gradle.debug";
    public static final String CONFIGURE_ON_DEMAND_PROPERTY = "org.gradle.configureondemand";

    private File daemonBaseDir;
    private String jvmArgs;

    private Integer idleTimeout;
    private boolean daemonEnabled;
    private File javaHome;
    private boolean debugMode;
    private boolean configureOnDemand;

    public boolean isDaemonEnabled() {
        return daemonEnabled;
    }

    @Nullable
    public File getDaemonBaseDir() {
        return daemonBaseDir;
    }

    public Integer getIdleTimeout() {
        return idleTimeout;
    }

    @Nullable
    public File getJavaHome() {
        return javaHome;
    }

    public String getJvmArgs() {
        return jvmArgs;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    private void setBaseDir(File baseDir) {
        this.daemonBaseDir = GFileUtils.canonicalise(baseDir);
    }

    public GradleProperties configureFromGradleUserHome(File gradleUserHomeDir) {
        setBaseDir(new File(gradleUserHomeDir, "daemon"));
        maybeConfigureFrom(new File(gradleUserHomeDir, Project.GRADLE_PROPERTIES));
        return this;
    }

    public GradleProperties configureFromSystemProperties(Map<?, ?> properties) {
        Object propertyValue = properties.get(BASE_DIR_PROPERTY);
        if (propertyValue != null) {
            setBaseDir(new File(propertyValue.toString()));
        }
        configureFrom(properties);
        return this;
    }

    public GradleProperties configureFromBuildDir(File currentDir, boolean searchUpwards) {
        BuildLayoutFactory factory = new BuildLayoutFactory();
        BuildLayout layout = factory.getLayoutFor(currentDir, searchUpwards);
        maybeConfigureFrom(new File(layout.getRootDirectory(), Project.GRADLE_PROPERTIES));
        return this;
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

    GradleProperties configureFrom(Map<?, ?> properties) {
        Object propertyValue = properties.get(IDLE_TIMEOUT_PROPERTY);
        if (propertyValue != null) {
            try {
                idleTimeout = new Integer(propertyValue.toString());
            } catch (NumberFormatException e) {
                throw new GradleException(String.format("Unable to parse %s property. The value should be an int but is: %s", IDLE_TIMEOUT_PROPERTY, propertyValue));
            }
        }
        propertyValue = properties.get(JVM_ARGS_PROPERTY);
        if (propertyValue != null) {
            jvmArgs = propertyValue.toString();
        }
        propertyValue = properties.get(DAEMON_ENABLED_PROPERTY);
        if (propertyValue != null) {
            daemonEnabled = isTrue(propertyValue);
        }

        propertyValue = properties.get(JAVA_HOME_PROPERTY);
        if (propertyValue != null) {
            javaHome = new File(propertyValue.toString());
            if (!javaHome.isDirectory()) {
                throw new GradleException(String.format("Java home supplied via '%s' is invalid. Dir does not exist: %s", JAVA_HOME_PROPERTY, propertyValue));
            }
            try {
                Jvm.forHome(javaHome);
            } catch (JavaHomeException e) {
                throw new GradleException(String.format("Java home supplied via '%s' seems to be invalid: %s", JAVA_HOME_PROPERTY, propertyValue));
            }
        }

        propertyValue = properties.get(DEBUG_MODE_PROPERTY);
        if (propertyValue != null) {
            debugMode = isTrue(propertyValue);
        }

        propertyValue = properties.get(CONFIGURE_ON_DEMAND_PROPERTY);
        if (propertyValue != null) {
            configureOnDemand = isTrue(propertyValue);
        }
        return this;
    }

    public void updateStartParameter(StartParameter startParameter) {
        if (configureOnDemand) {
            startParameter.setConfigureOnDemand(configureOnDemand);
        }
    }

    private static boolean isTrue(Object propertyValue) {
        return propertyValue.toString().equalsIgnoreCase("true");
    }
}
