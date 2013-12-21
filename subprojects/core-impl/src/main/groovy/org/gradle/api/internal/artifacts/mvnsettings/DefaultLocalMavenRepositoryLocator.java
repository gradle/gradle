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

import org.gradle.mvn3.org.apache.maven.settings.Settings;
import org.gradle.mvn3.org.apache.maven.settings.building.SettingsBuildingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultLocalMavenRepositoryLocator implements LocalMavenRepositoryLocator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultLocalMavenRepositoryLocator.class);
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^\\}]*)\\}");

    private final Map<String, String> systemProperties;
    private final Map<String, String> environmentVariables;
    private final MavenSettingsProvider settingsProvider;

    public DefaultLocalMavenRepositoryLocator(MavenSettingsProvider settingsProvider, Map<String, String> systemProperties,
                                              Map<String, String> environmentVariables) {
        this.systemProperties = systemProperties;
        this.environmentVariables = environmentVariables;
        this.settingsProvider = settingsProvider;
    }

    public File getLocalMavenRepository() throws CannotLocateLocalMavenRepositoryException{
        try {
            Settings settings = settingsProvider.buildSettings();
            String repoPath = settings.getLocalRepository();
            if (repoPath != null) {
                return new File(resolvePlaceholders(repoPath.trim()));
            } else {
                File defaultLocation = new File(System.getProperty("user.home"), "/.m2/repository").getAbsoluteFile();
                LOGGER.debug(String.format("No local repository in Settings file defined. Using default path: %s", defaultLocation));
                return defaultLocation;
            }
        } catch (SettingsBuildingException e) {
            throw new CannotLocateLocalMavenRepositoryException("Unable to parse local Maven settings.", e);
        }
    }

    private String resolvePlaceholders(String value) {
        StringBuffer result = new StringBuffer();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(value);
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String replacement = placeholder.startsWith("env.") ? environmentVariables.get(placeholder.substring(4)) : systemProperties.get(placeholder);
            if (replacement == null) {
                throw new CannotLocateLocalMavenRepositoryException(String.format("Cannot resolve placeholder '%s' in value '%s'", placeholder, value));
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }
}
