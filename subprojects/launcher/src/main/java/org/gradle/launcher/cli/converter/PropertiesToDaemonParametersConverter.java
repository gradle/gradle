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

package org.gradle.launcher.cli.converter;

import org.gradle.api.GradleException;
import org.gradle.internal.jvm.JavaHomeException;
import org.gradle.internal.jvm.JavaInfo;
import org.gradle.internal.jvm.Jvm;
import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.gradle.initialization.option.GradleBuildOptions;
import org.gradle.process.internal.JvmOptions;

import java.io.File;
import java.util.Map;

import static org.gradle.initialization.option.GradleBuildOptions.isTrue;

public class PropertiesToDaemonParametersConverter {
    public void convert(Map<String, String> properties, DaemonParameters target) {
        String prop = properties.get(GradleBuildOptions.DAEMON_IDLE_TIMEOUT.getGradleProperty());
        if (prop != null) {
            try {
                target.setIdleTimeout(new Integer(prop));
            } catch (NumberFormatException e) {
                throw new GradleException(String.format("Unable to parse %s property. The value should be an int but is: %s", GradleBuildOptions.DAEMON_IDLE_TIMEOUT.getGradleProperty(), prop));
            }
        }

        prop = properties.get(GradleBuildOptions.DAEMON_HEALTH_CHECK_INTERVAL.getGradleProperty());
        if (prop != null) {
            try {
                target.setPeriodicCheckInterval(new Integer(prop));
            } catch (NumberFormatException e) {
                throw new GradleException(String.format("Unable to parse %s property. Expected an int but got: %s", GradleBuildOptions.DAEMON_HEALTH_CHECK_INTERVAL.getGradleProperty(), prop), e);
            }
        }

        prop = properties.get(GradleBuildOptions.JVM_ARGS.getGradleProperty());
        if (prop != null) {
            target.setJvmArgs(JvmOptions.fromString(prop));
        }

        prop = properties.get(GradleBuildOptions.JAVA_HOME.getGradleProperty());
        if (prop != null) {
            File javaHome = new File(prop);
            if (!javaHome.isDirectory()) {
                throw new GradleException(String.format("Java home supplied via '%s' is invalid. Invalid directory: %s", GradleBuildOptions.JAVA_HOME.getGradleProperty(), prop));
            }
            JavaInfo jvm;
            try {
                jvm = Jvm.forHome(javaHome);
            } catch (JavaHomeException e) {
                throw new GradleException(String.format("Java home supplied via '%s' seems to be invalid: %s", GradleBuildOptions.JAVA_HOME.getGradleProperty(), prop));
            }
            target.setJvm(jvm);
        }

        prop = properties.get(GradleBuildOptions.DAEMON_BASE_DIR.getGradleProperty());
        if (prop != null) {
            target.setBaseDir(new File(prop));
        }

        String daemonEnabledPropertyValue = properties.get(GradleBuildOptions.DAEMON.getGradleProperty());
        if (daemonEnabledPropertyValue != null) {
            target.setEnabled(isTrue(daemonEnabledPropertyValue));
        }

        final String debugEnabledPropertyValue = properties.get(GradleBuildOptions.DEBUG_MODE.getGradleProperty());
        if (debugEnabledPropertyValue != null) {
            target.setDebug(isTrue(debugEnabledPropertyValue));
        }
    }
}
