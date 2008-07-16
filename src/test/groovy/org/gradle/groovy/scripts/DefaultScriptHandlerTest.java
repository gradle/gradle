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

import org.gradle.api.internal.project.DefaultProject;
import org.gradle.api.internal.project.ProjectScript;
import org.gradle.api.InputStreamClassLoader;
import org.gradle.api.Project;
import org.gradle.api.GradleScriptException;
import org.gradle.util.HelperUtil;
import org.gradle.util.GFileUtils;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;

import groovy.lang.Script;

/**
 * @author Hans Dockter
 */
public class DefaultScriptHandlerTest {

    static final String TEST_SCRIPT_NAME = "somename_craidle";
    static final String TEST_TEXT_FOR_SCRIPT_CREATED_FILE = "sometext";

    DefaultScriptHandler scriptHandler;

    DefaultProject testProject;

    File testProjectDir;

    File cacheDir;
    File scriptCacheDir;
    File cachedFile;

    String testScript;
    File testScriptCreatedFile;

    InputStreamClassLoader classLoader;

    Class expectedScriptClass;



    @Before
    public void setUp() throws IOException, ClassNotFoundException {
        testProjectDir = HelperUtil.makeNewTestDir("projectdir");
        classLoader = new InputStreamClassLoader();
        InputStream inputStream = this.getClass().getResourceAsStream("/org/gradle/api/ClasspathTester.dat");
        classLoader.loadClass("org.gradle.api.ClasspathTester", inputStream);
        scriptHandler = new DefaultScriptHandler();
        cacheDir = new File(testProjectDir, Project.CACHE_DIR_NAME);
        scriptCacheDir = new File(cacheDir, TEST_SCRIPT_NAME);
        cachedFile = new File(scriptCacheDir, TEST_SCRIPT_NAME + ".class");
        testScriptCreatedFile = new File(testProjectDir, "testscriptfile");
        testScript = "new File('" + testScriptCreatedFile.getAbsolutePath() + "').write('" + TEST_TEXT_FOR_SCRIPT_CREATED_FILE + "')";
        expectedScriptClass = TestBaseScript.class;
    }

    @After
    public void tearDown() {
//        HelperUtil.deleteTestDir();
    }
    
    @Test
    public void testWriteToCacheAndLoadFromCache() {
        Script script = scriptHandler.writeToCache(testScript, classLoader, TEST_SCRIPT_NAME, cacheDir, expectedScriptClass);
        checkCacheDestination();
        evaluateScript(script);
        evaluateScript(scriptHandler.loadFromCache(0, classLoader, TEST_SCRIPT_NAME, cacheDir));
    }

    private void checkCacheDestination() {
        assert scriptCacheDir.isDirectory();
        assert cachedFile.isFile();
    }

    @Test public void testCreateScript() {
        Script script = scriptHandler.createScript(testScript, classLoader, TEST_SCRIPT_NAME, expectedScriptClass);
        evaluateScript(script);
    }

    private void evaluateScript(Script script) {
        assertTrue(expectedScriptClass.isInstance(script));
        script.run();
        assertEquals(TEST_TEXT_FOR_SCRIPT_CREATED_FILE, GFileUtils.readFileToString(testScriptCreatedFile));
        testScriptCreatedFile.delete();
    }

    @Test public void testLoadFromCacheWithNonCachedBuildFile() {
        assertNull(scriptHandler.loadFromCache(0, classLoader, TEST_SCRIPT_NAME, cacheDir));
    }

    @Test public void testLoadFromCacheWithStaleCache() {
        scriptHandler.writeToCache(testScript, classLoader, TEST_SCRIPT_NAME, cacheDir, expectedScriptClass);
        cachedFile.setLastModified(0);
        assertNull(scriptHandler.loadFromCache(100000, classLoader, TEST_SCRIPT_NAME, cacheDir));
    }

    @Test(expected = GradleScriptException.class) public void testWriteToCacheWithException() {
        Script script = scriptHandler.writeToCache("new HHHHJSJSJ jsj", classLoader, TEST_SCRIPT_NAME, cacheDir, expectedScriptClass);
    }

    @Test(expected = GradleScriptException.class) public void testCreateScriptWithException() {
        Script script = scriptHandler.createScript("new HHHHJSJSJ jsj", classLoader, TEST_SCRIPT_NAME, expectedScriptClass);
    }

    public abstract static class TestBaseScript extends Script {
        
    }
}

