/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.groovy.scripts;

import org.gradle.util.GUtil;
import org.gradle.util.GradleVersion;
import org.gradle.util.TemporaryFolder;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Properties;

/**
 * @author Hans Dockter
 */
public class DefaultCachePropertiesHandlerTest {
    public static final String TEST_SCRIPT_TEXT = "someScript";

    private DefaultCachePropertiesHandler cachePropertyHandler;

    private ScriptSource scriptSource;
    private Map<String, Object> additionalProperties;
    @Rule
    public TemporaryFolder testDir = new TemporaryFolder();
    private File testCacheDir;

    @Before
    public void setUp() {
        testCacheDir = testDir.getDir();
        cachePropertyHandler = new DefaultCachePropertiesHandler();
        scriptSource = new StringScriptSource("script", TEST_SCRIPT_TEXT);
        additionalProperties = GUtil.map("a", "valuea");
    }

    @Test
    public void cacheStateIsInvalidWhenCacheDirDoesNotExist() {
        assertEquals(CachePropertiesHandler.CacheState.INVALID, cachePropertyHandler.getCacheState(scriptSource, new File("no existo"), additionalProperties));
    }

    @Test
    public void cacheStateIsInvalidWhenNoPropertiesFilePresent() {
        assertEquals(CachePropertiesHandler.CacheState.INVALID, cachePropertyHandler.getCacheState(scriptSource, testCacheDir, additionalProperties));
    }

    @Test
    public void cacheStateIsInvalidWhenPropertiesFileEmpty() {
        Properties properties = new Properties();
        GUtil.saveProperties(properties, new File(testCacheDir, CachePropertiesHandler.PROPERTY_FILE_NAME));
        assertEquals(CachePropertiesHandler.CacheState.INVALID, cachePropertyHandler.getCacheState(scriptSource, testCacheDir, additionalProperties));
    }

    @Test
    public void cacheStateIsInvalidWhenTextHashesAreDifferent() {
        createPropertiesFile(TEST_SCRIPT_TEXT + "delta", new GradleVersion().getVersion());
        assertEquals(CachePropertiesHandler.CacheState.INVALID, cachePropertyHandler.getCacheState(scriptSource, testCacheDir, additionalProperties));
    }

    private String createHash(String scriptText) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            messageDigest.update(scriptText.getBytes());
            return new BigInteger(1, messageDigest.digest()).toString(16);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void cacheStateIsValidWhenTextHashesAndGradleVersionAndAdditionalPropertiesAreEqual() {
        createPropertiesFile(TEST_SCRIPT_TEXT, new GradleVersion().getVersion());
        assertEquals(CachePropertiesHandler.CacheState.VALID, cachePropertyHandler.getCacheState(scriptSource, testCacheDir, additionalProperties));
    }

    @Test
    public void ignoresExtraAdditionalProperties() {
        additionalProperties.put("extra", "value");
        createPropertiesFile(TEST_SCRIPT_TEXT, new GradleVersion().getVersion());
        assertEquals(CachePropertiesHandler.CacheState.VALID, cachePropertyHandler.getCacheState(scriptSource, testCacheDir, GUtil.map("a", "valuea")));
    }

    @Test
    public void cacheStateIsInvalidWhenGradleVersionsAreDifferent() {
        createPropertiesFile(TEST_SCRIPT_TEXT, new GradleVersion().getVersion() + "delta");
        assertEquals(CachePropertiesHandler.CacheState.INVALID, cachePropertyHandler.getCacheState(scriptSource, testCacheDir, additionalProperties));
    }

    @Test
    public void cacheStateIsInvalidWhenAdditionalPropertyIsMissing() {
        createPropertiesFile(TEST_SCRIPT_TEXT, new GradleVersion().getVersion());
        assertEquals(CachePropertiesHandler.CacheState.INVALID, cachePropertyHandler.getCacheState(scriptSource, testCacheDir, GUtil.map("c", "valuec")));
    }

    @Test
    public void cacheStateIsInvalidWhenAdditionalPropertyValuesAreDifferent() {
        createPropertiesFile(TEST_SCRIPT_TEXT, new GradleVersion().getVersion());
        assertEquals(CachePropertiesHandler.CacheState.INVALID, cachePropertyHandler.getCacheState(scriptSource, testCacheDir, GUtil.map("a", "different value")));
    }

    private void createPropertiesFile(String scriptText, String version) {
        Properties properties = new Properties();
        properties.put(CachePropertiesHandler.HASH_KEY, createHash(scriptText));
        properties.put(CachePropertiesHandler.VERSION_KEY, version);
        properties.putAll(additionalProperties);
        GUtil.saveProperties(properties, new File(testCacheDir, CachePropertiesHandler.PROPERTY_FILE_NAME));
    }

    @Test
    public void writesPropertiesFile() throws IOException, NoSuchAlgorithmException {
        cachePropertyHandler.writeProperties(scriptSource, testCacheDir, additionalProperties);
        checkWriteProperties(additionalProperties);
        assertEquals(CachePropertiesHandler.CacheState.VALID, cachePropertyHandler.getCacheState(scriptSource, testCacheDir, additionalProperties));
    }

    private void checkWriteProperties(Map additionalExpectedProperties) {
        File propertiesFile = new File(testCacheDir, CachePropertiesHandler.PROPERTY_FILE_NAME);
        Properties actualProperties = GUtil.loadProperties(propertiesFile);
        Properties expectedProperties = new Properties();
        expectedProperties.put(CachePropertiesHandler.HASH_KEY, createHash(TEST_SCRIPT_TEXT));
        expectedProperties.put(CachePropertiesHandler.VERSION_KEY, new GradleVersion().getVersion());
        expectedProperties.putAll(additionalExpectedProperties);
        assertEquals(expectedProperties, actualProperties);
    }
}
