/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.jvm.inspection;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.jvm.toolchain.internal.InstallationLocation;

import java.util.function.BiConsumer;

/**
 * Reports invalid JVM installations with the provided logger `warn` level.
 */
public class InvalidInstallationWarningReporter implements BiConsumer<InstallationLocation, JvmInstallationMetadata> {

    private final Logger logger;

    public InvalidInstallationWarningReporter() {
        this(Logging.getLogger(InvalidInstallationWarningReporter.class));
    }

    @VisibleForTesting
    public InvalidInstallationWarningReporter(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void accept(InstallationLocation installationLocation, JvmInstallationMetadata metadata) {
        if (!metadata.isValidInstallation()) {
            logger.warn(
                "Invalid Java installation found at {}. " +
                    "It will be re-checked in the next build. This might have performance impact if it keeps failing. " +
                    "Run the 'javaToolchains' task for more details.",
                installationLocation.getDisplayName()
            );
        }
    }
}
