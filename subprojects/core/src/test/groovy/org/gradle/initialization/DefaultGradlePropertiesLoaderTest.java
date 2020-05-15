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
package org.gradle.initialization;

import org.gradle.api.Project;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static java.util.Collections.emptyMap;
import static org.gradle.api.Project.SYSTEM_PROP_PREFIX;
import static org.gradle.initialization.IGradlePropertiesLoader.*;
import static org.gradle.internal.Cast.uncheckedNonnullCast;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DefaultGradlePropertiesLoaderTest {
    private DefaultGradlePropertiesLoader gradlePropertiesLoader;
    private File gradleUserHomeDir;
    private File settingsDir;
    private File gradleInstallationHomeDir;
    private Map<String, String> systemProperties = new HashMap<>();
    private Map<String, String> envProperties = new HashMap<>();
    private final StartParameterInternal startParameter = new StartParameterInternal();
    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass());

    @Before
    public void setUp() {
        gradleUserHomeDir = tmpDir.createDir("gradleUserHome");
        settingsDir = tmpDir.createDir("settingsDir");
        gradleInstallationHomeDir = tmpDir.createDir("gradleInstallationHome");
        gradlePropertiesLoader = new DefaultGradlePropertiesLoader(startParameter);
        startParameter.setGradleUserHomeDir(gradleUserHomeDir);
        startParameter.setGradleHomeDir(gradleInstallationHomeDir);
    }

    private void writePropertyFile(File location, Map<String, String> propertiesMap) {
        Properties properties = new Properties();
        properties.putAll(propertiesMap);
        GUtil.saveProperties(properties, new File(location, Project.GRADLE_PROPERTIES));
    }

    @Test
    public void mergeAddsPropertiesFromInstallationPropertiesFile() {
        writePropertyFile(gradleInstallationHomeDir, uncheckedNonnullCast(uncheckedNonnullCast(GUtil.map("settingsProp", "settings value"))));

        Map<String, String> properties = loadAndMergePropertiesWith(emptyMap());

        assertEquals("settings value", properties.get("settingsProp"));
    }

    @Test
    public void mergeAddsPropertiesFromUserPropertiesFile() {
        writePropertyFile(gradleUserHomeDir, uncheckedNonnullCast(GUtil.map("userProp", "user value")));

        Map<String, String> properties = loadAndMergePropertiesWith(emptyMap());

        assertEquals("user value", properties.get("userProp"));
    }

    @Test
    public void mergeAddsPropertiesFromSettingsPropertiesFile() {
        writePropertyFile(settingsDir, uncheckedNonnullCast(GUtil.map("settingsProp", "settings value")));

        Map<String, String> properties = loadAndMergePropertiesWith(emptyMap());

        assertEquals("settings value", properties.get("settingsProp"));
    }

    @Test
    public void mergeAddsPropertiesFromEnvironmentVariablesWithPrefix() {
        envProperties = uncheckedNonnullCast(GUtil.map(
            ENV_PROJECT_PROPERTIES_PREFIX + "envProp", "env value",
            "ignoreMe", "ignored"));

        Map<String, String> properties = loadAndMergePropertiesWith(emptyMap());

        assertEquals("env value", properties.get("envProp"));
    }

    @Test
    public void mergeAddsPropertiesFromSystemPropertiesWithPrefix() {
        systemProperties = uncheckedNonnullCast(GUtil.map(
            SYSTEM_PROJECT_PROPERTIES_PREFIX + "systemProp", "system value",
            "ignoreMe", "ignored"));

        Map<String, String> properties = loadAndMergePropertiesWith(emptyMap());

        assertEquals("system value", properties.get("systemProp"));
    }

    @Test
    public void mergeAddsPropertiesFromStartParameter() {
        startParameter.setProjectProperties(uncheckedNonnullCast(GUtil.map("paramProp", "param value")));

        Map<String, String> properties = loadAndMergePropertiesWith(emptyMap());

        assertEquals("param value", properties.get("paramProp"));
    }

    @Test
    public void projectPropertiesHavePrecedenceOverInstallationPropertiesFile() {
        writePropertyFile(gradleInstallationHomeDir, uncheckedNonnullCast(GUtil.map("prop", "settings value")));
        Map<String, String> projectProperties = uncheckedNonnullCast(GUtil.map("prop", "project value"));

        Map<String, String> properties = loadAndMergePropertiesWith(projectProperties);

        assertEquals("project value", properties.get("prop"));
    }

    @Test
    public void projectPropertiesHavePrecedenceOverSettingsPropertiesFile() {
        writePropertyFile(settingsDir, uncheckedNonnullCast(GUtil.map("prop", "settings value")));
        Map<String, String> projectProperties = uncheckedNonnullCast(GUtil.map("prop", "project value"));

        Map<String, String> properties = loadAndMergePropertiesWith(projectProperties);

        assertEquals("project value", properties.get("prop"));
    }

    @Test
    public void userPropertiesFileHasPrecedenceOverSettingsPropertiesFile() {
        writePropertyFile(gradleUserHomeDir, uncheckedNonnullCast(GUtil.map("prop", "user value")));
        writePropertyFile(settingsDir, uncheckedNonnullCast(GUtil.map("prop", "settings value")));

        Map<String, String> properties = loadAndMergePropertiesWith(emptyMap());

        assertEquals("user value", properties.get("prop"));
    }

    @Test
    public void userPropertiesFileHasPrecedenceOverProjectProperties() {
        writePropertyFile(gradleUserHomeDir, uncheckedNonnullCast(GUtil.map("prop", "user value")));
        writePropertyFile(settingsDir, uncheckedNonnullCast(GUtil.map("prop", "settings value")));
        Map<String, String> projectProperties = uncheckedNonnullCast(GUtil.map("prop", "project value"));

        Map<String, String> properties = loadAndMergePropertiesWith(projectProperties);

        assertEquals("user value", properties.get("prop"));
    }

    @Test
    public void environmentVariablesHavePrecedenceOverProjectProperties() {
        writePropertyFile(gradleUserHomeDir, uncheckedNonnullCast(GUtil.map("prop", "user value")));
        writePropertyFile(settingsDir, uncheckedNonnullCast(GUtil.map("prop", "settings value")));
        Map<String, String> projectProperties = uncheckedNonnullCast(GUtil.map("prop", "project value"));
        envProperties = uncheckedNonnullCast(GUtil.map(ENV_PROJECT_PROPERTIES_PREFIX + "prop", "env value"));

        Map<String, String> properties = loadAndMergePropertiesWith(projectProperties);

        assertEquals("env value", properties.get("prop"));
    }

    @Test
    public void systemPropertiesHavePrecedenceOverEnvironmentVariables() {
        writePropertyFile(gradleUserHomeDir, uncheckedNonnullCast(GUtil.map("prop", "user value")));
        writePropertyFile(settingsDir, uncheckedNonnullCast(GUtil.map("prop", "settings value")));
        Map<String, String> projectProperties = uncheckedNonnullCast(GUtil.map("prop", "project value"));
        envProperties = uncheckedNonnullCast(GUtil.map(ENV_PROJECT_PROPERTIES_PREFIX + "prop", "env value"));
        systemProperties = uncheckedNonnullCast(GUtil.map(SYSTEM_PROJECT_PROPERTIES_PREFIX + "prop", "system value"));

        Map<String, String> properties = loadAndMergePropertiesWith(projectProperties);

        assertEquals("system value", properties.get("prop"));
    }

    @Test
    public void startParameterPropertiesHavePrecedenceOverSystemProperties() {
        writePropertyFile(gradleUserHomeDir, uncheckedNonnullCast(GUtil.map("prop", "user value")));
        writePropertyFile(settingsDir, uncheckedNonnullCast(GUtil.map("prop", "settings value")));
        Map<String, String> projectProperties = uncheckedNonnullCast(GUtil.map("prop", "project value"));
        envProperties = uncheckedNonnullCast(GUtil.map(ENV_PROJECT_PROPERTIES_PREFIX + "prop", "env value"));
        systemProperties = uncheckedNonnullCast(GUtil.map(SYSTEM_PROJECT_PROPERTIES_PREFIX + "prop", "system value"));
        startParameter.setProjectProperties(uncheckedNonnullCast(GUtil.map("prop", "param value")));

        Map<String, String> properties = loadAndMergePropertiesWith(projectProperties);

        assertEquals("param value", properties.get("prop"));
    }

    @Test
    public void loadDoesNotSetSystemProperties() {
        startParameter.setSystemPropertiesArgs(WrapUtil.toMap("systemPropArgKey", "systemPropArgValue"));
        writePropertyFile(gradleUserHomeDir, uncheckedNonnullCast(GUtil.map(SYSTEM_PROP_PREFIX + ".userSystemProp", "userSystemValue")));
        writePropertyFile(settingsDir, uncheckedNonnullCast(GUtil.map(
            SYSTEM_PROP_PREFIX + ".userSystemProp", "settingsSystemValue",
            SYSTEM_PROP_PREFIX + ".settingsSystemProp2", "settingsSystemValue2")));

        loadProperties();

        assertNull(System.getProperty("userSystemProp"));
        assertNull(System.getProperty("settingsSystemProp2"));
        assertNull(System.getProperty("systemPropArgKey"));
    }

    @Test
    public void loadPropertiesWithNoExceptionForNonexistentGradleInstallationHomeAndUserHomeAndSettingsDir() {
        tmpDir.getTestDirectory().deleteDir();
        loadProperties();
    }

    @Test
    public void loadPropertiesWithNoExceptionIfGradleInstallationHomeIsNotKnown() {
        gradleInstallationHomeDir = null;
        loadProperties();
    }

    @Test
    public void reloadsProperties() {
        writePropertyFile(settingsDir, uncheckedNonnullCast(GUtil.map("prop1", "value", "prop2", "value")));

        File otherSettingsDir = tmpDir.createDir("otherSettingsDir");
        writePropertyFile(otherSettingsDir, uncheckedNonnullCast(GUtil.map("prop1", "otherValue")));

        Map<String, String> properties = loadAndMergePropertiesWith(emptyMap());
        assertEquals("value", properties.get("prop1"));
        assertEquals("value", properties.get("prop2"));

        properties = loadPropertiesFrom(otherSettingsDir).getGradleProperties().mergeProperties(emptyMap());
        assertEquals("otherValue", properties.get("prop1"));
        assertNull(properties.get("prop2"));
    }

    @Test
    public void buildSystemProperties() {
        System.setProperty("gradle-loader-test", "value");
        assertTrue(gradlePropertiesLoader.getAllSystemProperties().containsKey("gradle-loader-test"));
        assertEquals("value", gradlePropertiesLoader.getAllSystemProperties().get("gradle-loader-test"));
    }

    @Test
    public void startParameterSystemPropertiesHavePrecedenceOverPropertiesFiles() {
        writePropertyFile(gradleUserHomeDir, uncheckedNonnullCast(GUtil.map(
            "systemProp.prop", "user value",
            "property", "user val"
        )));
        writePropertyFile(settingsDir, uncheckedNonnullCast(GUtil.map(
            "systemProp.prop", "settings value",
            "property", "settings val"
        )));
        systemProperties = uncheckedNonnullCast(GUtil.map(
            "prop", "system value",
            "property", "system val"
        ));
        startParameter.setSystemPropertiesArgs(uncheckedNonnullCast(GUtil.map(
            "prop", "commandline value",
            "org.gradle.project.property", "commandline val"
        )));

        LoadedGradleProperties loaded = loadProperties();

        assertEquals("commandline value", loaded.getSystemProperties().get("prop"));
        assertNull(loaded.getGradleProperties().find("systemProp.prop"));

        assertEquals("commandline val", loaded.getGradleProperties().find("property"));
    }

    private Map<String, String> loadAndMergePropertiesWith(Map<String, String> properties) {
        return loadProperties().getGradleProperties().mergeProperties(properties);
    }

    private LoadedGradleProperties loadProperties() {
        return loadPropertiesFrom(settingsDir);
    }

    private LoadedGradleProperties loadPropertiesFrom(File settingsDir) {
        return gradlePropertiesLoader.loadProperties(
            settingsDir,
            startParameter,
            systemProperties,
            envProperties
        );
    }
}
