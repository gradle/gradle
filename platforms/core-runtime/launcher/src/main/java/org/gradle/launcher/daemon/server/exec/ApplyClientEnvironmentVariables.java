/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.launcher.daemon.server.exec;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.nativeintegration.EnvironmentModificationResult;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.launcher.daemon.protocol.Build;
import org.gradle.launcher.daemon.server.api.DaemonCommandExecution;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Map;

/**
 * Applies the environment variables specified by the client to the daemon JVM, and restores the
 * previous values when the build finishes.
 *
 * <p>This action runs <em>after</em> {@link LogToClient} so that the warning logged when the daemon
 * cannot mutate its process environment (typically because the native integration is unavailable, e.g.
 * when {@code ~/.gradle/native/} is on a {@code noexec} filesystem) is forwarded to the client and
 * surfaces in the build's standard output rather than being buried in the daemon log.</p>
 *
 * <p>Sibling action {@link EstablishBuildEnvironment} handles system properties and the working
 * directory and runs <em>before</em> {@code LogToClient} so that properties such as
 * {@link LogToClient#DISABLE_OUTPUT} are visible to {@code LogToClient} when it decides whether to
 * forward output.</p>
 */
@NullMarked
public class ApplyClientEnvironmentVariables extends BuildCommandOnly {
    private static final Logger DEFAULT_LOGGER = Logging.getLogger(ApplyClientEnvironmentVariables.class);

    private final ProcessEnvironment processEnvironment;
    private final Logger logger;

    public ApplyClientEnvironmentVariables(ProcessEnvironment processEnvironment) {
        this(processEnvironment, DEFAULT_LOGGER);
    }

    @VisibleForTesting
    ApplyClientEnvironmentVariables(ProcessEnvironment processEnvironment, Logger logger) {
        this.processEnvironment = processEnvironment;
        this.logger = logger;
    }

    @Override
    protected void doBuild(DaemonCommandExecution execution, Build build) {
        Map<String, String> originalEnv = new HashMap<>(System.getenv());

        // Log only the variable names and not their values. Environment variables often contain sensitive data that should not be leaked to log files.
        logger.debug("Configuring env variables: {}", build.getParameters().getEnvVariables().keySet());

        EnvironmentModificationResult setEnvironmentResult = processEnvironment.maybeSetEnvironment(build.getParameters().getEnvVariables());
        if (!setEnvironmentResult.isSuccess()) {
            logger.warn("Warning: Unable to set daemon's environment variables to match the client because: "
                + System.getProperty("line.separator") + "  "
                + setEnvironmentResult
                + System.getProperty("line.separator") + "  "
                + "If the daemon was started with a significantly different environment from the client, and your build "
                + System.getProperty("line.separator") + "  "
                + "relies on environment variables, you may experience unexpected behavior.");
        }

        try {
            execution.proceed();
        } finally {
            processEnvironment.maybeSetEnvironment(originalEnv);
        }
    }
}
