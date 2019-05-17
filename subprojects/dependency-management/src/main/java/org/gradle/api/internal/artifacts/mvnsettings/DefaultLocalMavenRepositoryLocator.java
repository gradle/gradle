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
package org.gradle.api.internal.artifacts.mvnsettings;

import org.apache.maven.settings.building.SettingsBuildingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultLocalMavenRepositoryLocator implements LocalMavenRepositoryLocator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultLocalMavenRepositoryLocator.class);
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^\\}]*)\\}");

    private final MavenSettingsProvider settingsProvider;
    private final SystemPropertyAccess system;
    private String localRepoPathFromMavenSettings;

    public DefaultLocalMavenRepositoryLocator(MavenSettingsProvider settingsProvider) {
        this(settingsProvider, new CurrentSystemPropertyAccess());
    }

    protected DefaultLocalMavenRepositoryLocator(MavenSettingsProvider settingsProvider, SystemPropertyAccess system) {
        this.settingsProvider = settingsProvider;
        this.system = system;
    }

    @Override
    public File getLocalMavenRepository() throws CannotLocateLocalMavenRepositoryException {
        String localOverride = system.getProperty("maven.repo.local");
        if (localOverride != null) {
            return new File(localOverride);
        }
        try {
            String repoPath = parseLocalRepoPathFromMavenSettings();
            if (repoPath != null) {
                File file = new File(resolvePlaceholders(repoPath.trim()));
                if (isDriveRelativeWindowsPath(file)) {
                    return file.getAbsoluteFile();
                } else {
                    return file;
                }
            } else {
                File defaultLocation = new File(system.getProperty("user.home"), "/.m2/repository").getAbsoluteFile();
                LOGGER.debug("No local repository in Settings file defined. Using default path: {}", defaultLocation);
                return defaultLocation;
            }
        } catch (SettingsBuildingException e) {
            throw new CannotLocateLocalMavenRepositoryException("Unable to parse local Maven settings.", e);
        }
    }

    private boolean isDriveRelativeWindowsPath(File file) {
        return !file.isAbsolute() && file.getPath().startsWith(File.separator);
    }

    // We only cache the result of parsing the Maven settings files, but allow this value to be updated in-flight
    // via system properties. This allows the local maven repo to be overridden when publishing to maven
    // (see http://forums.gradle.org/gradle/topics/override_location_of_the_local_maven_repo).
    private synchronized String parseLocalRepoPathFromMavenSettings() throws SettingsBuildingException {
        if (localRepoPathFromMavenSettings == null) {
            localRepoPathFromMavenSettings = settingsProvider.getLocalRepository();
        }
        return localRepoPathFromMavenSettings;
    }

    private String resolvePlaceholders(String value) {
        StringBuffer result = new StringBuffer();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(value);
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String replacement = placeholder.startsWith("env.") ? system.getEnv(placeholder.substring(4)) : system.getProperty(placeholder);
            if (replacement == null) {
                throw new CannotLocateLocalMavenRepositoryException(String.format("Cannot resolve placeholder '%s' in value '%s'", placeholder, value));
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public static interface SystemPropertyAccess {
        String getProperty(String name);
        String getEnv(String name);
    }

    public static class CurrentSystemPropertyAccess implements SystemPropertyAccess {
        @Override
        public String getProperty(String name) {
            return System.getProperty(name);
        }

        @Override
        public String getEnv(String name) {
            return System.getenv(name);
        }
    }
}
