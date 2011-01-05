/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.maven.settings.DefaultMavenSettingsBuilder;
import org.apache.maven.settings.MavenSettingsBuilder;
import org.apache.maven.settings.Settings;
import org.gradle.api.internal.artifacts.publish.maven.pombuilder.PlexusLoggerAdapter;
import org.gradle.util.UncheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Field;

/**
 * @author Steve Ebersole
 */
public class LocalMavenCacheLocator {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalMavenCacheLocator.class);
    private static final String USER_HOME_MARKER = "${user.home}/";

    public File getLocalMavenCache() {
        File userHome = new File(System.getProperty("user.home"));
        File m2Dir = new File(userHome, ".m2");

        File userSettings = new File(m2Dir, "settings.xml");
        if (userSettings.exists()) {
            File overriddenMavenLocal = extractMavenLocal(userSettings, userHome);
            if (overriddenMavenLocal != null) {
                return overriddenMavenLocal;
            }
        }

        return new File(m2Dir, "repository");
    }

    private File extractMavenLocal(File userSettings, File userHome) {
        Settings settings = extractSettings(userSettings);
        String override = settings.getLocalRepository();
        if (override != null) {
            override = override.trim();
            if (override.length() > 0) {
                // Nice, it does not even handle the interpolation for us, so we'll handle some common cases...
                if (override.startsWith(USER_HOME_MARKER)) {
                    override = userHome.getAbsolutePath() + '/' + override.substring(USER_HOME_MARKER.length());
                }
                return new File(override);
            }
        }
        return null;
    }

    private Settings extractSettings(File userSettings) {
        try {
            MavenSettingsBuilder builder = buildSettingsBuilder(userSettings);
            return builder.buildSettings();
        } catch (Exception e) {
            throw UncheckedException.asUncheckedException(e);
        }
    }

    private MavenSettingsBuilder buildSettingsBuilder(File userSettings) throws Exception {
        final String userSettingsPath = userSettings.getAbsolutePath();

        DefaultMavenSettingsBuilder builder = new DefaultMavenSettingsBuilder();
        builder.enableLogging(new PlexusLoggerAdapter(LOGGER));

        Field userSettingsPathField = DefaultMavenSettingsBuilder.class.getDeclaredField("userSettingsPath");
        userSettingsPathField.setAccessible(true);
        userSettingsPathField.set(builder, userSettingsPath);

        Field globalSettingsPathField = DefaultMavenSettingsBuilder.class.getDeclaredField("globalSettingsPath");
        globalSettingsPathField.setAccessible(true);
        globalSettingsPathField.set(builder, userSettingsPath);

        builder.initialize();

        return builder;
    }
}
