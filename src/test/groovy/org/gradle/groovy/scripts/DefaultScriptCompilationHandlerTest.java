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
import org.gradle.util.HelperUtil;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.Expectations;
import org.hamcrest.Matchers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultScriptCompilationHandlerTest {

    static final String TEST_SCRIPT_NAME = "somename_craidle";
    static final String TEST_EXPECTED_SYSTEMPROP_VALUE = "somevalue";
    static final String TEST_EXPECTED_SYSTEMPROP_KEY = "somekey";

    private DefaultScriptCompilationHandler scriptCompilationHandler;

    private File testProjectDir;

    private File scriptCacheDir;
    private File cachedFile;

    private String testScript;

    private InputStreamClassLoader classLoader;

    private Class<? extends Script> expectedScriptClass;

    private CachePropertiesHandler cachePropertiesHandlerMock;

    private JUnit4Mockery context = new JUnit4Mockery();

    @Before
    public void setUp() throws IOException, ClassNotFoundException {
        cachePropertiesHandlerMock = context.mock(CachePropertiesHandler.class);
        testProjectDir = HelperUtil.makeNewTestDir("projectdir");
        classLoader = new InputStreamClassLoader();
        InputStream inputStream = this.getClass().getResourceAsStream("/org/gradle/api/ClasspathTester.dat");
        classLoader.loadClass("org.gradle.api.ClasspathTester", inputStream);
        scriptCompilationHandler = new DefaultScriptCompilationHandler(cachePropertiesHandlerMock);
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
    public void init() {
        assertSame(cachePropertiesHandlerMock, scriptCompilationHandler.getCachePropertyHandler());
    }
    
    @Test
    public void testWriteToCache() {
        context.checking(new Expectations() {{
            one(cachePropertiesHandlerMock).writeProperties(testScript, scriptCacheDir, false);
            one(cachePropertiesHandlerMock).getCacheState(testScript, scriptCacheDir); will(returnValue(CachePropertiesHandler.CacheState.VALID));
        }});
        scriptCompilationHandler.writeToCache(testScript, classLoader, TEST_SCRIPT_NAME, scriptCacheDir, expectedScriptClass);
        checkCacheDestination();
        evaluateScript(scriptCompilationHandler.loadFromCache(testScript, classLoader, TEST_SCRIPT_NAME, scriptCacheDir, expectedScriptClass));
    }

    @Test
    public void testWriteToCacheAndLoadFromCacheWithEmptyScript() {
        final String emptyScript = "// ignore me\n";
        context.checking(new Expectations() {{
            one(cachePropertiesHandlerMock).writeProperties(emptyScript, scriptCacheDir, true);
        }});
        scriptCompilationHandler.writeToCache(emptyScript, classLoader, TEST_SCRIPT_NAME, scriptCacheDir, expectedScriptClass);
    }

    private void checkCacheDestination() {
        assertTrue(scriptCacheDir.isDirectory());
        assertTrue(cachedFile.isFile());
    }

    @Test public void testCreateScriptOnTheFly() {
        Script script = scriptCompilationHandler.createScriptOnTheFly(testScript, classLoader, TEST_SCRIPT_NAME, expectedScriptClass);
        evaluateScript(script);
    }

    @Test public void testCreateScriptOnTheFlyWithEmptyScript() {
        Script script = scriptCompilationHandler.createScriptOnTheFly("// ignore me\n", classLoader, TEST_SCRIPT_NAME, expectedScriptClass);
        assertTrue(script instanceof EmptyScript);
    }

    private void evaluateScript(Script script) {
        assertTrue(expectedScriptClass.isInstance(script));
        assertEquals(script.getClass().getSimpleName(), TEST_SCRIPT_NAME);
        System.setProperty(TEST_EXPECTED_SYSTEMPROP_KEY, "not the expected value");
        script.run();
        assertEquals(TEST_EXPECTED_SYSTEMPROP_VALUE, System.getProperty(TEST_EXPECTED_SYSTEMPROP_KEY));
    }

    @Test public void testLoadFromCacheWithInvalidCache() {
        context.checking(new Expectations() {{
            allowing(cachePropertiesHandlerMock).getCacheState(testScript, scriptCacheDir); will(returnValue(CachePropertiesHandler.CacheState.INVALID));
        }});
        assertNull(scriptCompilationHandler.loadFromCache(testScript, classLoader, TEST_SCRIPT_NAME, scriptCacheDir, expectedScriptClass));
    }

    @Test public void testLoadFromCacheWithEmptyScript() {
        context.checking(new Expectations() {{
            allowing(cachePropertiesHandlerMock).getCacheState(testScript, scriptCacheDir); will(returnValue(CachePropertiesHandler.CacheState.EMPTY_SCRIPT));
        }});
        assertThat(scriptCompilationHandler.loadFromCache(testScript, classLoader, TEST_SCRIPT_NAME, scriptCacheDir, expectedScriptClass),
                Matchers.instanceOf(EmptyScript.class));
    }

    @Test public void testLoadFromCacheWhenNotAssignableToBaseClass() {
        context.checking(new Expectations() {{
            one(cachePropertiesHandlerMock).writeProperties(testScript, scriptCacheDir, false);
            allowing(cachePropertiesHandlerMock).getCacheState(testScript, scriptCacheDir); will(returnValue(CachePropertiesHandler.CacheState.VALID));
        }});
        scriptCompilationHandler.writeToCache(testScript, classLoader, TEST_SCRIPT_NAME, scriptCacheDir, Script.class);
        assertNull(scriptCompilationHandler.loadFromCache(testScript, classLoader, TEST_SCRIPT_NAME, scriptCacheDir, expectedScriptClass));
    }

    @Test(expected = GradleException.class) public void testWriteToCacheWithException() {
        scriptCompilationHandler.writeToCache("new HHHHJSJSJ jsj", classLoader, TEST_SCRIPT_NAME, scriptCacheDir, expectedScriptClass);
    }

    @Test(expected = GradleException.class) public void testCreateScriptWithException() {
        scriptCompilationHandler.createScriptOnTheFly("new HHHHJSJSJ jsj", classLoader, TEST_SCRIPT_NAME, expectedScriptClass);
    }

    public abstract static class TestBaseScript extends Script {
    }
}

