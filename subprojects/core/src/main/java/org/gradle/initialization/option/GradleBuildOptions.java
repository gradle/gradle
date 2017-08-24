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

package org.gradle.initialization.option;

import com.google.common.collect.ImmutableSet;

import java.util.Set;

/**
 * Defines all Gradle build options.
 *
 * @since 4.2
 */
public class GradleBuildOptions {

    public static final GradleBuildStringOption DAEMON_IDLE_TIMEOUT;
    public static final GradleBuildStringOption DAEMON_HEALTH_CHECK_INTERVAL;
    public static final GradleBuildStringOption DAEMON_BASE_DIR;
    public static final GradleBuildStringOption JVM_ARGS;
    public static final GradleBuildStringOption JAVA_HOME;
    public static final GradleBuildBooleanOption DAEMON;
    public static final GradleBuildStringOption DEBUG_MODE;
    public static final GradleBuildStringOption CONFIGURE_ON_DEMAND;
    public static final GradleBuildStringOption PARALLEL;
    public static final GradleBuildStringOption MAX_WORKERS;
    public static final GradleBuildBooleanOption BUILD_CACHE;
    public static final GradleBuildStringOption LOG_LEVEL;

    static {
        DAEMON_IDLE_TIMEOUT = GradleBuildOption.createStringOption("org.gradle.daemon.idletimeout");
        DAEMON_HEALTH_CHECK_INTERVAL = GradleBuildOption.createStringOption("org.gradle.daemon.healthcheckinterval");
        DAEMON_BASE_DIR = GradleBuildOption.createStringOption("org.gradle.daemon.registry.base");
        JVM_ARGS = GradleBuildOption.createStringOption("org.gradle.jvmargs");
        JAVA_HOME = GradleBuildOption.createStringOption("org.gradle.java.home");
        DAEMON = GradleBuildOption.createBooleanOption("org.gradle.daemon").withCommandLineOption("daemon", "Uses the Gradle Daemon to run the build. Starts the Daemon if not running.");
        DAEMON.getCommandLineOption().incubating();
        DEBUG_MODE = GradleBuildOption.createStringOption("org.gradle.debug");
        CONFIGURE_ON_DEMAND = GradleBuildOption.createStringOption("org.gradle.configureondemand").withCommandLineOption("configure-on-demand", "Configure necessary projects only. Gradle will attempt to reduce configuration time for large multi-project builds.");
        CONFIGURE_ON_DEMAND.getCommandLineOption().incubating();
        PARALLEL = GradleBuildOption.createStringOption("org.gradle.parallel").withCommandLineOption("parallel", "Build projects in parallel. Gradle will attempt to determine the optimal number of executor threads to use.");
        PARALLEL.getCommandLineOption().incubating();
        MAX_WORKERS = GradleBuildOption.createStringOption("org.gradle.workers.max").withCommandLineOption("max-workers", "Configure the number of concurrent workers Gradle is allowed to use.");
        MAX_WORKERS.getCommandLineOption().hasArgument().incubating();
        BUILD_CACHE = GradleBuildOption.createBooleanOption("org.gradle.caching").withCommandLineOption("build-cache", "Enables the Gradle build cache. Gradle will try to reuse outputs from previous builds.");
        BUILD_CACHE.getCommandLineOption().incubating();
        LOG_LEVEL = GradleBuildOption.createStringOption("org.gradle.logging.level");
    }

    public static final Set<GradleBuildOption> ALL = ImmutableSet.of(DAEMON_IDLE_TIMEOUT, DAEMON_HEALTH_CHECK_INTERVAL, DAEMON_BASE_DIR, JVM_ARGS,
        JAVA_HOME, DAEMON, DEBUG_MODE, CONFIGURE_ON_DEMAND, PARALLEL, MAX_WORKERS, BUILD_CACHE, LOG_LEVEL);

    public static boolean isTrue(Object value) {
        return value != null && value.toString().trim().equalsIgnoreCase("true");
    }
}
