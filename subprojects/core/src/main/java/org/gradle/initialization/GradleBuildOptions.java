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

package org.gradle.initialization;

import com.google.common.collect.ImmutableSet;

import java.util.Set;

import static org.gradle.initialization.GradleBuildOption.CommandLineOption;
import static org.gradle.initialization.GradleBuildOption.OptionType.BOOLEAN;
import static org.gradle.initialization.GradleBuildOption.OptionType.STRING;

/**
 * Defines all Gradle build options.
 *
 * @since 4.2
 */
public class GradleBuildOptions {

    public static final GradleBuildOption DAEMON_IDLE_TIMEOUT = new GradleBuildOption(STRING, "org.gradle.daemon.idletimeout");
    public static final GradleBuildOption DAEMON_HEALTH_CHECK_INTERVAL = new GradleBuildOption(STRING, "org.gradle.daemon.healthcheckinterval");
    public static final GradleBuildOption DAEMON_BASE_DIR = new GradleBuildOption(STRING, "org.gradle.daemon.registry.base");
    public static final GradleBuildOption JVM_ARGS = new GradleBuildOption(STRING, "org.gradle.jvmargs");
    public static final GradleBuildOption JAVA_HOME = new GradleBuildOption(STRING, "org.gradle.java.home");
    public static final GradleBuildOption DAEMON = new GradleBuildOption(BOOLEAN, new CommandLineOption("daemon", "Uses the Gradle Daemon to run the build. Starts the Daemon if not running.", false), "org.gradle.daemon");
    public static final GradleBuildOption DEBUG_MODE = new GradleBuildOption(STRING, "org.gradle.debug");
    public static final GradleBuildOption CONFIGURE_ON_DEMAND = new GradleBuildOption(STRING, new CommandLineOption("configure-on-demand", "Configure necessary projects only. Gradle will attempt to reduce configuration time for large multi-project builds.", true), "org.gradle.configureondemand");
    public static final GradleBuildOption PARALLEL = new GradleBuildOption(STRING, new CommandLineOption("parallel", "Build projects in parallel. Gradle will attempt to determine the optimal number of executor threads to use.", true), "org.gradle.parallel");
    public static final GradleBuildOption MAX_WORKERS = new GradleBuildOption(STRING, new CommandLineOption("max-workers", "Configure the number of concurrent workers Gradle is allowed to use.", true), "org.gradle.workers.max");
    public static final GradleBuildOption BUILD_CACHE = new GradleBuildOption(BOOLEAN, new CommandLineOption("build-cache", "Enables the Gradle build cache. Gradle will try to reuse outputs from previous builds.", true), "org.gradle.caching");
    public static final GradleBuildOption LOG_LEVEL = new GradleBuildOption(STRING, "org.gradle.logging.level");

    public static final Set<GradleBuildOption> ALL = ImmutableSet.of(DAEMON_IDLE_TIMEOUT, DAEMON_HEALTH_CHECK_INTERVAL, DAEMON_BASE_DIR, JVM_ARGS,
        JAVA_HOME, DAEMON, DEBUG_MODE, CONFIGURE_ON_DEMAND, PARALLEL, MAX_WORKERS, BUILD_CACHE, LOG_LEVEL);

    public static boolean isTrue(Object value) {
        return value != null && value.toString().trim().equalsIgnoreCase("true");
    }
}
