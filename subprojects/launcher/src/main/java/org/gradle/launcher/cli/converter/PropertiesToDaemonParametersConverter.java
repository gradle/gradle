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
import org.gradle.internal.jvm.Jvm;
import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.gradle.process.internal.JvmOptions;

import java.io.File;
import java.util.Map;

import static org.gradle.launcher.daemon.configuration.GradleProperties.*;

public class PropertiesToDaemonParametersConverter {
    public void convert(Map<String, String> properties, DaemonParameters target) {
        String prop = properties.get(IDLE_TIMEOUT_PROPERTY);
        if (prop != null) {
            try {
                target.setIdleTimeout(new Integer(prop));
            } catch (NumberFormatException e) {
                throw new GradleException(String.format("Unable to parse %s property. The value should be an int but is: %s", IDLE_TIMEOUT_PROPERTY, prop));
            }
        }

        prop = properties.get(JVM_ARGS_PROPERTY);
        if (prop != null) {
            target.setJvmArgs(JvmOptions.fromString(prop));
        }

        prop = properties.get(JAVA_HOME_PROPERTY);
        if (prop != null) {
            File javaHome = new File(prop);
            if (!javaHome.isDirectory()) {
                throw new GradleException(String.format("Java home supplied via '%s' is invalid. Invalid directory: %s", JAVA_HOME_PROPERTY, prop));
            }
            try {
                Jvm.forHome(javaHome);
            } catch (JavaHomeException e) {
                throw new GradleException(String.format("Java home supplied via '%s' seems to be invalid: %s", JAVA_HOME_PROPERTY, prop));
            }
            target.setJavaHome(javaHome);
        }

        prop = properties.get(DAEMON_BASE_DIR_PROPERTY);
        if (prop != null) {
            target.setBaseDir(new File(prop));
        }

        target.setEnabled(isTrue(properties.get(DAEMON_ENABLED_PROPERTY)));
        target.setDebug(isTrue(properties.get(DEBUG_MODE_PROPERTY)));
    }
}
