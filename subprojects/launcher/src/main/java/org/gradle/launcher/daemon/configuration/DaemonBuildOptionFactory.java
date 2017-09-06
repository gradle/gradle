/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.GradleException;
import org.gradle.internal.Factory;
import org.gradle.internal.buildoption.BooleanBuildOption;
import org.gradle.internal.buildoption.BuildOption;
import org.gradle.internal.buildoption.CommandLineOptionConfiguration;
import org.gradle.internal.buildoption.NoArgumentBuildOption;
import org.gradle.internal.buildoption.StringBuildOption;
import org.gradle.internal.jvm.JavaHomeException;
import org.gradle.internal.jvm.JavaInfo;
import org.gradle.internal.jvm.Jvm;
import org.gradle.process.internal.JvmOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DaemonBuildOptionFactory implements Factory<List<BuildOption<DaemonParameters>>> {

    private final List<BuildOption<DaemonParameters>> options = new ArrayList<BuildOption<DaemonParameters>>();

    public DaemonBuildOptionFactory() {
        options.add(new IdleTimeoutOption());
        options.add(new HealthCheckOption());
        options.add(new BaseDirOption());
        options.add(new JvmArgsOption());
        options.add(new JavaHomeOption());
        options.add(new DebugOption());
        options.add(new DaemonOption());
        options.add(new ForegroundOption());
        options.add(new StopOption());
        options.add(new StatusOption());
    }

    @Override
    public List<BuildOption<DaemonParameters>> create() {
        return options;
    }

    public static class IdleTimeoutOption extends StringBuildOption<DaemonParameters> {
        public static final String GRADLE_PROPERTY = "org.gradle.daemon.idletimeout";

        public IdleTimeoutOption() {
            super(GRADLE_PROPERTY);
        }

        @Override
        public void applyTo(String value, DaemonParameters settings) {
            try {
                settings.setIdleTimeout(new Integer(value));
            } catch (NumberFormatException e) {
                throw new GradleException(String.format("Unable to parse %s property. The value should be an int but is: %s", gradleProperty, value));
            }
        }
    }

    public static class HealthCheckOption extends StringBuildOption<DaemonParameters> {
        public static final String GRADLE_PROPERTY = "org.gradle.daemon.healthcheckinterval";

        public HealthCheckOption() {
            super(GRADLE_PROPERTY);
        }

        @Override
        public void applyTo(String value, DaemonParameters settings) {
            try {
                settings.setPeriodicCheckInterval(new Integer(value));
            } catch (NumberFormatException e) {
                throw new GradleException(String.format("Unable to parse %s property. Expected an int but got: %s", gradleProperty, value), e);
            }
        }
    }

    public static class BaseDirOption extends StringBuildOption<DaemonParameters> {
        public static final String GRADLE_PROPERTY = "org.gradle.daemon.registry.base";

        public BaseDirOption() {
            super(GRADLE_PROPERTY);
        }

        @Override
        public void applyTo(String value, DaemonParameters settings) {
            settings.setBaseDir(new File(value));
        }
    }

    public static class JvmArgsOption extends StringBuildOption<DaemonParameters> {
        public static final String GRADLE_PROPERTY = "org.gradle.jvmargs";

        public JvmArgsOption() {
            super(GRADLE_PROPERTY);
        }

        @Override
        public void applyTo(String value, DaemonParameters settings) {
            settings.setJvmArgs(JvmOptions.fromString(value));
        }
    }

    public static class JavaHomeOption extends StringBuildOption<DaemonParameters> {
        public static final String GRADLE_PROPERTY = "org.gradle.java.home";

        public JavaHomeOption() {
            super(GRADLE_PROPERTY);
        }

        @Override
        public void applyTo(String value, DaemonParameters settings) {
            File javaHome = new File(value);
            if (!javaHome.isDirectory()) {
                throw new GradleException(String.format("Java home supplied via '%s' is invalid. Invalid directory: %s", gradleProperty, value));
            }
            JavaInfo jvm;
            try {
                jvm = Jvm.forHome(javaHome);
            } catch (JavaHomeException e) {
                throw new GradleException(String.format("Java home supplied via '%s' seems to be invalid: %s", gradleProperty, value));
            }
            settings.setJvm(jvm);
        }
    }

    public static class DebugOption extends BooleanBuildOption<DaemonParameters> {
        public static final String GRADLE_PROPERTY = "org.gradle.debug";

        public DebugOption() {
            super(GRADLE_PROPERTY);
        }

        @Override
        public void applyTo(boolean value, DaemonParameters settings) {
            settings.setDebug(value);
        }
    }

    public static class DaemonOption extends BooleanBuildOption<DaemonParameters> {
        public static final String GRADLE_PROPERTY = "org.gradle.daemon";

        public DaemonOption() {
            super(GRADLE_PROPERTY, CommandLineOptionConfiguration.create("daemon", "Uses the Gradle Daemon to run the build. Starts the Daemon if not running."));
        }

        @Override
        public void applyTo(boolean value, DaemonParameters settings) {
            settings.setEnabled(value);
        }
    }

    public static class ForegroundOption extends NoArgumentBuildOption<DaemonParameters> {
        public ForegroundOption() {
            super(null, CommandLineOptionConfiguration.create("foreground", "Starts the Gradle Daemon in the foreground.").incubating());
        }

        @Override
        public void applyTo(DaemonParameters settings) {
            settings.setForeground(true);
        }
    }

    public static class StopOption extends NoArgumentBuildOption<DaemonParameters> {
        public StopOption() {
            super(null, CommandLineOptionConfiguration.create("stop", "Stops the Gradle Daemon if it is running."));
        }

        @Override
        public void applyTo(DaemonParameters settings) {
            settings.setStop(true);
        }
    }

    public static class StatusOption extends NoArgumentBuildOption<DaemonParameters> {
        public StatusOption() {
            super(null, CommandLineOptionConfiguration.create("status", "Shows status of running and recently stopped Gradle Daemon(s)."));
        }

        @Override
        public void applyTo(DaemonParameters settings) {
            settings.setStatus(true);
        }
    }
}
