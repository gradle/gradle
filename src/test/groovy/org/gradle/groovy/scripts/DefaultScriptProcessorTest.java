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
import org.gradle.util.HelperUtil;
import org.junit.runner.RunWith;
import org.jmock.Mockery;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.gradle.CacheUsage;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.net.URL;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class DefaultScriptProcessorTest {
    static final String TEST_BUILD_FILE_NAME = "mybuild.craidle";
    static final String TEST_SCRIPT_NAME = "mybuild_craidle";
    static final String TEST_SCRIPT_TEXT = "sometext";
    static final String TEST_IN_MEMORY_SCRIPT_TEXT = "someInMemoryText";
    static final String TEST_SCRIPT_ATACHEMENT = "import org.gradle.api.*";

    DefaultScriptProcessor scriptProcessor;

    File testCacheDir;
    File testScriptFileDir;
    File testScriptFile;

    ClassLoader testClassLoader;

    IScriptHandler scriptHandlerMock;

    Script expectedScript;

    Mockery context = new JUnit4Mockery();

    Class expectedScriptBaseClass = Script.class;

    @Before
    public void setUp() {
        scriptHandlerMock = context.mock(IScriptHandler.class);
        testClassLoader = new URLClassLoader(new URL[0]);
        testScriptFileDir = HelperUtil.makeNewTestDir("projectdir");
        testScriptFile = new File(testScriptFileDir, TEST_BUILD_FILE_NAME);
        testCacheDir = new File("scriptCacheDir");
        expectedScript = HelperUtil.createTestScript();
        context.checking(new Expectations() {
            {
            }
        });
        scriptProcessor = new DefaultScriptProcessor(scriptHandlerMock);
    }

    @After
    public void tearDown() {
        HelperUtil.deleteTestDir();
    }

    @Test
    public void testInit() {
        assertSame(scriptHandlerMock, scriptProcessor.getScriptHandler());
    }

    @Test
    public void testWithNonExistingBuildFile() {
        assertTrue(scriptProcessor.createScriptFromFile(testCacheDir, testScriptFile, "", CacheUsage.ON, testClassLoader, expectedScriptBaseClass) instanceof EmptyScript);
    }

    @Test
    public void testWithNullBuildFile() {
        assertTrue(scriptProcessor.createScriptFromFile(testCacheDir, null, "", CacheUsage.ON, testClassLoader, expectedScriptBaseClass) instanceof EmptyScript);
    }

    @Test
    public void testWithNonCachedExistingBuildFile() {
        createBuildScriptFile();
        context.checking(new Expectations() {
            {
                one(scriptHandlerMock).loadFromCache(testScriptFile.lastModified(), testClassLoader, TEST_SCRIPT_NAME, testCacheDir);
                will(returnValue(null));
                one(scriptHandlerMock).writeToCache(
                        TEST_SCRIPT_TEXT + System.getProperty("line.separator") + TEST_SCRIPT_ATACHEMENT,
                        testClassLoader,
                        TEST_SCRIPT_NAME,
                        testCacheDir,
                        expectedScriptBaseClass
                );
                will(returnValue(expectedScript));
            }
        });
        assertSame(expectedScript, scriptProcessor.createScriptFromFile(testCacheDir, testScriptFile, TEST_SCRIPT_ATACHEMENT,
                CacheUsage.ON, testClassLoader, expectedScriptBaseClass));
    }

    @Test
    public void testWithExistingCachedBuildFile() {
        createBuildScriptFile();
        context.checking(new Expectations() {
            {
                one(scriptHandlerMock).loadFromCache(testScriptFile.lastModified(), testClassLoader, TEST_SCRIPT_NAME, testCacheDir);
                will(returnValue(expectedScript));
            }
        });

        assertSame(expectedScript, scriptProcessor.createScriptFromFile(testCacheDir, testScriptFile, TEST_SCRIPT_ATACHEMENT,
                CacheUsage.ON, testClassLoader, expectedScriptBaseClass));
    }

    @Test
    public void testWithExistingBuildFileAndRebuildCache() {
        createBuildScriptFile();
        context.checking(new Expectations() {
            {
                one(scriptHandlerMock).writeToCache(
                        TEST_SCRIPT_TEXT + System.getProperty("line.separator") + TEST_SCRIPT_ATACHEMENT,
                        testClassLoader,
                        TEST_SCRIPT_NAME,
                        testCacheDir,
                        expectedScriptBaseClass
                ); will(returnValue(expectedScript));
            }
        });

        assertSame(expectedScript, scriptProcessor.createScriptFromFile(testCacheDir, testScriptFile, TEST_SCRIPT_ATACHEMENT,
                CacheUsage.REBUILD, testClassLoader, expectedScriptBaseClass));
    }

    @Test
    public void testWithInMemoryScriptText() {
        context.checking(new Expectations() {
            {
                one(scriptHandlerMock).createScript(
                        TEST_IN_MEMORY_SCRIPT_TEXT + System.getProperty("line.separator") + TEST_SCRIPT_ATACHEMENT,
                        testClassLoader,
                        TEST_SCRIPT_NAME,
                        expectedScriptBaseClass);
                will(returnValue(expectedScript));
            }
        });
        assertSame(expectedScript, scriptProcessor.createScriptFromText(TEST_IN_MEMORY_SCRIPT_TEXT, TEST_SCRIPT_ATACHEMENT,
                TEST_SCRIPT_NAME, testClassLoader, expectedScriptBaseClass));
    }

    @Test
    public void testWithExistingBuildFileAndCacheOff() {
        createBuildScriptFile();
        context.checking(new Expectations() {
            {
                one(scriptHandlerMock).createScript(
                        TEST_SCRIPT_TEXT + System.getProperty("line.separator") + TEST_SCRIPT_ATACHEMENT,
                        testClassLoader,
                        TEST_SCRIPT_NAME,
                        expectedScriptBaseClass);
                will(returnValue(expectedScript));
            }
        });
        assertSame(expectedScript, scriptProcessor.createScriptFromFile(testCacheDir, testScriptFile, TEST_SCRIPT_ATACHEMENT,
                CacheUsage.OFF, testClassLoader, expectedScriptBaseClass));
    }

    @Test
    public void testWithNonExistingBuildFileAndCacheOff() {
        assertTrue(scriptProcessor.createScriptFromFile(testCacheDir, testScriptFile, "", CacheUsage.OFF, testClassLoader, expectedScriptBaseClass) instanceof EmptyScript);
    }

    private void createBuildScriptFile() {
        try {
            FileUtils.writeStringToFile(testScriptFile, TEST_SCRIPT_TEXT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}