/*
 * Copyright 2020 the original author or authors.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.launcher.cli.WelcomeMessageConfiguration;
import org.gradle.api.launcher.cli.WelcomeMessageDisplayMode;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.IoActions;
import org.gradle.launcher.bootstrap.ExecutionListener;
import org.gradle.launcher.configuration.BuildLayoutResult;
import org.gradle.util.GradleVersion;
import org.gradle.util.internal.GFileUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

class WelcomeMessageAction implements Action<ExecutionListener> {

    private final Logger logger;
    private final BuildLayoutResult buildLayout;
    private final GradleVersion gradleVersion;
    private final Function<String, InputStream> inputStreamProvider;
    private final WelcomeMessageConfiguration welcomeMessageConfiguration;
    private final Action<ExecutionListener> action;

    WelcomeMessageAction(BuildLayoutResult buildLayout, WelcomeMessageConfiguration welcomeMessageConfiguration, Action<ExecutionListener> action) {
        this(Logging.getLogger(WelcomeMessageAction.class), buildLayout, welcomeMessageConfiguration, GradleVersion.current(), new Function<String, InputStream>() {
            @Nullable
            @Override
            public InputStream apply(@Nullable String input) {
                return getClass().getClassLoader().getResourceAsStream(input);
            }
        }, action);
    }

    @VisibleForTesting
    WelcomeMessageAction(Logger logger, BuildLayoutResult buildLayout, WelcomeMessageConfiguration welcomeMessageConfiguration, GradleVersion gradleVersion, Function<String, InputStream> inputStreamProvider, Action<ExecutionListener> action) {
        this.logger = logger;
        this.buildLayout = buildLayout;
        this.gradleVersion = gradleVersion;
        this.inputStreamProvider = inputStreamProvider;
        this.welcomeMessageConfiguration = welcomeMessageConfiguration;
        this.action = action;
    }

    @Override
    public void execute(ExecutionListener executionListener) {
        if (isEnabledBySystemProperty() && isEnabledByGradleProperty()) {
            File markerFile = getMarkerFile();

            if (!markerFile.exists() && logger.isLifecycleEnabled()) {
                logger.lifecycle("");
                logger.lifecycle("Welcome to Gradle " + gradleVersion.getVersion() + "!");

                String featureList = readReleaseFeatures();

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

                writeMarkerFile(markerFile);
            }
        }
        action.execute(executionListener);
    }

    /**
     * The system property is set for the purpose of internal testing.
     * In user environments the system property will never be available.
     */
    private boolean isEnabledBySystemProperty() {
        String messageEnabled = System.getProperty(DefaultCommandLineActionFactory.WELCOME_MESSAGE_ENABLED_SYSTEM_PROPERTY);

        if (messageEnabled == null) {
            return true;
        }

        return Boolean.parseBoolean(messageEnabled);
    }

    private boolean isEnabledByGradleProperty() {
        if (welcomeMessageConfiguration != null) {
            return welcomeMessageConfiguration.getWelcomeMessageDisplayMode() == WelcomeMessageDisplayMode.ONCE;
        }
        return true;
    }

    private File getMarkerFile() {
        File gradleUserHomeDir = buildLayout.getGradleUserHomeDir();
        File notificationsDir = new File(gradleUserHomeDir, "notifications");
        File versionedNotificationsDir = new File(notificationsDir, gradleVersion.getVersion());
        return new File(versionedNotificationsDir, "release-features.rendered");
    }

    private String readReleaseFeatures() {
        InputStream inputStream = inputStreamProvider.apply("release-features.txt");

        if (inputStream != null) {
            StringWriter writer = new StringWriter();

            try {
                IOUtils.copy(inputStream, writer, "UTF-8");
                return writer.toString();
            } catch (IOException e) {
                // do not fail the build as feature is non-critical
            } finally {
                IoActions.closeQuietly(inputStream);
            }
        }

        return null;
    }

    private void writeMarkerFile(File markerFile) {
        GFileUtils.mkdirs(markerFile.getParentFile());
        GFileUtils.touch(markerFile);
    }
}
