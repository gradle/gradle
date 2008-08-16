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
import org.apache.commons.io.FileUtils;
import org.gradle.CacheUsage;
import org.gradle.api.Project;
import org.gradle.util.HelperUtil;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

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

    ScriptSource source;

    @Before
    public void setUp() {
        scriptHandlerMock = context.mock(IScriptHandler.class);
        testClassLoader = new URLClassLoader(new URL[0]);
        testScriptFileDir = HelperUtil.makeNewTestDir("projectdir");
        testScriptFile = new File(testScriptFileDir, TEST_BUILD_FILE_NAME);
        testCacheDir = new File(new File(testScriptFileDir, Project.CACHE_DIR_NAME), TEST_BUILD_FILE_NAME);
        expectedScript = HelperUtil.createTestScript();
        scriptProcessor = new DefaultScriptProcessor(scriptHandlerMock, CacheUsage.ON);
        source = context.mock(ScriptSource.class);

        context.checking(new Expectations(){{
            allowing(source).getDescription();
            will(returnValue("[script source]"));
        }});
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
    public void testWithNonExistingSourceFileAndNoText() {
        context.checking(new Expectations(){{
            allowing(source).getSourceFile();
            will(returnValue(testScriptFile));
            allowing(source).getText();
            will(returnValue(null));
        }});

        assertTrue(scriptProcessor.createScript(source, testClassLoader, expectedScriptBaseClass) instanceof EmptyScript);
    }

    @Test
    public void testWithNoSourceFileAndNoText() {
        context.checking(new Expectations(){{
            allowing(source).getSourceFile();
            will(returnValue(null));
            allowing(source).getText();
            will(returnValue(null));
        }});

        assertTrue(scriptProcessor.createScript(source, testClassLoader, expectedScriptBaseClass) instanceof EmptyScript);
    }

    @Test
    public void testWithNoSouceFileAndNonEmptyText() {
        context.checking(new Expectations() {
            {
                allowing(source).getSourceFile();
                will(returnValue(null));

                allowing(source).getText();
                will(returnValue(TEST_SCRIPT_TEXT));

                allowing(source).getClassName();
                will(returnValue(TEST_SCRIPT_NAME));

                one(scriptHandlerMock).createScript(
                        TEST_SCRIPT_TEXT,
                        testClassLoader,
                        TEST_SCRIPT_NAME,
                        expectedScriptBaseClass);
                will(returnValue(expectedScript));
            }
        });

        assertSame(expectedScript, scriptProcessor.createScript(source, testClassLoader, expectedScriptBaseClass));
    }

    @Test
    public void testWithNonCachedExistingSourceFile() {
        createBuildScriptFile();
        context.checking(new Expectations() {
            {
                allowing(source).getSourceFile();
                will(returnValue(testScriptFile));

                allowing(source).getText();
                will(returnValue(TEST_SCRIPT_TEXT));

                allowing(source).getClassName();
                will(returnValue(TEST_SCRIPT_NAME));

                one(scriptHandlerMock).loadFromCache(testScriptFile.lastModified(), testClassLoader, TEST_SCRIPT_NAME, testCacheDir);
                will(returnValue(null));

                one(scriptHandlerMock).writeToCache(
                        TEST_SCRIPT_TEXT,
                        testClassLoader,
                        TEST_SCRIPT_NAME,
                        testCacheDir,
                        expectedScriptBaseClass
                );
                will(returnValue(expectedScript));
            }
        });
        assertSame(expectedScript, scriptProcessor.createScript(source, testClassLoader, expectedScriptBaseClass));
    }

    @Test
    public void testWithExistingCachedSourceFile() {
        createBuildScriptFile();
        context.checking(new Expectations() {
            {
                allowing(source).getSourceFile();
                will(returnValue(testScriptFile));

                allowing(source).getClassName();
                will(returnValue(TEST_SCRIPT_NAME));

                one(scriptHandlerMock).loadFromCache(testScriptFile.lastModified(), testClassLoader, TEST_SCRIPT_NAME, testCacheDir);
                will(returnValue(expectedScript));
            }
        });

        assertSame(expectedScript, scriptProcessor.createScript(source, testClassLoader, expectedScriptBaseClass));
    }

    @Test
    public void testWithExistingBuildFileAndRebuildCache() {
        createBuildScriptFile();
        context.checking(new Expectations() {
            {
                allowing(source).getSourceFile();
                will(returnValue(testScriptFile));

                allowing(source).getText();
                will(returnValue(TEST_SCRIPT_TEXT));

                allowing(source).getClassName();
                will(returnValue(TEST_SCRIPT_NAME));

                one(scriptHandlerMock).writeToCache(
                        TEST_SCRIPT_TEXT,
                        testClassLoader,
                        TEST_SCRIPT_NAME,
                        testCacheDir,
                        expectedScriptBaseClass
                ); will(returnValue(expectedScript));
            }
        });

        scriptProcessor = new DefaultScriptProcessor(scriptHandlerMock, CacheUsage.REBUILD);
        assertSame(expectedScript, scriptProcessor.createScript(source, testClassLoader, expectedScriptBaseClass));
    }

    @Test
    public void testWithExistingSourceFileAndCacheOff() {
        createBuildScriptFile();
        context.checking(new Expectations() {
            {
                allowing(source).getSourceFile();
                will(returnValue(testScriptFile));

                allowing(source).getText();
                will(returnValue(TEST_SCRIPT_TEXT));

                allowing(source).getClassName();
                will(returnValue(TEST_SCRIPT_NAME));

                one(scriptHandlerMock).createScript(
                        TEST_SCRIPT_TEXT,
                        testClassLoader,
                        TEST_SCRIPT_NAME,
                        expectedScriptBaseClass);
                will(returnValue(expectedScript));
            }
        });

        scriptProcessor = new DefaultScriptProcessor(scriptHandlerMock, CacheUsage.OFF);
        assertSame(expectedScript, scriptProcessor.createScript(source, testClassLoader, expectedScriptBaseClass));
    }

    @Test
    public void testWithNonExistingSourceFileAndCacheOff() {
        context.checking(new Expectations() {
            {
                allowing(source).getSourceFile();
                will(returnValue(testScriptFile));

                allowing(source).getText();
                will(returnValue(""));
            }
        });

        scriptProcessor = new DefaultScriptProcessor(scriptHandlerMock, CacheUsage.OFF);
        assertTrue(scriptProcessor.createScript(source, testClassLoader, expectedScriptBaseClass) instanceof EmptyScript);
    }

    private void createBuildScriptFile() {
        try {
            FileUtils.writeStringToFile(testScriptFile, TEST_SCRIPT_TEXT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}