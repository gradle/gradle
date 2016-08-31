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

import java.util.Set;

import static com.google.common.collect.Sets.newHashSet;

public class GradleProperties {

    public static final String IDLE_TIMEOUT_PROPERTY = "org.gradle.daemon.idletimeout";
    public static final String HEALTH_CHECK_INTERVAL_PROPERTY = "org.gradle.daemon.healthcheckinterval";
    public static final String DAEMON_BASE_DIR_PROPERTY = "org.gradle.daemon.registry.base";
    public static final String JVM_ARGS_PROPERTY = "org.gradle.jvmargs";
    public static final String JAVA_HOME_PROPERTY = "org.gradle.java.home";
    public static final String DAEMON_ENABLED_PROPERTY = "org.gradle.daemon";
    public static final String DEBUG_MODE_PROPERTY = "org.gradle.debug";
    public static final String CONFIGURE_ON_DEMAND_PROPERTY = "org.gradle.configureondemand";
    public static final String PARALLEL_PROPERTY = "org.gradle.parallel";
    public static final String WORKERS_PROPERTY = "org.gradle.workers.max";
    public static final String TASK_OUTPUT_CACHE_PROPERTY = "org.gradle.cache.tasks";

    public static final Set<String> ALL = newHashSet(IDLE_TIMEOUT_PROPERTY, HEALTH_CHECK_INTERVAL_PROPERTY, DAEMON_BASE_DIR_PROPERTY, JVM_ARGS_PROPERTY,
            JAVA_HOME_PROPERTY, DAEMON_ENABLED_PROPERTY, DEBUG_MODE_PROPERTY, CONFIGURE_ON_DEMAND_PROPERTY, PARALLEL_PROPERTY, WORKERS_PROPERTY, TASK_OUTPUT_CACHE_PROPERTY);

    public static boolean isTrue(Object propertyValue) {
        return propertyValue != null && propertyValue.toString().trim().equalsIgnoreCase("true");
    }
}
