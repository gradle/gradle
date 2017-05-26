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

package org.gradle.internal.nativeintegration.jvm;

import org.gradle.api.JavaVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Detects whether or not Gradle is being run on Java 9, and logs a warning message the first time it's detected.
 */
public class Java9Detector {
    private static final Logger LOGGER = LoggerFactory.getLogger(Java9Detector.class);
    private static final String SKIPPING_ENVIRONMENT = "Running with Java 9, the Gradle Daemon will not have the Gradle Client's environment variables.";
    private AtomicBoolean hasWarned = new AtomicBoolean(false);

    public boolean isJava9() {
        if (JavaVersion.current().isJava9Compatible()) {
            if (!hasWarned.getAndSet(true)) {
                LOGGER.warn(SKIPPING_ENVIRONMENT);
            }
            return true;
        }
        return false;
    }
}
