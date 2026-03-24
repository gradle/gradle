/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.launcher.cli;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.launcher.cli.WelcomeMessageConfiguration;
import org.gradle.api.launcher.cli.WelcomeMessageDisplayMode;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.launcher.bootstrap.ExecutionListener;
import org.gradle.util.GradleVersion;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Action responsible for emitting messages to the user during CLI startup.
 */
public class StartupLoggingAction implements Action<ExecutionListener> {

    /**
     * System property that can disable the welcome message.
     */
    public static final String WELCOME_MESSAGE_ENABLED_SYSTEM_PROPERTY = "org.gradle.internal.launcher.welcomeMessageEnabled";

    /**
     * Message printed to the console when debug logging is enabled.
     */
    static final String DEBUG_WARNING_MESSAGE_BODY = "\n" +
        "#############################################################################\n" +
        "   WARNING WARNING WARNING WARNING WARNING WARNING WARNING WARNING WARNING\n" +
        "\n" +
        "   Debug level logging will leak security sensitive information!\n" +
        "\n" +
        "   " + new DocumentationRegistry().getDocumentationRecommendationFor("details", "logging", "sec:debug_security") + "\n" +
        "#############################################################################\n";

    private final Logger logger;
    private final File gradleUserHomeDir;
    private final WelcomeMessageConfiguration welcomeMessageConfiguration;
    private final LoggingConfiguration loggingConfiguration;
    private final GradleVersion gradleVersion;
    private final Supplier<InputStream> releaseFeaturesSupplier;
    private final Action<ExecutionListener> action;

    StartupLoggingAction(
        Logger logger,
        File gradleUserHomeDir,
        WelcomeMessageConfiguration welcomeMessageConfiguration,
        LoggingConfiguration loggingConfiguration,
        GradleVersion gradleVersion,
        Supplier<InputStream> releaseFeaturesSupplier,
        Action<ExecutionListener> action
    ) {
        this.logger = logger;
        this.gradleUserHomeDir = gradleUserHomeDir;
        this.welcomeMessageConfiguration = welcomeMessageConfiguration;
        this.loggingConfiguration = loggingConfiguration;
        this.gradleVersion = gradleVersion;
        this.releaseFeaturesSupplier = releaseFeaturesSupplier;
        this.action = action;
    }

    @Override
    public void execute(ExecutionListener executionListener) {
        // Add the debug warning to the top of the output.
        maybeLogDebugWarning(loggingConfiguration);
        try {
            maybePrintWelcomeMessage();
            action.execute(executionListener);
        } finally {
            // Add the debug warning again to the bottom.
            maybeLogDebugWarning(loggingConfiguration);
        }
    }

    /**
     * If debug logging is enabled, log a warning to the user notifying them
     * that debug logging will leak sensitive information.
     */
    private void maybeLogDebugWarning(LoggingConfiguration loggingConfiguration) {
        if (LogLevel.DEBUG.equals(loggingConfiguration.getLogLevel())) {
            logger.lifecycle(DEBUG_WARNING_MESSAGE_BODY);
        }
    }

    /**
     * Prints a welcome message to the user the first time they run Gradle
     * for a specific version.
     */
    private void maybePrintWelcomeMessage() {
        String messageEnabled = System.getProperty(WELCOME_MESSAGE_ENABLED_SYSTEM_PROPERTY);
        if (messageEnabled != null && !"true".equalsIgnoreCase(messageEnabled)) {
            return;
        }
        if (welcomeMessageConfiguration.getWelcomeMessageDisplayMode() != WelcomeMessageDisplayMode.ONCE) {
            return;
        }
        if (!logger.isLifecycleEnabled()) {
            return;
        }
        if (hasPrintedWelcomeBefore()) {
            return;
        }

        logger.lifecycle("");
        logger.lifecycle("Welcome to Gradle " + gradleVersion.getVersion() + "!");

        String featureList = getReleaseFeaturesFromStream(releaseFeaturesSupplier.get());
        if (StringUtils.isNotBlank(featureList)) {
            logger.lifecycle("");
            logger.lifecycle("Here are the highlights of this release:");
            logger.lifecycle(StringUtils.stripEnd(featureList, " \n\r"));
        }

        if (!gradleVersion.isSnapshot()) {
            logger.lifecycle("");
            logger.lifecycle("For more details see https://docs.gradle.org/" + gradleVersion.getVersion() + "/release-notes.html");
        }

        logger.lifecycle("");
    }

    /**
     * Determines if the welcome message has already been printed for this version,
     * returning true if it has, or marking it as printed and returning false otherwise.
     */
    private boolean hasPrintedWelcomeBefore() {
        Path markerFile = gradleUserHomeDir.toPath()
            .resolve("notifications")
            .resolve(gradleVersion.getVersion())
            .resolve("release-features.rendered");

        if (Files.exists(markerFile)) {
            return true;
        }

        try {
            // Mark the welcome message as printed.
            Files.createDirectories(markerFile.getParent());
            Files.createFile(markerFile);
        } catch (IOException e) {
            // Do not fail the build, as this feature is non-critical.
        }

        return false;
    }

    private static @Nullable String getReleaseFeaturesFromStream(InputStream releaseFeatures) {
        try (InputStream is = releaseFeatures) {
            if (is == null) {
                return null;
            }
            return IOUtils.toString(is, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Do not fail the build, as this feature is non-critical.
            return null;
        }
    }

}
