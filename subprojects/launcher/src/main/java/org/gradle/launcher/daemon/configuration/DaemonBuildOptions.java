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

import org.gradle.internal.buildoption.BooleanBuildOption;
import org.gradle.internal.buildoption.BooleanCommandLineOptionConfiguration;
import org.gradle.internal.buildoption.BuildOption;
import org.gradle.internal.buildoption.BuildOptionSet;
import org.gradle.internal.buildoption.CommandLineOptionConfiguration;
import org.gradle.internal.buildoption.EnabledOnlyBooleanBuildOption;
import org.gradle.internal.buildoption.Origin;
import org.gradle.internal.buildoption.StringBuildOption;
import org.gradle.internal.jvm.JavaHomeException;
import org.gradle.internal.jvm.JavaInfo;
import org.gradle.internal.jvm.Jvm;
import org.gradle.process.internal.JvmOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class DaemonBuildOptions extends BuildOptionSet<DaemonParameters> {

    private static List<BuildOption<DaemonParameters>> options;

    static {
        List<BuildOption<DaemonParameters>> options = new ArrayList<BuildOption<DaemonParameters>>();
        options.add(new IdleTimeoutOption());
        options.add(new HealthCheckOption());
        options.add(new BaseDirOption());
        options.add(new JvmArgsOption());
        options.add(new JavaHomeOption());
        options.add(new DebugOption());
        options.add(new DebugPortOption());
        options.add(new DebugServerOption());
        options.add(new DebugSuspendOption());
        options.add(new DaemonOption());
        options.add(new ForegroundOption());
        options.add(new StopOption());
        options.add(new StatusOption());
        options.add(new PriorityOption());
        DaemonBuildOptions.options = Collections.unmodifiableList(options);
    }

    public static List<BuildOption<DaemonParameters>> get() {
        return options;
    }

    @Override
    public List<? extends BuildOption<? super DaemonParameters>> getAllOptions() {
        return options;
    }

    public static class IdleTimeoutOption extends StringBuildOption<DaemonParameters> {
        public static final String GRADLE_PROPERTY = "org.gradle.daemon.idletimeout";

        public IdleTimeoutOption() {
            super(GRADLE_PROPERTY);
        }

        @Override
        public void applyTo(String value, DaemonParameters settings, Origin origin) {
            try {
                settings.setIdleTimeout(Integer.parseInt(value));
            } catch (NumberFormatException e) {
                origin.handleInvalidValue(value, "the value should be an int");
            }
        }
    }

    public static class HealthCheckOption extends StringBuildOption<DaemonParameters> {
        public static final String GRADLE_PROPERTY = "org.gradle.daemon.healthcheckinterval";

        public HealthCheckOption() {
            super(GRADLE_PROPERTY);
        }

        @Override
        public void applyTo(String value, DaemonParameters settings, Origin origin) {
            try {
                settings.setPeriodicCheckInterval(Integer.parseInt(value));
            } catch (NumberFormatException e) {
                origin.handleInvalidValue(value, "the value should be an int");
            }
        }
    }

    public static class BaseDirOption extends StringBuildOption<DaemonParameters> {
        public static final String GRADLE_PROPERTY = "org.gradle.daemon.registry.base";

        public BaseDirOption() {
            super(GRADLE_PROPERTY);
        }

        @Override
        public void applyTo(String value, DaemonParameters settings, Origin origin) {
            settings.setBaseDir(new File(value));
        }
    }

    public static class JvmArgsOption extends StringBuildOption<DaemonParameters> {
        public static final String GRADLE_PROPERTY = "org.gradle.jvmargs";

        public JvmArgsOption() {
            super(GRADLE_PROPERTY);
        }

        @Override
        public void applyTo(String value, DaemonParameters settings, Origin origin) {
            settings.setJvmArgs(JvmOptions.fromString(value));
        }
    }

    public static class JavaHomeOption extends StringBuildOption<DaemonParameters> {
        public static final String GRADLE_PROPERTY = "org.gradle.java.home";

        public JavaHomeOption() {
            super(GRADLE_PROPERTY);
        }

        @Override
        public void applyTo(String value, DaemonParameters settings, Origin origin) {
            File javaHome = new File(value);
            if (!javaHome.isDirectory()) {
                origin.handleInvalidValue(value, "Java home supplied is invalid");
            }
            JavaInfo jvm;
            try {
                jvm = Jvm.forHome(javaHome);
                settings.setJvm(jvm);
            } catch (JavaHomeException e) {
                origin.handleInvalidValue(value, "Java home supplied seems to be invalid");
            }
        }
    }

    public static class DebugOption extends BooleanBuildOption<DaemonParameters> {
        public static final String GRADLE_PROPERTY = "org.gradle.debug";

        public DebugOption() {
            super(GRADLE_PROPERTY);
        }

        @Override
        public void applyTo(boolean value, DaemonParameters settings, Origin origin) {
            settings.setDebug(value);
        }
    }

    public static class DebugPortOption extends StringBuildOption<DaemonParameters> {
        public static final String GRADLE_PROPERTY = "org.gradle.debug.port";

        public DebugPortOption() {
            super(GRADLE_PROPERTY);
        }

        @Override
        public void applyTo(String value, DaemonParameters settings, Origin origin) {
            String hint = "must be a number between 1 and 65535";
            int port = 0;
            try {
                port = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                origin.handleInvalidValue(value, hint);
            }
            if (port < 1 || port > 65535) {
                origin.handleInvalidValue(value, hint);
            } else {
                settings.setDebugPort(port);
            }
        }
    }

    public static class DebugSuspendOption extends BooleanBuildOption<DaemonParameters> {
        public static final String GRADLE_PROPERTY = "org.gradle.debug.suspend";

        public DebugSuspendOption() {
            super(GRADLE_PROPERTY);
        }

        @Override
        public void applyTo(boolean value, DaemonParameters settings, Origin origin) {
            settings.setDebugSuspend(value);
        }
    }

    public static class DebugServerOption extends BooleanBuildOption<DaemonParameters> {
        public static final String GRADLE_PROPERTY = "org.gradle.debug.server";

        public DebugServerOption() {
            super(GRADLE_PROPERTY);
        }

        @Override
        public void applyTo(boolean value, DaemonParameters settings, Origin origin) {
            settings.setDebugServer(value);
        }
    }

    public static class DaemonOption extends BooleanBuildOption<DaemonParameters> {
        public static final String GRADLE_PROPERTY = "org.gradle.daemon";

        public DaemonOption() {
            super(GRADLE_PROPERTY, BooleanCommandLineOptionConfiguration.create("daemon", "Uses the Gradle daemon to run the build. Starts the daemon if not running.", "Do not use the Gradle daemon to run the build. Useful occasionally if you have configured Gradle to always run with the daemon by default."));
        }

        @Override
        public void applyTo(boolean value, DaemonParameters settings, Origin origin) {
            settings.setEnabled(value);
        }
    }

    public static class ForegroundOption extends EnabledOnlyBooleanBuildOption<DaemonParameters> {
        public ForegroundOption() {
            super(null, CommandLineOptionConfiguration.create("foreground", "Starts the Gradle daemon in the foreground."));
        }

        @Override
        public void applyTo(DaemonParameters settings, Origin origin) {
            settings.setForeground(true);
        }
    }

    public static class StopOption extends EnabledOnlyBooleanBuildOption<DaemonParameters> {
        public StopOption() {
            super(null, CommandLineOptionConfiguration.create("stop", "Stops the Gradle daemon if it is running."));
        }

        @Override
        public void applyTo(DaemonParameters settings, Origin origin) {
            settings.setStop(true);
        }
    }

    public static class StatusOption extends EnabledOnlyBooleanBuildOption<DaemonParameters> {
        public StatusOption() {
            super(null, CommandLineOptionConfiguration.create("status", "Shows status of running and recently stopped Gradle daemon(s)."));
        }

        @Override
        public void applyTo(DaemonParameters settings, Origin origin) {
            settings.setStatus(true);
        }
    }

    public static class PriorityOption extends StringBuildOption<DaemonParameters> {
        public static final String GRADLE_PROPERTY = "org.gradle.priority";

        public PriorityOption() {
            super(GRADLE_PROPERTY, CommandLineOptionConfiguration.create("priority", "Specifies the scheduling priority for the Gradle daemon and all processes launched by it. Values are 'normal' (default) or 'low'"));
        }

        @Override
        public void applyTo(String value, DaemonParameters settings, Origin origin) {
            try {
                settings.setPriority(DaemonParameters.Priority.valueOf(value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                origin.handleInvalidValue(value);
            }
        }
    }
}
