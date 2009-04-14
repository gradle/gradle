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

import org.junit.Before;
import org.junit.Test;
import org.junit.After;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import org.gradle.util.GradleVersion;
import org.gradle.util.GUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.FileInputStream;
import java.util.Properties;
import java.util.Map;
import java.util.HashMap;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.math.BigInteger;

/**
 * @author Hans Dockter
 */
public class DefaultCachePropertiesHandlerTest {
    public static final String TEST_SCRIPT_TEXT = "someScript";

    private DefaultCachePropertiesHandler cachePropertyHandler;

    private File testCacheDir;

    @Before
    public void setUp() {
        testCacheDir = HelperUtil.makeNewTestDir();
        cachePropertyHandler = new DefaultCachePropertiesHandler();
    }

    @After
    public void tearDown() {
        HelperUtil.deleteTestDir();
    }

    @Test
    public void getCacheStateWithNoCachePropertiesFile() {
        assertEquals(CachePropertiesHandler.CacheState.INVALID, cachePropertyHandler.getCacheState(TEST_SCRIPT_TEXT, testCacheDir));
    }

    @Test
    public void getCacheStateWithDifferentHashes() throws NoSuchAlgorithmException, IOException {
        createPropertiesFile(TEST_SCRIPT_TEXT + "delta", new GradleVersion().getVersion());
        assertEquals(CachePropertiesHandler.CacheState.INVALID, cachePropertyHandler.getCacheState(TEST_SCRIPT_TEXT, testCacheDir));
    }

    private String createHash(String scriptText) throws NoSuchAlgorithmException {
        MessageDigest messageDigest = MessageDigest.getInstance("MD5");
        messageDigest.update(scriptText.getBytes());
        return new BigInteger(1, messageDigest.digest()).toString(16);
    }

    @Test
    public void getCacheStateWithSameHashes() throws NoSuchAlgorithmException, IOException {
        createPropertiesFile(TEST_SCRIPT_TEXT, new GradleVersion().getVersion());
        assertEquals(CachePropertiesHandler.CacheState.VALID, cachePropertyHandler.getCacheState(TEST_SCRIPT_TEXT, testCacheDir));
    }

    @Test
    public void getCacheStateWithDifferentVersions() throws NoSuchAlgorithmException, IOException {
        createPropertiesFile(TEST_SCRIPT_TEXT, new GradleVersion().getVersion() + "delta");
        assertEquals(CachePropertiesHandler.CacheState.INVALID, cachePropertyHandler.getCacheState(TEST_SCRIPT_TEXT, testCacheDir));
    }

    private void createPropertiesFile(String scriptText, String version) throws NoSuchAlgorithmException, IOException {
        Properties properties = new Properties();
        properties.put(CachePropertiesHandler.HASH_KEY, createHash(scriptText));
        properties.put(CachePropertiesHandler.VERSION_KEY, version);
        GUtil.saveProperties(properties, new File(testCacheDir, CachePropertiesHandler.PROPERTY_FILE_NAME));
    }

    @Test
    public void writeProperties() throws IOException, NoSuchAlgorithmException {
        cachePropertyHandler.writeProperties(TEST_SCRIPT_TEXT, testCacheDir);
        checkWriteProperties(new HashMap());
    }

    private void checkWriteProperties(Map additionalExpectedProperties) throws IOException, NoSuchAlgorithmException {
        File propertiesFile = new File(testCacheDir, CachePropertiesHandler.PROPERTY_FILE_NAME);
        Properties actualProperties = GUtil.loadProperties(propertiesFile);
        Properties expectedProperties = new Properties();
        expectedProperties.put(CachePropertiesHandler.HASH_KEY, createHash(TEST_SCRIPT_TEXT));
        expectedProperties.put(CachePropertiesHandler.VERSION_KEY, new GradleVersion().getVersion());
        expectedProperties.putAll(additionalExpectedProperties);
        assertEquals(expectedProperties, actualProperties);
    }
}
