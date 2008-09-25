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

import groovy.lang.Script;
import org.gradle.api.InputStreamClassLoader;
import org.gradle.api.GradleException;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.util.HelperUtil;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Hans Dockter
 */
public class DefaultScriptHandlerTest {

    static final String TEST_SCRIPT_NAME = "somename_craidle";
    static final String TEST_EXPECTED_SYSTEMPROP_VALUE = "somevalue";
    static final String TEST_EXPECTED_SYSTEMPROP_KEY = "somekey";

    DefaultScriptHandler scriptHandler;

    DefaultProject testProject;

    File testProjectDir;

    File scriptCacheDir;
    File cachedFile;

    String testScript;

    InputStreamClassLoader classLoader;

    Class expectedScriptClass;

    @Before
    public void setUp() throws IOException, ClassNotFoundException {
        testProjectDir = HelperUtil.makeNewTestDir("projectdir");
        classLoader = new InputStreamClassLoader();
        InputStream inputStream = this.getClass().getResourceAsStream("/org/gradle/api/ClasspathTester.dat");
        classLoader.loadClass("org.gradle.api.ClasspathTester", inputStream);
        scriptHandler = new DefaultScriptHandler();
        scriptCacheDir = new File(testProjectDir, "cache");
        cachedFile = new File(scriptCacheDir, TEST_SCRIPT_NAME + ".class");
        testScript = "System.setProperty('" + TEST_EXPECTED_SYSTEMPROP_KEY + "', '" + TEST_EXPECTED_SYSTEMPROP_VALUE + "')";
        expectedScriptClass = TestBaseScript.class;
    }

    @After
    public void tearDown() {
        HelperUtil.deleteTestDir();
        System.getProperties().remove(TEST_EXPECTED_SYSTEMPROP_KEY);
    }
    
    @Test
    public void testWriteToCacheAndLoadFromCache() {
        Script script = scriptHandler.writeToCache(testScript, classLoader, TEST_SCRIPT_NAME, scriptCacheDir, expectedScriptClass);
        checkCacheDestination();
        evaluateScript(script);
        evaluateScript(scriptHandler.loadFromCache(0, classLoader, TEST_SCRIPT_NAME, scriptCacheDir));
    }

    private void checkCacheDestination() {
        assertTrue(scriptCacheDir.isDirectory());
        assertTrue(cachedFile.isFile());
    }

    @Test public void testCreateScript() {
        Script script = scriptHandler.createScript(testScript, classLoader, TEST_SCRIPT_NAME, expectedScriptClass);
        evaluateScript(script);
    }

    private void evaluateScript(Script script) {
        assertTrue(expectedScriptClass.isInstance(script));
        assertEquals(script.getClass().getSimpleName(), TEST_SCRIPT_NAME);
        System.setProperty(TEST_EXPECTED_SYSTEMPROP_KEY, "not the expected value");
        script.run();
        assertEquals(TEST_EXPECTED_SYSTEMPROP_VALUE, System.getProperty(TEST_EXPECTED_SYSTEMPROP_KEY));
    }

    @Test public void testLoadFromCacheWithNonCachedBuildFile() {
        assertNull(scriptHandler.loadFromCache(0, classLoader, TEST_SCRIPT_NAME, scriptCacheDir));
    }

    @Test public void testLoadFromCacheWithStaleCache() {
        scriptHandler.writeToCache(testScript, classLoader, TEST_SCRIPT_NAME, scriptCacheDir, expectedScriptClass);
        long inTheFuture = scriptCacheDir.lastModified() + 1000;
        assertNull(scriptHandler.loadFromCache(inTheFuture, classLoader, TEST_SCRIPT_NAME, scriptCacheDir));
    }

    @Test(expected = GradleException.class) public void testWriteToCacheWithException() {
        Script script = scriptHandler.writeToCache("new HHHHJSJSJ jsj", classLoader, TEST_SCRIPT_NAME, scriptCacheDir, expectedScriptClass);
    }

    @Test(expected = GradleException.class) public void testCreateScriptWithException() {
        Script script = scriptHandler.createScript("new HHHHJSJSJ jsj", classLoader, TEST_SCRIPT_NAME, expectedScriptClass);
    }

    public abstract static class TestBaseScript extends Script {
    }
}

