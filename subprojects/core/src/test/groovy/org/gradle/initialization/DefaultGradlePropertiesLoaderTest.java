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
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.internal.Cast;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.gradle.util.internal.GUtil;
import org.gradle.util.SetSystemProperties;
import org.gradle.util.internal.WrapUtil;
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
    private StartParameterInternal startParameter = new StartParameterInternal();
    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass());
    @Rule
    public SetSystemProperties sysProp = new SetSystemProperties();

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
        writePropertyFile(gradleInstallationHomeDir, Cast.uncheckedNonnullCast(Cast.uncheckedNonnullCast(GUtil.map("settingsProp", "settings value"))));

        Map<String, String> properties = loadAndMergePropertiesWith(emptyMap());

        assertEquals("settings value", properties.get("settingsProp"));
    }

    @Test
    public void mergeAddsPropertiesFromUserPropertiesFile() {
        writePropertyFile(gradleUserHomeDir, Cast.uncheckedNonnullCast(GUtil.map("userProp", "user value")));

        Map<String, String> properties = loadAndMergePropertiesWith(emptyMap());

        assertEquals("user value", properties.get("userProp"));
    }

    @Test
    public void mergeAddsPropertiesFromSettingsPropertiesFile() {
        writePropertyFile(settingsDir, Cast.uncheckedNonnullCast(GUtil.map("settingsProp", "settings value")));

        Map<String, String> properties = loadAndMergePropertiesWith(emptyMap());

        assertEquals("settings value", properties.get("settingsProp"));
    }

    @Test
    public void mergeAddsPropertiesFromEnvironmentVariablesWithPrefix() {
        envProperties = Cast.uncheckedNonnullCast(GUtil.map(
            ENV_PROJECT_PROPERTIES_PREFIX + "envProp", "env value",
            "ignoreMe", "ignored"));

        Map<String, String> properties = loadAndMergePropertiesWith(emptyMap());

        assertEquals("env value", properties.get("envProp"));
    }

    @Test
    public void mergeAddsPropertiesFromSystemPropertiesWithPrefix() {
        systemProperties = Cast.uncheckedNonnullCast(GUtil.map(
            SYSTEM_PROJECT_PROPERTIES_PREFIX + "systemProp", "system value",
            "ignoreMe", "ignored"));

        Map<String, String> properties = loadAndMergePropertiesWith(emptyMap());

        assertEquals("system value", properties.get("systemProp"));
    }

    @Test
    public void mergeAddsPropertiesFromStartParameter() {
        startParameter.setProjectProperties(Cast.uncheckedNonnullCast(GUtil.map("paramProp", "param value")));

        Map<String, String> properties = loadAndMergePropertiesWith(emptyMap());

        assertEquals("param value", properties.get("paramProp"));
    }

    @Test
    public void projectPropertiesHavePrecedenceOverInstallationPropertiesFile() {
        writePropertyFile(gradleInstallationHomeDir, Cast.uncheckedNonnullCast(GUtil.map("prop", "settings value")));
        Map<String, String> projectProperties = Cast.uncheckedNonnullCast(GUtil.map("prop", "project value"));

        Map<String, String> properties = loadAndMergePropertiesWith(projectProperties);

        assertEquals("project value", properties.get("prop"));
    }

    @Test
    public void projectPropertiesHavePrecedenceOverSettingsPropertiesFile() {
        writePropertyFile(settingsDir, Cast.uncheckedNonnullCast(GUtil.map("prop", "settings value")));
        Map<String, String> projectProperties = Cast.uncheckedNonnullCast(GUtil.map("prop", "project value"));

        Map<String, String> properties = loadAndMergePropertiesWith(projectProperties);

        assertEquals("project value", properties.get("prop"));
    }

    @Test
    public void userPropertiesFileHasPrecedenceOverSettingsPropertiesFile() {
        writePropertyFile(gradleUserHomeDir, Cast.uncheckedNonnullCast(GUtil.map("prop", "user value")));
        writePropertyFile(settingsDir, Cast.uncheckedNonnullCast(GUtil.map("prop", "settings value")));

        Map<String, String> properties = loadAndMergePropertiesWith(emptyMap());

        assertEquals("user value", properties.get("prop"));
    }

    @Test
    public void userPropertiesFileHasPrecedenceOverProjectProperties() {
        writePropertyFile(gradleUserHomeDir, Cast.uncheckedNonnullCast(GUtil.map("prop", "user value")));
        writePropertyFile(settingsDir, Cast.uncheckedNonnullCast(GUtil.map("prop", "settings value")));
        Map<String, String> projectProperties = Cast.uncheckedNonnullCast(GUtil.map("prop", "project value"));

        Map<String, String> properties = loadAndMergePropertiesWith(projectProperties);

        assertEquals("user value", properties.get("prop"));
    }

    @Test
    public void environmentVariablesHavePrecedenceOverProjectProperties() {
        writePropertyFile(gradleUserHomeDir, Cast.uncheckedNonnullCast(GUtil.map("prop", "user value")));
        writePropertyFile(settingsDir, Cast.uncheckedNonnullCast(GUtil.map("prop", "settings value")));
        Map<String, String> projectProperties = Cast.uncheckedNonnullCast(GUtil.map("prop", "project value"));
        envProperties = Cast.uncheckedNonnullCast(GUtil.map(ENV_PROJECT_PROPERTIES_PREFIX + "prop", "env value"));

        Map<String, String> properties = loadAndMergePropertiesWith(projectProperties);

        assertEquals("env value", properties.get("prop"));
    }

    @Test
    public void systemPropertiesHavePrecedenceOverEnvironmentVariables() {
        writePropertyFile(gradleUserHomeDir, Cast.uncheckedNonnullCast(GUtil.map("prop", "user value")));
        writePropertyFile(settingsDir, Cast.uncheckedNonnullCast(GUtil.map("prop", "settings value")));
        Map<String, String> projectProperties = Cast.uncheckedNonnullCast(GUtil.map("prop", "project value"));
        envProperties = Cast.uncheckedNonnullCast(GUtil.map(ENV_PROJECT_PROPERTIES_PREFIX + "prop", "env value"));
        systemProperties = Cast.uncheckedNonnullCast(GUtil.map(SYSTEM_PROJECT_PROPERTIES_PREFIX + "prop", "system value"));

        Map<String, String> properties = loadAndMergePropertiesWith(projectProperties);

        assertEquals("system value", properties.get("prop"));
    }

    @Test
    public void startParameterPropertiesHavePrecedenceOverSystemProperties() {
        writePropertyFile(gradleUserHomeDir, Cast.uncheckedNonnullCast(GUtil.map("prop", "user value")));
        writePropertyFile(settingsDir, Cast.uncheckedNonnullCast(GUtil.map("prop", "settings value")));
        Map<String, String> projectProperties = Cast.uncheckedNonnullCast(GUtil.map("prop", "project value"));
        envProperties = Cast.uncheckedNonnullCast(GUtil.map(ENV_PROJECT_PROPERTIES_PREFIX + "prop", "env value"));
        systemProperties = Cast.uncheckedNonnullCast(GUtil.map(SYSTEM_PROJECT_PROPERTIES_PREFIX + "prop", "system value"));
        startParameter.setProjectProperties(Cast.uncheckedNonnullCast(GUtil.map("prop", "param value")));

        Map<String, String> properties = loadAndMergePropertiesWith(projectProperties);

        assertEquals("param value", properties.get("prop"));
    }

    @Test
    public void loadSetsSystemProperties() {
        startParameter.setSystemPropertiesArgs(WrapUtil.toMap("systemPropArgKey", "systemPropArgValue"));
        writePropertyFile(gradleUserHomeDir, Cast.uncheckedNonnullCast(GUtil.map(SYSTEM_PROP_PREFIX + ".userSystemProp", "userSystemValue")));
        writePropertyFile(settingsDir, Cast.uncheckedNonnullCast(GUtil.map(
            SYSTEM_PROP_PREFIX + ".userSystemProp", "settingsSystemValue",
            SYSTEM_PROP_PREFIX + ".settingsSystemProp2", "settingsSystemValue2")));

        loadProperties();

        assertEquals("userSystemValue", System.getProperty("userSystemProp"));
        assertEquals("settingsSystemValue2", System.getProperty("settingsSystemProp2"));
        assertEquals("systemPropArgValue", System.getProperty("systemPropArgKey"));
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
        writePropertyFile(settingsDir, Cast.uncheckedNonnullCast(GUtil.map("prop1", "value", "prop2", "value")));

        File otherSettingsDir = tmpDir.createDir("otherSettingsDir");
        writePropertyFile(otherSettingsDir, Cast.uncheckedNonnullCast(GUtil.map("prop1", "otherValue")));

        Map<String, String> properties = loadAndMergePropertiesWith(emptyMap());
        assertEquals("value", properties.get("prop1"));
        assertEquals("value", properties.get("prop2"));

        properties = loadPropertiesFrom(otherSettingsDir).mergeProperties(emptyMap());
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
        writePropertyFile(gradleUserHomeDir, Cast.uncheckedNonnullCast(GUtil.map("systemProp.prop", "user value")));
        writePropertyFile(settingsDir, Cast.uncheckedNonnullCast(GUtil.map("systemProp.prop", "settings value")));
        systemProperties = Cast.uncheckedNonnullCast(GUtil.map("prop", "system value"));
        startParameter.setSystemPropertiesArgs(WrapUtil.toMap("prop", "commandline value"));

        loadProperties();

        assertEquals("commandline value", System.getProperty("prop"));
    }

    private Map<String, String> loadAndMergePropertiesWith(Map<String, String> properties) {
        return loadProperties().mergeProperties(properties);
    }

    private GradleProperties loadProperties() {
        return loadPropertiesFrom(settingsDir);
    }

    private GradleProperties loadPropertiesFrom(File settingsDir) {
        return gradlePropertiesLoader.loadProperties(
            settingsDir,
            startParameter,
            systemProperties,
            envProperties
        );
    }
}
