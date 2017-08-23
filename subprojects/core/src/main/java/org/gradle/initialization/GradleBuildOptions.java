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

import static org.gradle.initialization.GradleBuildOption.OptionType.BOOLEAN;
import static org.gradle.initialization.GradleBuildOption.OptionType.STRING;

/**
 * Defines all Gradle build options.
 *
 * @since 4.2
 */
public class GradleBuildOptions {

    public static final GradleBuildOption DAEMON_IDLE_TIMEOUT;
    public static final GradleBuildOption DAEMON_HEALTH_CHECK_INTERVAL;
    public static final GradleBuildOption DAEMON_BASE_DIR;
    public static final GradleBuildOption JVM_ARGS;
    public static final GradleBuildOption JAVA_HOME;
    public static final GradleBuildOption DAEMON;
    public static final GradleBuildOption DEBUG_MODE;
    public static final GradleBuildOption CONFIGURE_ON_DEMAND;
    public static final GradleBuildOption PARALLEL;
    public static final GradleBuildOption MAX_WORKERS;
    public static final GradleBuildOption BUILD_CACHE;
    public static final GradleBuildOption LOG_LEVEL;

    static {
        DAEMON_IDLE_TIMEOUT = GradleBuildOption.create(STRING, "org.gradle.daemon.idletimeout");
        DAEMON_HEALTH_CHECK_INTERVAL = GradleBuildOption.create(STRING, "org.gradle.daemon.healthcheckinterval");
        DAEMON_BASE_DIR = GradleBuildOption.create(STRING, "org.gradle.daemon.registry.base");
        JVM_ARGS = GradleBuildOption.create(STRING, "org.gradle.jvmargs");
        JAVA_HOME = GradleBuildOption.create(STRING, "org.gradle.java.home");
        DAEMON = GradleBuildOption.create(BOOLEAN, "org.gradle.daemon").withCommandLineOption("daemon", "Uses the Gradle Daemon to run the build. Starts the Daemon if not running.");
        DAEMON.getCommandLineOption().incubating();
        DEBUG_MODE = GradleBuildOption.create(STRING, "org.gradle.debug");
        CONFIGURE_ON_DEMAND = GradleBuildOption.create(STRING, "org.gradle.configureondemand").withCommandLineOption("configure-on-demand", "Configure necessary projects only. Gradle will attempt to reduce configuration time for large multi-project builds.");
        CONFIGURE_ON_DEMAND.getCommandLineOption().incubating();
        PARALLEL = GradleBuildOption.create(STRING, "org.gradle.parallel").withCommandLineOption("parallel", "Build projects in parallel. Gradle will attempt to determine the optimal number of executor threads to use.");
        PARALLEL.getCommandLineOption().incubating();
        MAX_WORKERS = GradleBuildOption.create(STRING, "org.gradle.workers.max").withCommandLineOption("max-workers", "Configure the number of concurrent workers Gradle is allowed to use.");
        MAX_WORKERS.getCommandLineOption().hasArgument().incubating();
        BUILD_CACHE = GradleBuildOption.create(BOOLEAN, "org.gradle.caching").withCommandLineOption("build-cache", "Enables the Gradle build cache. Gradle will try to reuse outputs from previous builds.");
        BUILD_CACHE.getCommandLineOption().incubating();
        LOG_LEVEL = GradleBuildOption.create(STRING, "org.gradle.logging.level");
    }

    public static final Set<GradleBuildOption> ALL = ImmutableSet.of(DAEMON_IDLE_TIMEOUT, DAEMON_HEALTH_CHECK_INTERVAL, DAEMON_BASE_DIR, JVM_ARGS,
        JAVA_HOME, DAEMON, DEBUG_MODE, CONFIGURE_ON_DEMAND, PARALLEL, MAX_WORKERS, BUILD_CACHE, LOG_LEVEL);

    public static boolean isTrue(Object value) {
        return value != null && value.toString().trim().equalsIgnoreCase("true");
    }
}
