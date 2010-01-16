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

import org.gradle.StartParameter;
import org.gradle.api.Project;
import org.gradle.util.GUtil;
import org.gradle.util.TemporaryFolder;
import org.gradle.util.WrapUtil;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.util.Map;
import java.util.Properties;

/**
 * @author Hans Dockter
 */
public class DefaultGradlePropertiesLoaderTest {
    private DefaultGradlePropertiesLoader gradlePropertiesLoader;
    private File gradleUserHomeDir;
    private File settingsDir;
    private Map<String, String> systemProperties;
    private Map<String, String> envProperties;
    private Map<String, String> userHomeProperties;
    private Map<String, String> settingsDirProperties;
    private StartParameter startParameter;
    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    @Before
    public void setUp() {
        gradleUserHomeDir = tmpDir.createDir("gradleUserHome");
        settingsDir = tmpDir.createDir("settingsDir");
        gradlePropertiesLoader = new DefaultGradlePropertiesLoader();
        startParameter = new StartParameter();
        startParameter.setGradleUserHomeDir(gradleUserHomeDir);
        startParameter.setSystemPropertiesArgs(WrapUtil.toMap("systemPropArgKey", "systemPropArgValue"));
        systemProperties = GUtil.map(
                IGradlePropertiesLoader.SYSTEM_PROJECT_PROPERTIES_PREFIX + "systemProp1", "systemValue1");
        envProperties = GUtil.map(
                IGradlePropertiesLoader.ENV_PROJECT_PROPERTIES_PREFIX + "systemProp1", "envValue1",
                IGradlePropertiesLoader.ENV_PROJECT_PROPERTIES_PREFIX + "envProp2", "envValue2");
        writePropertyFile(gradleUserHomeDir, userHomeProperties = GUtil.map(
                "envProp2", "userValue1",
                "userProp2", "userValue2",
                Project.SYSTEM_PROP_PREFIX + ".userSystemProp", "userSystemValue"));
        writePropertyFile(settingsDir, settingsDirProperties = GUtil.map(
                "userProp2", "settingsValue1",
                "settingsProp2", "settingsValue2",
                Project.SYSTEM_PROP_PREFIX + ".userSystemProp", "settingsSystemValue",
                Project.SYSTEM_PROP_PREFIX + ".settingsSystemProp2", "settingsSystemValue2"));
    }

    private void writePropertyFile(File location, Map<String, String> propertiesMap) {
        Properties properties = new Properties();
        properties.putAll(propertiesMap);
        GUtil.saveProperties(properties, new File(location, Project.GRADLE_PROPERTIES));
    }

    @Test
    public void loadProperties() {
        gradlePropertiesLoader.loadProperties(settingsDir, startParameter, systemProperties, envProperties);
        assertEquals("systemValue1", gradlePropertiesLoader.getGradleProperties().get("systemProp1"));
        assertEquals("envValue2", gradlePropertiesLoader.getGradleProperties().get("envProp2"));
        assertEquals("userValue2", gradlePropertiesLoader.getGradleProperties().get("userProp2"));
        assertEquals("settingsValue2", gradlePropertiesLoader.getGradleProperties().get("settingsProp2"));
        assertEquals("userSystemValue", System.getProperty("userSystemProp"));
        assertEquals("settingsSystemValue2", System.getProperty("settingsSystemProp2"));
        assertEquals("systemPropArgValue", System.getProperty("systemPropArgKey"));
    }

    @Test
    public void loadPropertiesWithNoExceptionForNonExistingUserHomeAndSettingsDir() {
        tmpDir.getDir().deleteDir();
        gradlePropertiesLoader.loadProperties(settingsDir, startParameter, systemProperties, envProperties);
    }

    @Test
    public void reloadsProperties() {
        writePropertyFile(settingsDir, GUtil.map("prop1", "value", "prop2", "value"));

        File otherSettingsDir = tmpDir.createDir("otherSettingsDir");
        writePropertyFile(otherSettingsDir, GUtil.map("prop1", "otherValue"));

        gradlePropertiesLoader.loadProperties(settingsDir, startParameter, systemProperties, envProperties);
        assertEquals("value", gradlePropertiesLoader.getGradleProperties().get("prop1"));
        assertEquals("value", gradlePropertiesLoader.getGradleProperties().get("prop2"));

        gradlePropertiesLoader.loadProperties(otherSettingsDir, startParameter, systemProperties, envProperties);
        assertEquals("otherValue", gradlePropertiesLoader.getGradleProperties().get("prop1"));
        assertNull(gradlePropertiesLoader.getGradleProperties().get("prop2"));
    }

    @Test
    public void buildSystemProperties() {
        System.setProperty("gradle-loader-test", "value");
        assertTrue(gradlePropertiesLoader.getAllSystemProperties().containsKey("gradle-loader-test"));
        assertEquals("value", gradlePropertiesLoader.getAllSystemProperties().get("gradle-loader-test"));
    }
}
